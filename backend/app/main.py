"""FastAPI-App: /health + /sync. Bearer-Token-Auth via SYNC_TOKEN."""
from __future__ import annotations

import os
import secrets
import time

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import db, sync
from .models import SyncRequest, SyncResponse

# --- Token-Auth (Shared-Secret) ---
_sync_token = os.environ.get("SYNC_TOKEN", "")
security = HTTPBearer()


def verify_token(
    creds: HTTPAuthorizationCredentials = Depends(security),
) -> str:
    if not _sync_token:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="SYNC_TOKEN nicht konfiguriert",
        )
    # Constant-time compare (timing-safe).
    if not secrets.compare_digest(
        creds.credentials.encode(), _sync_token.encode()
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid token",
        )
    return creds.credentials


# --- App lifecycle: DB initialisieren ---
app = FastAPI(title="TodoNotes Sync", version="0.1.0")


@app.on_event("startup")
def _startup() -> None:
    db_path = os.environ.get("DB_PATH", "data/todonotes.db")
    db.init_db(db_path)


@app.get("/health")
def health() -> dict:
    """Public health-check (kein Token nötig)."""
    return {"status": "ok", "time": int(time.time() * 1000)}


@app.post("/sync", response_model=SyncResponse, dependencies=[Depends(verify_token)])
def sync_endpoint(req: SyncRequest) -> SyncResponse:
    """Haupt-Endpoint: nimmt Client-Änderungen, liefert Server-Änderungen."""
    server_changes = sync.sync(req.last_synced_at, req.changes)
    new_synced_at = int(time.time() * 1000)
    return SyncResponse(new_synced_at=new_synced_at, server_changes=server_changes)
