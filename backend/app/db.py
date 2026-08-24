"""SQLite-Zugriffsschicht (rohes sqlite3, kein ORM).

Das Schema spiegelt die Android-Room-Entities exakt wider:
  - todos, habits, habit_logs, habit_history
  - Spalten-Namen und -Typen passen zu den Kotlin data classes.
  - Timestamps sind Millis (Long) wie auf Android (System.currentTimeMillis()).

Warum kein SQLAlchemy: das Schema ist klein und stabil, rohes sqlite3 ist
übersichtlicher und hat weniger Magie. Pydantic-Modelle (models.py) übernehmen
die Serialisierung/Validierung.
"""
from __future__ import annotations

import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

# Module-global: ein Connection-Objekt, das alle Threads teilen.
# sqlite3 connections sind thread-safe, wenn check_same_thread=False und wir
# selbst serialisieren (Lock). Für eine Ein-Nutzer-App mit wenigen Requests
# reicht ein Mutex locker.
_lock = threading.Lock()
_conn: sqlite3.Connection | None = None
_db_path: str = "data/todonotes.db"


def init_db(db_path: str) -> None:
    """Initialisiert die DB-Verbindung und legt Tabellen an, falls fehlen."""
    global _conn, _db_path
    _db_path = db_path
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    _conn = sqlite3.connect(db_path, check_same_thread=False)
    _conn.row_factory = sqlite3.Row  # dict-like access: row["col"]
    _conn.execute("PRAGMA journal_mode=WAL;")  # bessere Concurrent-Read-Perf
    _conn.execute("PRAGMA foreign_keys=ON;")
    _create_schema(_conn)
    _conn.commit()


@contextmanager
def db() -> Iterator[sqlite3.Connection]:
    """Serialisierte DB-Session (Lock hält concurrent writes konsistent)."""
    if _conn is None:
        raise RuntimeError("db.init_db() wurde nicht aufgerufen")
    with _lock:
        try:
            yield _conn
            _conn.commit()
        except Exception:
            _conn.rollback()
            raise


