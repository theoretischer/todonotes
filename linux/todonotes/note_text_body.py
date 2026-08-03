"""NoteTextBody — Port des Kotlin NoteTextBody.

Plain-Text-Notiz-Body mit Marker-Präfixen für Listen und
Markdown-Markern für Inline-Formatierung (**fett**, *kursiv*, __unterstrichen__).

Der Body ist ein einziger Text-String, zeilenweise mit \\n getrennt.
Präfixe:
  - Eintrag        → Bullet-Liste
  1. Eintrag       → Nummerierte Liste
  [ ] Eintrag      → Checkbox (offen)
  [x] Eintrag      → Checkbox (erledigt)
  → Eintrag        → Pfeil-Liste

Identisch zum Android-Format → Sync-kompatibel.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum
from typing import NamedTuple


class ListType(Enum):
    ORDERED = "ordered"
    BULLET = "bullet"
    ARROW = "arrow"
    CHECKBOX = "checkbox"


class InlineStyle(Enum):
    BOLD = "bold"
    ITALIC = "italic"
    UNDERLINE = "underline"


BULLET_PREFIX = "- "
CHECKBOX_OPEN = "[ ] "
CHECKBOX_DONE = "[x] "
ARROW_PREFIX = "→ "

MD_BOLD = "**"
MD_ITALIC = "*"
MD_UNDERLINE = "__"

_BOLD_REGEX = re.compile(r"\*\*([^*]+)\*\*")
_ITALIC_REGEX = re.compile(r"(?<!\*)\*([^*]+?)\*(?!\*)")
_UNDERLINE_REGEX = re.compile(r"__([^_]+)__")

_ORDERED_PREFIX_REGEX = re.compile(r"^(\d+)\. ")


@dataclass
class NoteLine:
    content: str
    type: ListType | None = None
    number: int = 0
    checked: bool = False


class InlineSegment(NamedTuple):
    text: str
    style: InlineStyle | None


# ── Präfix-Erkennung ────────────────────────────────────────────

def detect_list_type(line: str) -> ListType | None:
    if line.startswith(BULLET_PREFIX):
        return ListType.BULLET
    if line.startswith(CHECKBOX_OPEN) or line.startswith(CHECKBOX_DONE):
        return ListType.CHECKBOX
    if line.startswith(ARROW_PREFIX):
        return ListType.ARROW
    if _ORDERED_PREFIX_REGEX.match(line):
        return ListType.ORDERED
    return None


def strip_prefix(line: str) -> str:
    if line.startswith(BULLET_PREFIX):
        return line[len(BULLET_PREFIX):]
    if line.startswith(CHECKBOX_OPEN):
        return line[len(CHECKBOX_OPEN):]
    if line.startswith(CHECKBOX_DONE):
        return line[len(CHECKBOX_DONE):]
    if line.startswith(ARROW_PREFIX):
        return line[len(ARROW_PREFIX):]
    m = _ORDERED_PREFIX_REGEX.match(line)
    if m:
        return line[m.end():]
    return line


def is_checkbox_checked(line: str) -> bool:
    return line.startswith(CHECKBOX_DONE)


# ── Zeilen-Splitting ────────────────────────────────────────────

def to_lines(text: str) -> list[NoteLine]:
    """Zerlegt den Body-Text in Zeilen mit ListType + Content."""
    if not text:
        return [NoteLine("", None)]
    raw_lines = text.split("\n")
    result: list[NoteLine] = []
    ordered_counter = 0
    for raw_line in raw_lines:
        lt = detect_list_type(raw_line)
        content = strip_prefix(raw_line)
        if lt == ListType.ORDERED:
            ordered_counter += 1
            number = ordered_counter
        else:
            ordered_counter = 0
            number = 0
        checked = lt == ListType.CHECKBOX and is_checkbox_checked(raw_line)
        result.append(NoteLine(content, lt, number, checked))
    return result


def from_lines(lines: list[NoteLine]) -> str:
    """Baut aus Zeilen den Body-Text mit Präfixen."""
    parts: list[str] = []
    ordered_counter = 0
    for line in lines:
        if line.type is None:
            ordered_counter = 0
        elif line.type == ListType.ORDERED:
            ordered_counter += 1
            parts.append(f"{ordered_counter}. ")
        elif line.type == ListType.BULLET:
            ordered_counter = 0
            parts.append(BULLET_PREFIX)
        elif line.type == ListType.CHECKBOX:
            ordered_counter = 0
            parts.append(CHECKBOX_DONE if line.checked else CHECKBOX_OPEN)
        elif line.type == ListType.ARROW:
            ordered_counter = 0
            parts.append(ARROW_PREFIX)
        parts.append(line.content)
        parts.append("\n")
    result = "".join(parts)
    return result.rstrip("\n") if result else ""


# ── Checkbox Toggle ─────────────────────────────────────────────

def toggle_checkbox(text: str, line_start: int) -> str:
    """Toggelt [ ] ↔ [x] an Position line_start."""
    line_end = text.find("\n", line_start)
    if line_end < 0:
        line_end = len(text)
    line = text[line_start:line_end]
    if line.startswith(CHECKBOX_OPEN):
        new_line = CHECKBOX_DONE + line[len(CHECKBOX_OPEN):]
    elif line.startswith(CHECKBOX_DONE):
        new_line = CHECKBOX_OPEN + line[len(CHECKBOX_DONE):]
    else:
        return text
    return text[:line_start] + new_line + text[line_end:]


# ── Inline-Styles ───────────────────────────────────────────────

def _marker(style: InlineStyle) -> str:
    if style == InlineStyle.BOLD:
        return MD_BOLD
    if style == InlineStyle.ITALIC:
        return MD_ITALIC
    return MD_UNDERLINE


@dataclass
class _Match:
    start: int
    end: int  # exclusive
    style: InlineStyle
    content: str


@dataclass
class _VisibleText:
    text: str
    removed_raw: set[int]
    styles: list[tuple[range, InlineStyle]]  # ranges in visible text


def _collect_matches(text: str) -> list[_Match]:
    """Sammelt überlappungsfreie Inline-Style-Matches mit Raw-Offsets.

    Wie _build_visible, aber liefert nur die Matches (für TextTag-Anwendung).
    Sortiert nach Position, überspringt überlappende Matches.
    """
    matches: list[_Match] = []
    for m in _BOLD_REGEX.finditer(text):
        matches.append(_Match(m.start(), m.end(), InlineStyle.BOLD, m.group(1)))
    for m in _ITALIC_REGEX.finditer(text):
        matches.append(_Match(m.start(), m.end(), InlineStyle.ITALIC, m.group(1)))
    for m in _UNDERLINE_REGEX.finditer(text):
        matches.append(_Match(m.start(), m.end(), InlineStyle.UNDERLINE, m.group(1)))
    matches.sort(key=lambda x: x.start)

    # Überlappende Matches herausfiltern (wie _build_visible)
    result: list[_Match] = []
    used: list[range] = []
    for m in matches:
        if any(r.start < m.end and m.start < r.stop for r in used):
            continue
        result.append(m)
        used.append(range(m.start, m.end))
    return result


def _build_visible(text: str) -> _VisibleText:
    sb: list[str] = []
    removed_raw: set[int] = set()
    styles: list[tuple[range, InlineStyle]] = []

    matches: list[_Match] = []
    for m in _BOLD_REGEX.finditer(text):
        matches.append(_Match(m.start(), m.end(), InlineStyle.BOLD, m.group(1)))
    for m in _ITALIC_REGEX.finditer(text):
        matches.append(_Match(m.start(), m.end(), InlineStyle.ITALIC, m.group(1)))
    for m in _UNDERLINE_REGEX.finditer(text):
        matches.append(_Match(m.start(), m.end(), InlineStyle.UNDERLINE, m.group(1)))
    matches.sort(key=lambda x: x.start)

    pos = 0
    used: list[range] = []
    for m in matches:
        # Überlappende Matches überspringen
        if any(r.start < m.end and m.start < r.stop for r in used):
            continue
        open_len = len(_marker(m.style))
        sb.append(text[pos:m.start])
        for i in range(m.start, m.start + open_len):
            removed_raw.add(i)
        v_start = len("".join(sb))
        sb.append(m.content)
        v_end = v_start + len(m.content)  # exclusive
        for i in range(m.end - open_len, m.end):
            removed_raw.add(i)
        styles.append((range(v_start, v_end), m.style))
        pos = m.end
        used.append(range(m.start, m.end))
    sb.append(text[pos:])
    return _VisibleText("".join(sb), removed_raw, styles)


def parse_inline_styles(text: str) -> list[InlineSegment]:
    """Zerlegt text in sichtbare Segmente (Marker entfernt) + Stil."""
    vis = _build_visible(text)
    if not vis.text:
        return []
    result: list[InlineSegment] = []
    pos = 0
    for r, style in vis.styles:
        if pos < r.start:
            result.append(InlineSegment(vis.text[pos:r.start], None))
        result.append(InlineSegment(vis.text[r.start:r.stop], style))
        pos = r.stop
    if pos < len(vis.text):
        result.append(InlineSegment(vis.text[pos:], None))
    return result if result else [InlineSegment(vis.text, None)]


def raw_to_visual_offset(text: str, raw_offset: int) -> int:
    if raw_offset <= 0:
        return 0
    vis = _build_visible(text)
    if raw_offset >= len(text):
        return len(vis.text)
    visual = 0
    for i in range(raw_offset):
        if i not in vis.removed_raw:
            visual += 1
    return visual


def visual_to_raw_offset(text: str, visual_offset: int) -> int:
    if visual_offset <= 0:
        return 0
    vis = _build_visible(text)
    if visual_offset >= len(vis.text):
        return len(text)
    visual = 0
    i = 0
    while i < len(text):
        if visual >= visual_offset:
            return i
        if i not in vis.removed_raw:
            visual += 1
        i += 1
    return len(text)


def toggle_inline_style(
    text: str, start: int, end: int, style: InlineStyle
) -> tuple[str, int, int]:
    """Togglet style um die Auswahl start..end. → (newText, newStart, newEnd)."""
    m = _marker(style)
    before = text[:start]
    sel = text[start:end]
    after = text[end:]

    # Fall A: Auswahl enthält die Marker selbst
    if sel.startswith(m) and sel.endswith(m) and len(sel) > 2 * len(m):
        inner = sel[len(m):-len(m)]
        return before + inner + after, start, start + len(inner)
    # Fall B: Marker umschließen die Auswahl von außen
    if before.endswith(m) and after.startswith(m):
        new_before = before[:-len(m)]
        new_after = after[len(m):]
        return new_before + sel + new_after, start - len(m), start - len(m) + len(sel)
    # Fall C: einschalten
    new_text = before + m + sel + m + after
    return new_text, start + len(m), start + len(m) + len(sel)


def migrate_if_needed(body_json: str) -> str:
    """Migriert alten Block-JSON zu Plain Text, falls nötig.

    Falls der bodyJson im alten Block-Format (F3) gespeichert ist,
    wird er zu Plain Text konvertiert. Ist er bereits Plain Text,
    wird er unverändert zurückgegeben.
    """
    if not body_json or not body_json.strip():
        return ""
    trimmed = body_json.strip()
    looks_like_json = trimmed.startswith("[") or trimmed.startswith("{")
    if looks_like_json:
        # Vereinfachte Migration: Block-JSON → Plain Text
        # Da der Linux-Client kein NoteBodyJson-Decoder hat, versuchen
        # wir nur die einfachste Form (leere Liste → leer, sonst roh).
        if trimmed == "[]":
            return ""
        # Komplexere Block-JSON: roh zurückgeben (wird in TextView angezeigt)
        # TODO: echtes JSON-Parsing wenn bestehende Notizen Block-Format haben
        return body_json
    return body_json


# ── Pango-Markup für GTK-Rendering ──────────────────────────────

def to_pango_markup(text: str) -> str:
    """Wandelt Markdown-Inline-Styles in Pango-Markup um.

    **fett** → <b>fett</b>
    *kursiv* → <i>kursiv</i>
    __unterstrichen__ → <u>unterstrichen</u>
    Sonderzeichen (<, >, &) werden escaped.
    """
    if not text:
        return ""
    # Escape HTML-Sonderzeichen
    escaped = (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )
    # Styles anwenden (Reihenfolge: bold, underline, italic — um
    # Konflikte bei ** vs * zu vermeiden)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", escaped)
    escaped = re.sub(r"__([^_]+)__", r"<u>\1</u>", escaped)
    escaped = re.sub(r"(?<!\*)\*([^*]+?)\*(?!\*)", r"<i>\1</i>", escaped)
    return escaped
