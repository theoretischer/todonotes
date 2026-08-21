"""Auth-Logik: Account-Verwaltung, Token-Erzeugung, Token-Validierung.

Multi-User: jeder Account hat username + password_hash (bcrypt).
Tokens sind opaque Strings (secrets.token_urlsafe), in der `tokens`-Tabelle
gespeichert. Ein Account kann mehrere Tokens haben (mehrere Geräte + Web).

Übergangs-Auth: ist in der .env ein SYNC_TOKEN gesetzt, wird es als
Static-Secret akzeptiert und mappt auf den Legacy-User (`db.LEGACY_USER_ID`).
So funktioniert die alte Android-App weiter, bis sie auf Login umgestellt ist.
"""
from __future__ import annotations

import os
import secrets
import time

import bcrypt
from fastapi import HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials

from . import db

_SYNC_TOKEN = os.environ.get("SYNC_TOKEN", "")


# --- Password-Hashing (bcrypt) ---

def _hash_password(password: str) -> str:
    return bcrypt.hashpw(
        password.encode("utf-8"), bcrypt.gensalt(rounds=12)
    ).decode("utf-8")


def _verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(
            password.encode("utf-8"), password_hash.encode("utf-8")
        )
    except (ValueError, TypeError):
        return False


# --- Token-Erzeugung ---

def _generate_token() -> str:
    return secrets.token_urlsafe(32)


def _create_token(conn, user_id: str) -> str:
    token = _generate_token()
    now = int(time.time() * 1000)
    token_id = secrets.token_urlsafe(16)
    conn.execute(
        "INSERT INTO tokens (id, user_id, token, created_at, last_used_at) "
        "VALUES (?, ?, ?, ?, ?)",
        (token_id, user_id, token, now, now),
    )
    return token


# --- Public API ---

def register_user(username: str, password: str) -> tuple[str, str]:
    """Account erstellen + Token erzeugen.

    Liefert (user_id, token). Wirft 409 wenn username belegt.
    """
    if not username or not password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="username und password dürfen nicht leer sein",
        )
    if len(password) < 6:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Passwort muss mindestens 6 Zeichen lang sein",
        )
    now = int(time.time() * 1000)
    user_id = secrets.token_urlsafe(16)
    password_hash = _hash_password(password)
    with db.db() as conn:
        # Username eindeutig?
        cur = conn.execute(
            "SELECT id FROM users WHERE username = ?", (username,)
        )
        if cur.fetchone() is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="username bereits vergeben",
            )
        conn.execute(
            "INSERT INTO users (id, username, password_hash, created_at, is_legacy) "
            "VALUES (?, ?, ?, ?, 0)",
            (user_id, username, password_hash, now),
        )
        token = _create_token(conn, user_id)
    return user_id, token


def login_user(username: str, password: str) -> tuple[str, str]:
    """Login mit username/password → (user_id, token).

    Wirft 401 bei falschen Credentials.
    Legacy-User kann sich nicht einloggen (password_hash = !LEGACY_NO_LOGIN).
    """
    with db.db() as conn:
        cur = conn.execute(
            "SELECT id, password_hash, is_legacy FROM users WHERE username = ?",
            (username,),
        )
        row = cur.fetchone()
        if row is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="falscher username oder passwort",
            )
        if row["is_legacy"]:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="legacy-user kann nicht einloggen — bitte migrate-legacy nutzen",
            )
        if not _verify_password(password, row["password_hash"]):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="falscher username oder passwort",
            )
        token = _create_token(conn, row["id"])
    return row["id"], token


def migrate_legacy(username: str, password: str) -> tuple[str, str]:
    """Registriert neuen Account + überträgt alle Legacy-Daten auf ihn.

    Liefert (user_id, token). Wirft 409 wenn username belegt.
    Wirft 400 wenn es keine Legacy-Daten gibt.
    """
    user_id, token = register_user(username, password)
    with db.db() as conn:
        # Anzahl Legacy-Daten zählen (über alle Tabellen).
        count = 0
        for table in (
            "todos", "habits", "habit_logs", "habit_history",
            "folders", "notes", "chat_messages",
        ):
            cur = conn.execute(
                f"SELECT COUNT(*) AS c FROM {table} WHERE userId = ?",
                (db.LEGACY_USER_ID,),
            )
            count += cur.fetchone()["c"]
        # Daten auf neuen User übertragen.
        for table in (
            "todos", "habits", "habit_logs", "habit_history",
            "folders", "notes", "chat_messages",
        ):
            conn.execute(
                f"UPDATE {table} SET userId = ? WHERE userId = ?",
                (user_id, db.LEGACY_USER_ID),
            )
    return user_id, token


def resolve_token(token: str) -> str | None:
    """Token → user_id (oder None wenn ungültig).

    Aktualisiert last_used_at. Prüft nicht das Static-Secret —
    das macht `verify_request_token` separat.
    """
    now = int(time.time() * 1000)
    with db.db() as conn:
        cur = conn.execute(
            "SELECT user_id FROM tokens WHERE token = ?",
            (token,),
        )
        row = cur.fetchone()
        if row is None:
            return None
        conn.execute(
            "UPDATE tokens SET last_used_at = ? WHERE token = ?",
            (now, token),
        )
        return row["user_id"]


def verify_request_token(
    creds: HTTPAuthorizationCredentials,
) -> str:
    """FastAPI-Dependency: prüft Bearer-Token, liefert user_id.

    Akzeptiert BEIDE:
    1. Static-Secret (SYNC_TOKEN aus .env) → mappt auf Legacy-User.
       Für Übergangs-Compat mit alter Android-App.
    2. DB-Token (tokens-Tabelle) → echte user_id.
    """
    token = creds.credentials
    # 1. Static-Secret?
    if _SYNC_TOKEN and secrets.compare_digest(
        token.encode(), _SYNC_TOKEN.encode()
    ):
        return db.LEGACY_USER_ID
    # 2. DB-Token?
    user_id = resolve_token(token)
    if user_id is not None:
        return user_id
    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="invalid token",
    )
