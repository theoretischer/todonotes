"""F7 Link-Vorschau: Server-Seitiger Meta-Proxy.

Warum: Im Browser blockt CORS jeden externen og-Fetch (reddit.com,
youtube.com … senden keinen Access-Control-Allow-Origin). Die Web-App
fragt deshalb diesen Endpoint same-origin an; der Server fetcht ohne
CORS-Beschränkung und schickt die extrahierten Metadaten als JSON.

Was der Server macht:
1. URL validieren (nur http/https, Redirect-Auflösung auf max. 5 Hops).
2. /s/<hash>-Share-Redirects von Reddit (301 → /comments/<id>/) auflösen.
3. Content-Type "image/*" → direkt als Binär-Daten durchreichen.
4. HTML (max. 1 MB) → og:*/twitter:*/<title>-Metas extrahieren.
   (Tag-für-Tag-Anleihe aus app/data/linkpreview/LinkMetaParser.kt —
   dort läuft derselbe Regex-Ansatz im Client, hier serverseitig.)
5. Reddit-Post-URL ohne og:image → oembed-Endpoint probieren
   (reddit.com blockt sonst alles; oembed braucht die AUFGELÖSTE
   Post-URL, rohe /s/-Links geben 400).
6. Bild (max. 3 MB) direkt inline als base64 mitschicken, wenn die
   og:image ein klein genuges JPEG/PNG/GIF/WebP ist — spart dem
   Client einen zweiten Fetch und umgeht CORS beim Bild-Download.

Security: Bearer-Token nötig (verify_token) — kein offener Proxy,
den irgendein Skript als SSRF-Sprunganlage nutzen könnte. Zusätzlich
blocking der privaten Adressen (SSRF-Schutz), Redirects nur auf
öffentliche Hosts.
"""
from __future__ import annotations

import base64
import ipaddress
import json
import re
import socket
import urllib.error
import urllib.parse
import urllib.request

from fastapi import HTTPException
from pydantic import BaseModel

from .auth import verify_token_str

# --- Limits (mit Client abgestimmt: LinkPreviewRepository / Kotlin) ---
MAX_HTML_CHARS = 1_000_000
MAX_IMAGE_BYTES = 3_000_000
MAX_REDIRECTS = 5
TIMEOUT_SECONDS = 8

USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
)
ACCEPT_HTML = "text/html,application/xhtml+xml"
ACCEPT_ANY = "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8"

# Regexes wie im Client-Parser (LinkMetaParser.kt), nur die nötigen:
_RE_META = re.compile(
    r"<meta\s[^>]*?property=[\"'](?:og|twitter):([a-z_]+)[\"'][^>]*?"
    r"content=[\"']([^\"']*)[\"']|"
    r"<meta\s[^>]*?content=[\"']([^\"']*)[\"'][^>]*?"
    r"property=[\"'](?:og|twitter):([a-z_]+)[\"']",
    re.IGNORECASE,
)
_RE_TITLE = re.compile(r"<title[^>]*>([^<]*)</title>", re.IGNORECASE)
_RE_COMMENT_OR_SCRIPT = re.compile(r"<(script|style)[^>]*>.*?</\1>|<!--.*?-->", re.IGNORECASE | re.DOTALL)

# HTML-Entities wie im Client
_ENTITY_RE = re.compile(r"&#(\d+);|&#x([0-9a-fA-F]+);|&([a-zA-Z]+);")
_NAMED = {
    "amp": "&", "lt": "<", "gt": ">", "quot": '"', "apos": "'",
    "nbsp": " ", "hellip": "…", "mdash": "—", "ndash": "–", "bull": "•",
    "laquo": "«", "raquo": "»", "ldquo": "“", "rdquo": "”", "lsquo": "‘",
    "rsquo": "’", "deg": "°", "middot": "·", "times": "×", "copy": "©",
    "reg": "®", "trade": "™", "euro": "€", "pound": "£", "cent": "¢",
}

# Reddit
_REDDIT_POST = re.compile(
    r"^https?://(?:[a-z0-9-]+\.)*reddit\.com/(r/[A-Za-z0-9_]+/comments/\w+/[^/?#]*|comments/\w+)"
)
_REDDIT_SHARE = re.compile(r"^https?://(?:[a-z0-9-]+\.)*reddit\.com/r/[A-Za-z0-9_]+/s/[A-Za-z0-9]+$")
_REDDIT_SHORT = re.compile(r"^https?://(?:www\.)?redd\.it/([A-Za-z0-9]+)$")

# RFC 2396: unreserved + reserved, ohne Unicode — reicht für die
# allermeisten URLs; exotische Unicode-Zeichen im Pfad werden
# vorher prozent-encodiert (unquote+quote).
_SAFE_URL = "!#$%&'()*+,-./:;=?@[]_~"


