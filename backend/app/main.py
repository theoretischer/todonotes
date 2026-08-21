"""FastAPI-App: /health, /auth/*, /sync.

Auth: Account-basiert (Multi-User). /sync validiert Bearer-Token gegen
tokens-Tabelle (oder SYNC_TOKEN-Static-Secret → Legacy-User).
"""
from __future__ import annotations

import os
import time

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.staticfiles import StaticFiles

from . import auth, db, sync
from .models import (
    AuthResponse,
    LoginRequest,
    MigrateLegacyRequest,
    RegisterRequest,
    SyncRequest,
    SyncResponse,
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


# --- Sync-Endpoint ---

@app.post("/sync", response_model=SyncResponse)
def sync_endpoint(
    req: SyncRequest,
    user_id: str = Depends(verify_token),
) -> SyncResponse:
    """Haupt-Endpoint: nimmt Client-Änderungen, liefert Server-Änderungen.

    user_id kommt vom verify_token-Dependency (DB-Token oder Legacy-User).
    """
    server_changes = sync.sync(req.last_synced_at, req.changes, user_id)
    new_synced_at = int(time.time() * 1000)
    return SyncResponse(new_synced_at=new_synced_at, server_changes=server_changes)


# --- Statische Web-Files (für Wasm-App, gefüllt in Phase M9) ---
# Nur mounten wenn Verzeichnis existiert — sonst gibt es 404 auf /.
_web_dir = os.environ.get("WEB_DIR", "data/web")
import pathlib
if pathlib.Path(_web_dir).is_dir():
    app.mount("/", StaticFiles(directory=_web_dir, html=True), name="web")
