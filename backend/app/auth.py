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
    Wirft 403 wenn open_registration deaktiviert.
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
        # Open Registration prüfen.
        open_reg = db.get_setting(conn, "open_registration", "0") == "1"
        if not open_reg and _admin_exists(conn):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Registrierung ist deaktiviert",
            )
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


# --- M7d-3: Setup, Profil, Admin ---

def _admin_exists(conn) -> bool:
    """Prüft ob mindestens ein Admin-Account existiert."""
    cur = conn.execute("SELECT 1 FROM users WHERE is_admin = 1 LIMIT 1")
    return cur.fetchone() is not None


def setup_status() -> dict:
    """Liefert Setup-Status (ohne Login): admin_exists, open_registration."""
    with db.db() as conn:
        return {
            "admin_exists": _admin_exists(conn),
            "open_registration": db.get_setting(conn, "open_registration", "0") == "1",
        }


def setup_admin(username: str, password: str, display_name: str = "") -> tuple[str, str]:
    """Ersten Admin-Account erstellen + Legacy-Daten migrieren.

    Nur erlaubt wenn noch kein Admin existiert. Legacy-Daten werden auf
    den neuen Admin übertragen (damit bestehende Todos/Notizen erhalten
    bleiben).
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
        if _admin_exists(conn):
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Admin existiert bereits",
            )
        cur = conn.execute(
            "SELECT id FROM users WHERE username = ?", (username,)
        )
        if cur.fetchone() is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="username bereits vergeben",
            )
        conn.execute(
            "INSERT INTO users (id, username, password_hash, created_at, is_legacy, is_admin, display_name) "
            "VALUES (?, ?, ?, ?, 0, 1, ?)",
            (user_id, username, password_hash, now, display_name or username),
        )
        token = _create_token(conn, user_id)
        # Legacy-Daten auf den neuen Admin übertragen.
        for table in (
            "todos", "habits", "habit_logs", "habit_history",
            "folders", "notes", "chat_messages",
        ):
            conn.execute(
                f"UPDATE {table} SET userId = ? WHERE userId = ?",
                (user_id, db.LEGACY_USER_ID),
            )
    return user_id, token


def get_user_profile(user_id: str) -> dict:
    """Liefert Profil des eingeloggten Users."""
    with db.db() as conn:
        cur = conn.execute(
            "SELECT id, username, display_name, is_admin, profile_picture "
            "FROM users WHERE id = ?",
            (user_id,),
        )
        row = cur.fetchone()
        if row is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="user nicht gefunden",
            )
        return {
            "user_id": row["id"],
            "username": row["username"],
            "display_name": row["display_name"] or row["username"],
            "is_admin": bool(row["is_admin"]),
            "profile_picture": row["profile_picture"],
        }


def update_profile(
    user_id: str,
    display_name: str | None = None,
    password: str | None = None,
) -> None:
    """Eigenes Profil bearbeiten: display_name und/oder passwort ändern."""
    with db.db() as conn:
        if display_name is not None:
            if not display_name.strip():
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="display_name darf nicht leer sein",
                )
            conn.execute(
                "UPDATE users SET display_name = ? WHERE id = ?",
                (display_name.strip(), user_id),
            )
        if password is not None:
            if len(password) < 6:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Passwort muss mindestens 6 Zeichen lang sein",
                )
            conn.execute(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                (_hash_password(password), user_id),
            )


def set_profile_picture(user_id: str, filename: str) -> None:
    """Profilbild-Dateiname für einen User setzen."""
    with db.db() as conn:
        conn.execute(
            "UPDATE users SET profile_picture = ? WHERE id = ?",
            (filename, user_id),
        )


def get_profile_picture_filename(user_id: str) -> str | None:
    """Profilbild-Dateiname eines Users lesen (für /avatars/{userId})."""
    with db.db() as conn:
        cur = conn.execute(
            "SELECT profile_picture FROM users WHERE id = ?",
            (user_id,),
        )
        row = cur.fetchone()
        return row["profile_picture"] if row is not None else None


def require_admin(user_id: str) -> None:
    """Wirft 403 wenn user_id kein Admin ist."""
    with db.db() as conn:
        cur = conn.execute(
            "SELECT is_admin FROM users WHERE id = ?", (user_id,)
        )
        row = cur.fetchone()
        if row is None or not row["is_admin"]:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="admin-rechte erforderlich",
            )


def admin_list_users() -> list[dict]:
    """Alle User auflisten (Admin only)."""
    with db.db() as conn:
        cur = conn.execute(
            "SELECT id, username, display_name, is_admin, created_at "
            "FROM users WHERE is_legacy = 0 ORDER BY created_at ASC"
        )
        return [
            {
                "user_id": r["id"],
                "username": r["username"],
                "display_name": r["display_name"] or r["username"],
                "is_admin": bool(r["is_admin"]),
                "created_at": r["created_at"],
            }
            for r in cur.fetchall()
        ]


def admin_create_user(
    username: str, password: str, display_name: str = "", is_admin: bool = False
) -> str:
    """User durch Admin anlegen (ohne open_registration-Prüfung).
    Liefert user_id."""
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
    with db.db() as conn:
        cur = conn.execute(
            "SELECT id FROM users WHERE username = ?", (username,)
        )
        if cur.fetchone() is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="username bereits vergeben",
            )
        conn.execute(
            "INSERT INTO users (id, username, password_hash, created_at, is_legacy, is_admin, display_name) "
            "VALUES (?, ?, ?, ?, 0, ?, ?)",
            (user_id, username, _hash_password(password), now,
             1 if is_admin else 0, display_name or username),
        )
    return user_id


def admin_delete_user(user_id: str) -> None:
    """User löschen (Admin only). darf sich nicht selbst löschen."""
    with db.db() as conn:
        cur = conn.execute(
            "SELECT is_admin, is_legacy FROM users WHERE id = ?", (user_id,)
        )
        row = cur.fetchone()
        if row is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="user nicht gefunden",
            )
        if row["is_legacy"]:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="legacy-user kann nicht gelöscht werden",
            )
        conn.execute("DELETE FROM users WHERE id = ?", (user_id,))


def update_settings(open_registration: bool | None = None) -> dict:
    """App-Settings aktualisieren (Admin only)."""
    with db.db() as conn:
        if open_registration is not None:
            db.set_setting(conn, "open_registration", "1" if open_registration else "0")
        return {
            "open_registration": db.get_setting(conn, "open_registration", "0") == "1",
        }
