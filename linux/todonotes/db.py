"""SQLite-Zugriffsschicht — Spiegelt das Backend-Schema (db.py) exakt.

Identische Tabellen/Spalten wie backend/app/db.py und die Android-Room-Entities.
Timestamps sind Millis (int) wie auf Android (time.time() * 1000).

Die lokale DB liegt unter ~/.local/share/todonotes/todonotes.db.
"""
from __future__ import annotations

import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

_lock = threading.Lock()
_conn: sqlite3.Connection | None = None
_db_path: str = ""


def init_db(db_path: str) -> None:
    """Initialisiert die DB-Verbindung und legt Tabellen an, falls fehlen."""
    global _conn, _db_path
    _db_path = db_path
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    _conn = sqlite3.connect(db_path, check_same_thread=False)
    _conn.row_factory = sqlite3.Row
    _conn.execute("PRAGMA journal_mode=WAL;")
    _conn.execute("PRAGMA foreign_keys=ON;")
    _create_schema(_conn)
    _conn.commit()


def db_path() -> str:
    return _db_path


@contextmanager
def db() -> Iterator[sqlite3.Connection]:
    """Serialisierte DB-Session."""
    if _conn is None:
        raise RuntimeError("db.init_db() wurde nicht aufgerufen")
    with _lock:
        try:
            yield _conn
            _conn.commit()
        except Exception:
            _conn.rollback()
            raise


_SCHEMA = """
CREATE TABLE IF NOT EXISTS todos (
    id           TEXT PRIMARY KEY,
    title        TEXT NOT NULL,
    notes        TEXT NOT NULL,
    dueAt        INTEGER,
    recurrence   TEXT,
    completedAt  INTEGER,
    createdAt    INTEGER NOT NULL,
    updatedAt    INTEGER NOT NULL,
    deletedAt    INTEGER,
    logToHistory INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_todos_updatedAt ON todos(updatedAt);

CREATE TABLE IF NOT EXISTS habits (
    id                    TEXT PRIMARY KEY,
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
    createdAt             INTEGER NOT NULL,
    updatedAt             INTEGER NOT NULL,
    deletedAt             INTEGER
);
CREATE INDEX IF NOT EXISTS idx_habits_updatedAt ON habits(updatedAt);

CREATE TABLE IF NOT EXISTS habit_logs (
    id        TEXT PRIMARY KEY,
    habitId   TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    note      TEXT NOT NULL,
    FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_habit_logs_habitId ON habit_logs(habitId);
CREATE INDEX IF NOT EXISTS idx_habit_logs_timestamp ON habit_logs(timestamp);

CREATE TABLE IF NOT EXISTS habit_history (
    id           TEXT PRIMARY KEY,
    habitId      TEXT NOT NULL,
    title        TEXT NOT NULL,
    cadenceLabel TEXT NOT NULL,
    periodStart  INTEGER NOT NULL,
    count        INTEGER NOT NULL,
    goal         INTEGER NOT NULL,
    loggedAt     INTEGER NOT NULL,
    FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_habit_history_habitId ON habit_history(habitId);
CREATE INDEX IF NOT EXISTS idx_habit_history_loggedAt ON habit_history(loggedAt);

CREATE TABLE IF NOT EXISTS folders (
    id        TEXT PRIMARY KEY,
    parentId  TEXT,
    name      TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    deletedAt INTEGER,
    position  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_folders_parentId ON folders(parentId);
CREATE INDEX IF NOT EXISTS idx_folders_updatedAt ON folders(updatedAt);

CREATE TABLE IF NOT EXISTS notes (
    id        TEXT PRIMARY KEY,
    folderId  TEXT,
    type      TEXT NOT NULL DEFAULT 'NOTE',
    title     TEXT NOT NULL,
    bodyJson  TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    deletedAt INTEGER,
    position  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_notes_folderId ON notes(folderId);
CREATE INDEX IF NOT EXISTS idx_notes_updatedAt ON notes(updatedAt);

CREATE TABLE IF NOT EXISTS chat_messages (
    id                TEXT PRIMARY KEY,
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


def _create_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(_SCHEMA)


# ── Generic helpers ──────────────────────────────────────────────

def upsert(table: str, row: dict[str, Any]) -> None:
    """INSERT OR REPLACE — für Sync (Server gewinnt) und lokale Writes."""
    cols = list(row.keys())
    placeholders = ",".join("?" for _ in cols)
    col_list = ",".join(cols)
    vals = [_bool_to_int(v) for v in row.values()]
    with db() as conn:
        conn.execute(
            f"INSERT OR REPLACE INTO {table} ({col_list}) VALUES ({placeholders})",
            vals,
        )


def upsert_many(table: str, rows: list[dict[str, Any]]) -> None:
    if not rows:
        return
    cols = list(rows[0].keys())
    placeholders = ",".join("?" for _ in cols)
    col_list = ",".join(cols)
    tuples = [tuple(_bool_to_int(v) for v in r.values()) for r in rows]
    with db() as conn:
        conn.executemany(
            f"INSERT OR REPLACE INTO {table} ({col_list}) VALUES ({placeholders})",
            tuples,
        )


def fetch_all(table: str) -> list[dict[str, Any]]:
    """Alle Zeilen einer Tabelle (für Sync: collect local changes)."""
    with db() as conn:
        rows = conn.execute(f"SELECT * FROM {table}").fetchall()
    return [_row_to_dict(r) for r in rows]


def fetch_one(table: str, row_id: str) -> dict[str, Any] | None:
    with db() as conn:
        row = conn.execute(
            f"SELECT * FROM {table} WHERE id = ?", (row_id,)
        ).fetchone()
    return _row_to_dict(row) if row else None


def _row_to_dict(row: sqlite3.Row) -> dict[str, Any]:
    d = dict(row)
    # 0/1 -> bool für Booleans
    for bcol in ("logToHistory",):
        if bcol in d and d[bcol] is not None:
            d[bcol] = bool(d[bcol])
    return d


def _bool_to_int(v: Any) -> Any:
    if isinstance(v, bool):
        return 1 if v else 0
    return v
