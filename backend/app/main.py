"""FastAPI-App: /health, /auth/*, /sync.

Auth: Account-basiert (Multi-User). /sync validiert Bearer-Token gegen
tokens-Tabelle (oder SYNC_TOKEN-Static-Secret → Legacy-User).
"""
from __future__ import annotations

import os
import time
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Request, UploadFile, File, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.staticfiles import StaticFiles

from . import auth, db, sync
from .models import (
    AdminCreateUserRequest,
    AdminUserResponse,
    AuthResponse,
    LoginRequest,
    MigrateLegacyRequest,
    PasswordConfirmRequest,
    RegisterRequest,
    SetupRequest,
    SetupStatusResponse,
    SyncRequest,
    SyncResponse,
    UpdateProfileRequest,
    UpdateSettingsRequest,
    UserProfileResponse,
)

# --- Auth (Bearer-Token: DB-Token ODER Static-Secret → Legacy-User) ---
security = HTTPBearer()


def verify_token(
    creds: HTTPAuthorizationCredentials = Depends(security),
) -> str:
    """Liefert user_id des authentifizierten Users."""
    return auth.verify_request_token(creds)


# --- App lifecycle: DB initialisieren ---
app = FastAPI(title="TodoNotes Sync", version="0.2.0")

# CORS: lokale Entwicklung (Web auf :8090, Backend auf :8001).
# Produktiv läuft beides auf gleicher Origin → CORS irrelevant.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


# COOP/COEP-Header auf ALLE Responses (auch statische Files).
# Nötig für SharedArrayBuffer/Atomics → SQLite-Wasm-OPFS im Browser.
# (Lokaler Dev-Server /tmp/opfs_server.py setzt dieselben Header.)
@app.middleware("http")
async def coop_coep_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["Cross-Origin-Opener-Policy"] = "same-origin"
    response.headers["Cross-Origin-Embedder-Policy"] = "require-corp"
    # index.html + alle .js-Dateien nie cachen: composeApp.js und die
    # Zahlen-Chunks (229.js ...) haben stabile Namen, ändern aber ihren
    # Inhalt bei jedem Build → würden sonst tagelang die alte App-Logik
    # liefern (NPM "Cache Assets" setzt sonst max-age=~14h drauf).
    # .wasm-Dateien sind echte Hash-Namen (ändern sich mit dem Inhalt) →
    # dürfen ewig gecacht werden (schnellere Ladezeiten).
    path = request.url.path
    if path in ("/", "/index.html") or path.endswith(".js") or path.endswith(".html"):
        response.headers["Cache-Control"] = "no-cache"
    elif path.endswith(".wasm"):
        response.headers["Cache-Control"] = "public, max-age=31536000, immutable"
    return response


@app.on_event("startup")
def _startup() -> None:
    db_path = os.environ.get("DB_PATH", "data/todonotes.db")
    db.init_db(db_path)


@app.get("/health")
def health() -> dict:
    """Public health-check (kein Token nötig)."""
    return {"status": "ok", "time": int(time.time() * 1000)}


# --- Auth-Endpunkte ---

@app.post("/auth/register", response_model=AuthResponse)
def register(req: RegisterRequest) -> AuthResponse:
    """Account erstellen → Token."""
    user_id, token = auth.register_user(req.username, req.password)
    return AuthResponse(user_id=user_id, token=token)


@app.post("/auth/login", response_model=AuthResponse)
def login(req: LoginRequest) -> AuthResponse:
    """Login → Token."""
    user_id, token = auth.login_user(req.username, req.password)
    return AuthResponse(user_id=user_id, token=token)


@app.post("/auth/migrate-legacy", response_model=AuthResponse)
def migrate_legacy(req: MigrateLegacyRequest) -> AuthResponse:
    """Account erstellen + alle Legacy-Daten auf ihn übertragen → Token."""
    user_id, token = auth.migrate_legacy(req.username, req.password)
    return AuthResponse(user_id=user_id, token=token)


# --- M7d-3: Setup + Profil + Admin ---

@app.get("/auth/setup-status", response_model=SetupStatusResponse)
def setup_status_endpoint() -> SetupStatusResponse:
    """Public: ob Admin existiert + ob Registrierung offen ist."""
    s = auth.setup_status()
    return SetupStatusResponse(**s)


@app.post("/auth/setup", response_model=AuthResponse)
def setup_endpoint(req: SetupRequest) -> AuthResponse:
    """Ersten Admin erstellen + Legacy-Daten migrieren (nur wenn keiner da)."""
    user_id, token = auth.setup_admin(req.username, req.password, req.display_name)
    return AuthResponse(user_id=user_id, token=token)