class LinkMetaRequest(BaseModel):
    url: str


class LinkMetaResponse(BaseModel):
    title: str = ""
    description: str = ""
    siteName: str = ""
    imageUrl: str = ""
    imageBase64: str = ""
    imageMime: str = ""


def _decode_entities(s: str) -> str:
    """HTML-Entities auflösen (wie LinkMetaParser im Client)."""
    def repl(m: re.Match) -> str:
        if m.group(1):
            try:
                return chr(int(m.group(1)))
            except (ValueError, OverflowError):
                return ""
        if m.group(2):
            try:
                return chr(int(m.group(2), 16))
            except (ValueError, OverflowError):
                return ""
        return _NAMED.get(m.group(3).lower(), "")
    return _ENTITY_RE.sub(repl, s)


def _valid_scheme(url: str) -> bool:
    return url.startswith("http://") or url.startswith("https://")


def _is_private_host(host: str) -> bool:
    """SSRF-Schutz: host darf nicht auf private/loopback-Adressen zeigen."""
    if not host:
        return False
    if host.lower() in ("localhost",) or host.endswith(".local") or host.endswith(".internal"):
        return True
    try:
        addrs = socket.getaddrinfo(host, None)
    except OSError:
        return True  # nicht auflösbar → nicht anfassen
    for fam, _, _, _, sa in addrs:
        addr = sa[0]
        try:
            ip = ipaddress.ip_address(addr)
        except ValueError:
            continue
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
            return True
    return False


class _UnsafeUrl(Exception):
    pass


def _normalized_target(url: str) -> str:
    """URL validieren + Host gegen SSRF prüfen."""
    if not _valid_scheme(url):
        raise _UnsafeUrl("nur http/https")
    parsed = urllib.parse.urlsplit(url)
    if not parsed.netloc or _is_private_host(parsed.hostname or ""):
        raise _UnsafeUrl("host nicht erlaubt")
    return url


def _open_no_redirect(url: str, accept: str) -> tuple[int | None, object, bytes | None]:
    """Ein GET ohne Redirect-Follow. Liefert (status, headers, body).

    3xx: opener.open würfe normalerweise HTTPError — das fangen wir hier
    ab und geben Status + Location-fähige headers zurück.
    """
    req = urllib.request.Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept": accept,
        "Accept-Language": "de-DE,de;q=0.9,en;q=0.8",
    })
    opener = urllib.request.build_opener(_NoRedirect())
    try:
        resp = opener.open(req, timeout=TIMEOUT_SECONDS)
    except urllib.error.HTTPError as e:
        # 3xx ohne auto-redirect = HTTPError mit Location im headers
        return e.code, e.headers, None
    except OSError:
        return None, None, None
    try:
        data = resp.read(MAX_HTML_CHARS + 1)
        return getattr(resp, "status", 200) or 200, resp.headers, data
    finally:
        resp.close()


def _request(url: str, accept: str) -> tuple[str, bytes, str]:
    """GET mit Redirect-Following (max. 5, jeder Hop SSRF-geprüft)."""
    current = _normalized_target(url)
    for _ in range(MAX_REDIRECTS):
        status, headers, data = _open_no_redirect(current, accept)
        if status is None:
            return None, None, None
        if 300 <= status < 400:
            location = headers.get("Location")
            if not location:
                return None, None, None
            current = _normalized_target(urllib.parse.urljoin(current, location))
            continue
        if status >= 400:
            return None, None, None
        content_type = (headers.get("Content-Type") or "").split(";")[0].strip().lower()
        return content_type, data, current
    return None, None, None


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Redirect NICHT automatisch folgen (wir lesen Location selbst)."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: N803
        return None


def _meta_props(html: str) -> dict[str, str]:
    """og:*/twitter:* Properties aus dem GANZEN HTML (max 1 MB)."""
    props: dict[str, str] = {}
    html = _RE_COMMENT_OR_SCRIPT.sub(" ", html)
    #<meta property= ... content=...> in beiden Attribut-Reihenfolgen
    for m in _RE_META.finditer(html):
        key = m.group(1) or m.group(4)
        value = m.group(2) or m.group(3)
        if key and value:
            key = key.lower()
            if key not in props:
                props[key] = value
    # Fallbacks: <title> (nur wenn og:title fehlt) + <meta name="description">
    title = _RE_TITLE.findall(html)
    if "title" not in props and title:
        props["title"] = max(title, key=len)  # längster Titel (SPA-Platzhalter sind kurz)
    return props