def _create_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS users (
            id            TEXT PRIMARY KEY,
            username      TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at    INTEGER NOT NULL,
            is_legacy     INTEGER NOT NULL DEFAULT 0,
            is_admin      INTEGER NOT NULL DEFAULT 0,
            display_name  TEXT,
            profile_picture TEXT
        );

        CREATE TABLE IF NOT EXISTS tokens (
            id           TEXT PRIMARY KEY,
            user_id      TEXT NOT NULL,
            token        TEXT NOT NULL UNIQUE,
            created_at   INTEGER NOT NULL,
            last_used_at INTEGER,
            FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_tokens_token ON tokens(token);
        CREATE INDEX IF NOT EXISTS idx_tokens_user_id ON tokens(user_id);

        -- App-Einstellungen (Key-Value, z.B. open_registration).
        CREATE TABLE IF NOT EXISTS app_settings (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS todos (
            id           TEXT PRIMARY KEY,
            userId       TEXT,
            title        TEXT NOT NULL,
            notes        TEXT NOT NULL,
            dueAt        INTEGER,
            recurrence   TEXT,
            completedAt  INTEGER,
            createdAt    INTEGER NOT NULL,
            updatedAt    INTEGER NOT NULL,
            deletedAt    INTEGER,
            logToHistory INTEGER NOT NULL,
            notificationStyle INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_todos_updatedAt ON todos(updatedAt);

        CREATE TABLE IF NOT EXISTS habits (
            id                    TEXT PRIMARY KEY,
            userId                TEXT,
            title                 TEXT NOT NULL,
            notes                 TEXT NOT NULL,
            cadenceType           TEXT NOT NULL,
            interval              INTEGER NOT NULL,
            resetWeekday          INTEGER,
            resetAnchorDay        INTEGER,
            resetAnchorMonth      INTEGER,
            goalCount             INTEGER NOT NULL,
            startDate             INTEGER NOT NULL,
            logToHistory          INTEGER NOT NULL,
            lastLoggedPeriodStart INTEGER,
            type                  TEXT NOT NULL DEFAULT 'HABIT',
            currentRating         INTEGER,
            position              INTEGER NOT NULL DEFAULT 0,
            createdAt             INTEGER NOT NULL,
            updatedAt             INTEGER NOT NULL,
            deletedAt             INTEGER
        );
        CREATE INDEX IF NOT EXISTS idx_habits_updatedAt ON habits(updatedAt);

        CREATE TABLE IF NOT EXISTS habit_logs (
            id        TEXT PRIMARY KEY,
            userId    TEXT,
            habitId   TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            note      TEXT NOT NULL,
            FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_habit_logs_habitId ON habit_logs(habitId);
        CREATE INDEX IF NOT EXISTS idx_habit_logs_timestamp ON habit_logs(timestamp);

        CREATE TABLE IF NOT EXISTS habit_history (
            id          TEXT PRIMARY KEY,
            userId      TEXT,
            habitId     TEXT NOT NULL,
            title       TEXT NOT NULL,
            cadenceLabel TEXT NOT NULL,
            periodStart INTEGER NOT NULL,
            count       INTEGER NOT NULL,
            goal        INTEGER NOT NULL,
            newRating   INTEGER,
            loggedAt    INTEGER NOT NULL,
            FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_habit_history_habitId ON habit_history(habitId);
        CREATE INDEX IF NOT EXISTS idx_habit_history_loggedAt ON habit_history(loggedAt);

        -- Block F1: Notiz-App
        CREATE TABLE IF NOT EXISTS folders (
            id        TEXT PRIMARY KEY,
            userId    TEXT,
            parentId  TEXT,
            name      TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER
        );
        CREATE INDEX IF NOT EXISTS idx_folders_parentId ON folders(parentId);
        CREATE INDEX IF NOT EXISTS idx_folders_updatedAt ON folders(updatedAt);

        CREATE TABLE IF NOT EXISTS notes (
            id        TEXT PRIMARY KEY,
            userId    TEXT,
            folderId  TEXT,
            type      TEXT NOT NULL DEFAULT 'NOTE',  -- 'NOTE' | 'CHAT' (Block H)
            title     TEXT NOT NULL,
            bodyJson  TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER,
            position  INTEGER NOT NULL DEFAULT 0
            -- kein FK auf folderId: Sync darf Notiz vor Ordner annehmen
        );
        CREATE INDEX IF NOT EXISTS idx_notes_folderId ON notes(folderId);
        CREATE INDEX IF NOT EXISTS idx_notes_updatedAt ON notes(updatedAt);

        -- Block H: Chat-Nachrichten (WhatsApp-Style Tracking-Notizen).
        -- Eigene Tabelle, damit jede Nachricht ihr unveränderliches createdAt
        -- behält (bleibt beim Bearbeiten gleich — nur text/updatedAt ändern).
        CREATE TABLE IF NOT EXISTS chat_messages (
            id                TEXT PRIMARY KEY,
            userId            TEXT,
            noteId            TEXT NOT NULL,
            text              TEXT NOT NULL,
            createdAt         INTEGER NOT NULL,
            updatedAt         INTEGER NOT NULL,
            deletedAt         INTEGER,
            position          INTEGER NOT NULL,
            quotedMessageId   TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_chat_messages_noteId ON chat_messages(noteId);
        CREATE INDEX IF NOT EXISTS idx_chat_messages_updatedAt ON chat_messages(updatedAt);
        """
    )
    _migrate_schema(conn)


def _migrate_schema(conn: sqlite3.Connection) -> None:
    """Idempotente Spalten-Migrationen für bereits bestehende Tabellen.

    CREATE TABLE IF NOT EXISTS überspringt Tabellen, die schon da sind — neu
    hinzugefügte Spalten müssen per ALTER TABLE nachgezogen werden. Wir prüfen
    per PRAGMA table_info, ob die Spalte fehlt, und legen sie dann an.
    Sicher bei mehreren Starts (idempotent)."""
    def _has_column(table: str, col: str) -> bool:
        return any(r[1] == col for r in conn.execute(f"PRAGMA table_info({table})"))

    def _add_column(table: str, col: str, ddl: str) -> None:
        if not _has_column(table, col):
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {ddl}")

    # M8: todos.notificationStyle (0=Vollbild, 1=nur Notification, 2=stumm).
    _add_column("todos", "notificationStyle", "notificationStyle INTEGER NOT NULL DEFAULT 0")

    # Block H: notes.type (NOTE/CHAT, default NOTE für bestehende Notizen).
    _add_column("notes", "type", "type TEXT NOT NULL DEFAULT 'NOTE'")

    # M7d: notes.position — Reihenfolge der Notizen im Ordner (Sync der Reorder).
    _add_column("notes", "position", "position INTEGER NOT NULL DEFAULT 0")

    # Zufriedenheits-Tracker: habits.type (HABIT/SATISFACTION) + currentRating + position
    _add_column("habits", "type", "type TEXT NOT NULL DEFAULT 'HABIT'")
    _add_column("habits", "currentRating", "currentRating INTEGER")
    _add_column("habits", "position", "position INTEGER NOT NULL DEFAULT 0")
    _add_column("habit_history", "newRating", "newRating INTEGER")

    # Block H-Quote: quotedMessageId für Zitate in Chat-Nachrichten.
    _add_column("chat_messages", "quotedMessageId", "quotedMessageId TEXT")

    # M1: Multi-User-Auth — userId-Spalte auf alle Daten-Tabellen.
    # Bestehende Zeilen (NULL nach ALTER) werden unten auf den Legacy-User gesetzt.
    for table in (
        "todos", "habits", "habit_logs", "habit_history",
        "folders", "notes", "chat_messages",
    ):
        _add_column(table, "userId", "userId TEXT")
        conn.execute(
            f"CREATE INDEX IF NOT EXISTS idx_{table}_userId ON {table}(userId)"
        )

    # M7d-3: User-Profil erweitern (is_admin, display_name, profile_picture).
    _add_column("users", "is_admin", "is_admin INTEGER NOT NULL DEFAULT 0")
    _add_column("users", "display_name", "display_name TEXT")
    _add_column("users", "profile_picture", "profile_picture TEXT")

    # M7d-3: Default-Settings.
    conn.execute(
        "INSERT OR IGNORE INTO app_settings (key, value) VALUES ('open_registration', '0')"
    )

    # Legacy-User anlegen (einmalig, idempotent).
    # Alle bestehenden Daten ohne userId werden ihm zugeordnet.
    # Er ist nur via SYNC_TOKEN (Static-Secret) erreichbar — kein Login möglich.
    _ensure_legacy_user(conn)

LEGACY_USER_ID = "legacy-user"
"""User-ID des Legacy-Users (für Static-Token-Auth)."""


def get_setting(conn: sqlite3.Connection, key: str, default: str = "") -> str:
    """Liest einen app_settings-Wert."""
    cur = conn.execute(
        "SELECT value FROM app_settings WHERE key = ?", (key,)
    )
    row = cur.fetchone()
    return row["value"] if row is not None else default


def set_setting(conn: sqlite3.Connection, key: str, value: str) -> None:
    """Schreibt einen app_settings-Wert (upsert)."""
    conn.execute(
        "INSERT INTO app_settings (key, value) VALUES (?, ?) "
        "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        (key, value),
    )

def _ensure_legacy_user(conn: sqlite3.Connection) -> None:
    """Legt den Legacy-User an falls fehlt, ordnet alle Daten ohne userId ihm zu.
    Idempotent — sicher bei jedem Startup."""
    now = int(time.time() * 1000)
    cur = conn.execute(
        "SELECT id FROM users WHERE id = ?", (LEGACY_USER_ID,)
    )
    if cur.fetchone() is None:
        conn.execute(
            "INSERT INTO users (id, username, password_hash, created_at, is_legacy) "
            "VALUES (?, ?, ?, ?, 1)",
            (LEGACY_USER_ID, "legacy", "!LEGACY_NO_LOGIN", now),
        )
    # Alle Zeilen ohne userId auf Legacy-User setzen (idempotent).
    for table in (
        "todos", "habits", "habit_logs", "habit_history",
        "folders", "notes", "chat_messages",
    ):
        conn.execute(
            f"UPDATE {table} SET userId = ? WHERE userId IS NULL",
            (LEGACY_USER_ID,),
        )