@app.get("/auth/me", response_model=UserProfileResponse)
def get_me(user_id: str = Depends(verify_token)) -> UserProfileResponse:
    """Eigenes Profil abrufen."""
    p = auth.get_user_profile(user_id)
    return UserProfileResponse(**p)


@app.patch("/auth/me", response_model=UserProfileResponse)
def update_me(
    req: UpdateProfileRequest,
    user_id: str = Depends(verify_token),
) -> UserProfileResponse:
    """Eigenes Profil bearbeiten (display_name, password)."""
    auth.update_profile(user_id, req.display_name, req.password)
    p = auth.get_user_profile(user_id)
    return UserProfileResponse(**p)


@app.post("/auth/me/avatar")
def upload_avatar(
    req: dict,
    user_id: str = Depends(verify_token),
) -> dict:
    """Profilbild hochladen (Base64-JSON)."""
    import base64
    avatars_dir = Path(os.environ.get("DATA_DIR", "data")) / "avatars"
    avatars_dir.mkdir(parents=True, exist_ok=True)
    ext = req.get("ext", ".png")
    if not ext.startswith("."):
        ext = "." + ext
    filename = f"{user_id}{ext}"
    filepath = avatars_dir / filename
    content = base64.b64decode(req["data"])
    filepath.write_bytes(content)
    auth.set_profile_picture(user_id, filename)
    return {"filename": filename}


@app.get("/avatars/{user_id}")
def get_avatar(user_id: str):
    """Profilbild eines Users ausliefern."""
    filename = auth.get_profile_picture_filename(user_id)
    if filename is None:
        raise HTTPException(status_code=404, detail="kein profilbild")
    avatars_dir = Path(os.environ.get("DATA_DIR", "data")) / "avatars"
    filepath = avatars_dir / filename
    if not filepath.exists():
        raise HTTPException(status_code=404, detail="profilbild nicht gefunden")
    return FileResponse(filepath)


@app.get("/admin/users", response_model=list[AdminUserResponse])
def admin_list_users_endpoint(user_id: str = Depends(verify_token)) -> list[AdminUserResponse]:
    """Alle User auflisten (Admin only)."""
    auth.require_admin(user_id)
    users = auth.admin_list_users()
    return [AdminUserResponse(**u) for u in users]


@app.post("/admin/users", response_model=AdminUserResponse)
def admin_create_user_endpoint(
    req: AdminCreateUserRequest,
    user_id: str = Depends(verify_token),
) -> AdminUserResponse:
    """User anlegen (Admin only)."""
    auth.require_admin(user_id)
    new_id = auth.admin_create_user(req.username, req.password, req.display_name, req.is_admin)
    users = auth.admin_list_users()
    u = next((x for x in users if x["user_id"] == new_id), users[0])
    return AdminUserResponse(**u)


@app.delete("/admin/users/{target_id}")
def admin_delete_user_endpoint(
    target_id: str,
    user_id: str = Depends(verify_token),
) -> dict:
    """User löschen (Admin only, nicht sich selbst)."""
    auth.require_admin(user_id)
    if target_id == user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="man kann sich nicht selbst löschen",
        )
    auth.admin_delete_user(target_id)
    return {"ok": True}


@app.patch("/admin/settings")
def admin_update_settings_endpoint(
    req: UpdateSettingsRequest,
    user_id: str = Depends(verify_token),
) -> dict:
    """App-Settings ändern (Admin only)."""
    auth.require_admin(user_id)
    return auth.update_settings(req.open_registration)


# --- Daten-Löschen (mit Passwort-Bestätigung) ---

@app.delete("/me/data")
def wipe_my_data(
    req: PasswordConfirmRequest,
    user_id: str = Depends(verify_token),
) -> dict:
    """Löscht ALLE Daten des eingeloggten Users (Account bleibt).

    Todos, Habits, Habit-Logs/History, Notizen, Chat-Nachrichten, Folder.
    User-Account + Passwort bleiben erhalten → danach neu einloggen.
    """
    if not auth.verify_user_password(user_id, req.password):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="falsches passwort",
        )
    with db.db() as conn:
        conn.execute("DELETE FROM chat_messages WHERE userId = ?", (user_id,))
        conn.execute("DELETE FROM notes WHERE userId = ?", (user_id,))
        conn.execute("DELETE FROM folders WHERE userId = ?", (user_id,))
        conn.execute("DELETE FROM habit_history WHERE userId = ?", (user_id,))
        conn.execute("DELETE FROM habit_logs WHERE userId = ?", (user_id,))
        conn.execute("DELETE FROM habits WHERE userId = ?", (user_id,))
        conn.execute("DELETE FROM todos WHERE userId = ?", (user_id,))
        # wipe_epoch setzen → andere Geräte dieses Users leeren lokal.
        db.set_setting(conn, "wipe_epoch", str(int(time.time() * 1000)))
    return {"ok": True}