def _resolve_share_link(url: str) -> str | None:
    """Reddit-/s/-Share-Redirect (301) zu echter Post-URL auflösen."""
    if not _REDDIT_SHARE.match(url) and not _REDDIT_SHORT.match(url):
        return None
    status, headers, _ = _open_no_redirect(url, ACCEPT_ANY)
    if status is None or not (300 <= status < 400):
        return None
    location = headers.get("Location")
    if not location:
        return None
    resolved = urllib.parse.urljoin(url, location)
    return resolved.split("?", 1)[0]


def _reddit_oembed(url: str) -> dict[str, str] | None:
    """Titel/Subreddit/Autor über Reddits oembed (ohne Auth). Liefert nie ein Bild."""
    try:
        ctype, data, _ = _request(
            "https://www.reddit.com/oembed?url=" + urllib.parse.quote(url, safe=_SAFE_URL),
            ACCEPT_ANY,
        )
    except _UnsafeUrl:
        return None
    if ctype is None or not ctype.startswith("text/") and "json" not in ctype:
        return None
    if not data:
        return None
    try:
        raw = json.loads(data[:64_000].decode("utf-8", errors="replace"))
    except json.JSONDecodeError:
        return None
    if not isinstance(raw, dict):
        return None
    title = _decode_entities(str(raw.get("title", "")))
    if not title:
        return None
    author = str(raw.get("author_name", ""))
    # Subreddit aus html-Field ("<a href=…reddit.com/r/<sub>/…") —
    # derselbe Fallback wie im Client (RedditOembed.kt).
    site = ""
    m = re.search(r"reddit\.com/r/([A-Za-z0-9_]+)/", str(raw.get("html", "")))
    if m:
        site = f"r/{m.group(1)}"
    return {
        "title": title,
        "description": f"u/{author}" if author else "",
        "siteName": site or "reddit.com",
        "imageUrl": "",
    }


def get_link_meta(url: str, token: str) -> LinkMetaResponse:
    verify_token_str(token)

    # 1) Reddit-Share-Link → echte Post-URL
    resolved_share = _resolve_share_link(url)
    target = resolved_share or url

    # 2) Direkter Bild-Link → Daten durchreichen (base64 inline)
    try:
        content_type, data, _ = _request(target, ACCEPT_ANY)
    except _UnsafeUrl as e:
        raise HTTPException(status_code=400, detail=str(e))
    if content_type is None:
        raise HTTPException(status_code=502, detail="fetch fehlgeschlagen")
    if content_type.startswith("image/"):
        if data and len(data) <= MAX_IMAGE_BYTES:
            return LinkMetaResponse(
                title="",
                siteName=_domain_of(target),
                imageBase64=base64.b64encode(data).decode(),
                imageMime=content_type,
            )
        return LinkMetaResponse(title="", siteName=_domain_of(target))

    # 3) HTML → Metas. Keys OHNE og:/twitter:-Präfix (Regex-Gruppe
    #    hinter dem Doppelpunkt), twitter:-Fallbacks zuerst og:.
    html = data[:MAX_HTML_CHARS].decode("utf-8", errors="replace")
    props = _meta_props(html)
    title = _decode_entities(props.get("title", ""))
    description = _decode_entities(props.get("description", ""))
    site_name = _decode_entities(props.get("site_name", ""))
    image_url = props.get("image", "")
    # relative Bild-URLs → absolut
    if image_url:
        image_url = urllib.parse.urljoin(target, _decode_entities(image_url))

    # 4) Reddit-Post ohne og:image → oembed (mit AUFGELÖSTER URL!)
    if not image_url and _REDDIT_POST.match(target):
        meta = _reddit_oembed(target)
        if meta:
            return LinkMetaResponse(
                title=meta["title"],
                description=meta["description"],
                siteName=meta["siteName"],
                imageUrl="",
            )

    if not title and not image_url:
        raise HTTPException(status_code=404, detail="keine metadaten")

    # 5) og:image inline laden (spart dem Client CORS-Problem beim Bild)
    image_base64 = ""
    image_mime = ""
    if image_url and not content_type.startswith("image/"):
        try:
            ictype, idata, _ = _request(image_url, "image/*")
        except _UnsafeUrl:
            ictype, idata = None, None
        if ictype and ictype.startswith("image/") and idata and len(idata) <= MAX_IMAGE_BYTES:
            image_base64 = base64.b64encode(idata).decode()
            image_mime = ictype

    return LinkMetaResponse(
        title=title,
        description=description,
        siteName=site_name or _domain_of(target),
        imageUrl=image_url,
        imageBase64=image_base64,
        imageMime=image_mime,
    )


def _domain_of(url: str) -> str:
    host = urllib.parse.urlsplit(url).hostname or ""
    return host.removeprefix("www.")