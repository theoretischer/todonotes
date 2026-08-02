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
            id          TEXT PRIMARY KEY,
            habitId     TEXT NOT NULL,
            title       TEXT NOT NULL,
            cadenceLabel TEXT NOT NULL,
            periodStart INTEGER NOT NULL,
            count       INTEGER NOT NULL,
            goal        INTEGER NOT NULL,
            loggedAt    INTEGER NOT NULL,
            FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_habit_history_habitId ON habit_history(habitId);
        CREATE INDEX IF NOT EXISTS idx_habit_history_loggedAt ON habit_history(loggedAt);

        -- Block F1: Notiz-App
        CREATE TABLE IF NOT EXISTS folders (
            id        TEXT PRIMARY KEY,
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
            folderId  TEXT,
            title     TEXT NOT NULL,
            bodyJson  TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            deletedAt INTEGER
            -- kein FK auf folderId: Sync darf Notiz vor Ordner annehmen
        );
        CREATE INDEX IF NOT EXISTS idx_notes_folderId ON notes(folderId);
        CREATE INDEX IF NOT EXISTS idx_notes_updatedAt ON notes(updatedAt);
        """
    )