@app.delete("/admin/wipe-all")
def wipe_all_data(
    req: PasswordConfirmRequest,
    user_id: str = Depends(verify_token),
) -> dict:
    """Löscht ALLE Daten ALLER User + alle User-Accounts (Factory-Reset).

    Nur Admin. Nach Aufruf: Setup-Screen (neuer Admin nötig).
    app_settings (open_registration) bleibt erhalten.
    """
    auth.require_admin(user_id)
    if not auth.verify_user_password(user_id, req.password):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="falsches passwort",
        )
    # Avatars auf Disk löschen.
    avatars_dir = Path(os.environ.get("DATA_DIR", "data")) / "avatars"
    if avatars_dir.exists():
        for f in avatars_dir.iterdir():
            if f.is_file():
                f.unlink()
    with db.db() as conn:
        # Alle Daten-Tabellen leeren (User + Inhalte).
        for table in (
            "chat_messages", "notes", "folders",
            "habit_history", "habit_logs", "habits",
            "todos", "tokens", "users",
        ):
            conn.execute(f"DELETE FROM {table}")
        # wipe_epoch setzen → alle Geräte leeren lokal beim nächsten Sync.
        db.set_setting(conn, "wipe_epoch", str(int(time.time() * 1000)))
    return {"ok": True}


# --- Sync-Endpoint ---

@app.post("/sync", response_model=SyncResponse)
def sync_endpoint(
    req: SyncRequest,
    user_id: str = Depends(verify_token),
) -> SyncResponse:
    """Haupt-Endpoint: nimmt Client-Änderungen, liefert Server-Änderungen.

    user_id kommt vom verify_token-Dependency (DB-Token oder Legacy-User).
    """
    server_now = int(time.time() * 1000)
    server_changes, applied_count = sync.sync(req.last_synced_at, req.changes, user_id, server_now)
    # newSyncedAt = server_now (KEIN +1 mehr). Das +1 machte Rows mit
    # updatedAt=server_now unsichtbar für Clients deren lastSyncedAt
    # bereits >= server_now war → Datenverlust bei lokalem DB-Verlust.
    # Full Down-Sync macht +1 überflüssig: der Client bekommt ohnehin
    # alle Rows, LWW verhindert Churn.
    new_synced_at = server_now
    # wipe_epoch auslesen → an Client weitergeben, damit dieser seine
    # lokale DB leeren kann wenn sie veraltet ist.
    with db.db() as conn:
        wipe_epoch = int(db.get_setting(conn, "wipe_epoch", "0"))
    # Andere verbundene Clients nur benachrichtigen wenn tatsächlich
    # Rows geändert wurden (applied_count > 0) UND der Client gepusht hat
    # (notify=true). Verhindert Ping-Pong UND unnötige Pulls bei
    # unveränderten Re-Pushes (Full Up-Sync sendet alle Rows, LWW
    # skippt unveränderte → applied_count=0 → kein Notify).
    if req.notify and applied_count > 0:
        from .event_bus import event_bus
        event_bus.publish(user_id, except_client_id=req.client_id or "")
    return SyncResponse(new_synced_at=new_synced_at, server_changes=server_changes, wipe_epoch=wipe_epoch)


@app.get("/sync/events")
async def sync_events(
    token: str,
    request: Request,
    client_id: str = "",
):
    """SSE-Stream: pusht 'sync'-Events an verbundene Clients.

    token als Query-Parameter (SSE kann keine Custom-Header senden).
    client_id: damit der Server diesen Client beim publish() exclude kann
    (kein Ping-Pong). Bei jedem POST /sync eines anderen Clients feuert hier
    ein Event → der Client pullt sofort neue Daten.
    """
    # Token validieren (gleiches wie verify_token, aber als Query-Param).
    try:
        user_id = auth.verify_token_str(token)
    except HTTPException:
        raise HTTPException(status_code=401, detail="token ungültig")
    from .event_bus import event_bus
    q = event_bus.subscribe(user_id, client_id)

    async def event_stream():
        try:
            while True:
                if await request.is_disconnected():
                    break
                event = await q.get()
                if event == "__keepalive__":
                    yield ": keepalive\n\n"
                else:
                    yield f"event: sync\ndata: {event}\n\n"
        finally:
            event_bus.unsubscribe(user_id, q)

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# --- Statische Web-Files (für Wasm-App, gefüllt in Phase M9) ---
# Nur mounten wenn Verzeichnis existiert — sonst gibt es 404 auf /.
_web_dir = os.environ.get("WEB_DIR", "data/web")
import pathlib
if pathlib.Path(_web_dir).is_dir():
    app.mount("/", StaticFiles(directory=_web_dir, html=True), name="web")
