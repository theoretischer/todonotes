"""Sync-Engine: nimmt Client-Änderungen entgegen und liefert Server-Änderungen.

Last-Write-Wins über updated_at:
  - Pro Zeile: client.upsert wenn client.updatedAt >= server.updatedAt
    (>= statt >, damit ein initialer Push von 0-Werten klappt).
  - Append-only-Tabellen (habit_logs, habit_history) haben keinen
    Konflikt-Begriff: sie werden per INSERT OR IGNORE eingefügt (id ist PK).
    Gelöscht werden sie nie direkt (nur via CASCADE, wenn das Habit stirbt).

Datenvolumen-Schutz: der Server liefert nur Zeilen, deren
  updatedAt (bzw. loggedAt/timestamp) > last_synced_at ist.
"""
from __future__ import annotations

from . import db
from .models import (
    ChangesBundle,
    ChatMessageDTO,
    FolderDTO,
    HabitDTO,
    HabitHistoryEntryDTO,
    HabitLogDTO,
    NoteDTO,
    TodoDTO,
)


# (tablename, dto_class, pk_field, change_field, change_col)
# change_field/col = Feld, das den "Stand" einer Zeile markiert und
# für den "was hat sich seit last_synced_at geändert"-Filter benutzt wird.
_SYNC_TABLES = [
    # name,        dto,                pk,      change_field,         change_col
    ("todos",         TodoDTO,               "id", "updatedAt",         "updatedAt"),
    ("habits",        HabitDTO,              "id", "updatedAt",         "updatedAt"),
    ("habit_logs",    HabitLogDTO,           "id", "timestamp",         "timestamp"),
    ("habit_history", HabitHistoryEntryDTO,  "id", "loggedAt",          "loggedAt"),
    ("folders",       FolderDTO,             "id", "updatedAt",         "updatedAt"),
    ("notes",         NoteDTO,               "id", "updatedAt",         "updatedAt"),
    ("chat_messages", ChatMessageDTO,        "id", "updatedAt",         "updatedAt"),
]


def sync(
    last_synced_at: int,
    changes: ChangesBundle,
    user_id: str,
    server_now: int,
) -> ChangesBundle:
    """Wendet Client-Änderungen an und liefert Server-Änderungen seit
    last_synced_at. Liefert nie None — leere Bundle wenn nichts da.

    user_id: alle Änderungen werden auf diesen User eingeschränkt.
    server_now: Server-Zeit (ms) — wird als updatedAt für alle angewendeten
    Änderungen gesetzt (Source of Truth, eliminiert Clock-Skew).
    """
    _apply_changes(changes, user_id, server_now)
    return _collect_server_changes(last_synced_at, user_id)


# ----- Client -> Server (Apply) -----

def _apply_changes(changes: ChangesBundle, user_id: str, server_now: int) -> None:
    """Client-Änderungen einspielen. Server setzt updatedAt = server_now
    (Server-Zeit als Source of Truth, eliminiert Clock-Skew).

    user_id: alle eingefügten/aktualisierten Zeilen bekommen diese userId.
    Bestehende Zeilen eines anderen Users werden nicht überschrieben
    (PK-Kollision → UPDATE nur wenn userId passt, sonst ignorieren).
    """
    bundle_map = {
        "todos":         changes.todos,
        "habits":        changes.habits,
        "habit_logs":    changes.habit_logs,
        "habit_history": changes.habit_history,
        "folders":       changes.folders,
        "notes":         changes.notes,
        "chat_messages": changes.chat_messages,
    }
    with db.db() as conn:
        for table, dto_cls, _pk, change_field, _change_col in _SYNC_TABLES:
            rows = bundle_map[table]
            for dto in rows:
                _upsert_row(conn, table, dto_cls, dto, change_field, user_id, server_now)


def _upsert_row(
    conn, table: str, dto_cls, dto, change_field: str, user_id: str, server_now: int
) -> None:
    """Upsert: Client-Änderung anwenden, updatedAt = server_now setzen.

    Kein LWW-Check mehr (Client-Uhren können skewed sein). Der Server
    als Source of Truth setzt updatedAt = server_now → eliminiert
    Clock-Skew-Probleme zwischen Geräten.

    user_id: neue Zeilen bekommen diese userId. Bestehende Zeilen werden nur
    aktualisiert, wenn sie demselben user_id gehören (Sicherheit: fremde Daten
    werden nicht überschrieben).
    """
    data = dto.model_dump()
    # Server-Zeit als Source of Truth (nicht Client-Zeit → kein Clock-Skew).
    data[change_field] = server_now
    pk = data["id"]
    # Bestehendes updated_at lesen (falls Zeile existiert).
    cur = conn.execute(
        f"SELECT {change_field}, userId FROM {table} WHERE id = ?", (pk,)
    )
    existing = cur.fetchone()
    if existing is not None:
        # Append-only (logs/history): INSERT OR IGNORE (id-Kollision = bereits da)
        if change_field in ("timestamp", "loggedAt"):
            return
        # Fremde Daten? Nicht überschreiben (Multi-User-Sicherheit).
        if existing["userId"] is not None and existing["userId"] != user_id:
            return
        # UPDATE: Server-Zeit als updatedAt (kein LWW-Check mehr —
        # der letzte Sync gewinnt, eliminiert Clock-Skew).
        _update_row(conn, table, data)
    else:
        # Neue Zeile: userId vom authentifizierten User setzen.
        data["userId"] = user_id
        _insert_row(conn, table, data)


def _insert_row(conn, table: str, data: dict) -> None:
    cols = list(data.keys())
    placeholders = ",".join("?" for _ in cols)
    col_list = ",".join(cols)
    # Booleans -> 0/1 für SQLite.
    vals = [_bool_to_int(v) for v in data.values()]
    conn.execute(
        f"INSERT INTO {table} ({col_list}) VALUES ({placeholders})", vals
    )


def _update_row(conn, table: str, data: dict) -> None:
    cols = list(data.keys())
    set_clause = ",".join(f"{c} = ?" for c in cols)
    vals = [_bool_to_int(v) for v in data.values()]
    vals.append(data["id"])  # WHERE id = ?
    conn.execute(
        f"UPDATE {table} SET {set_clause} WHERE id = ?", vals
    )


def _bool_to_int(v):
    if isinstance(v, bool):
        return 1 if v else 0
    return v


# ----- Server -> Client (Collect) -----

def _collect_server_changes(
    last_synced_at: int, user_id: str
) -> ChangesBundle:
    """Liefert alle Server-Zeilen, die seit last_synced_at geändert wurden.

    user_id: nur Daten des authentifizierten Users zurückgeben.
    """
    out = ChangesBundle()
    with db.db() as conn:
        for table, dto_cls, _pk, _change_field, change_col in _SYNC_TABLES:
            rows = conn.execute(
                f"SELECT * FROM {table} WHERE {change_col} > ? AND userId = ?",
                (last_synced_at, user_id),
            ).fetchall()
            for row in rows:
                d = dict(row)
                # 0/1 -> bool für Booleans (nur relevant für todos/habits).
                for bcol in ("logToHistory",):
                    if bcol in d and d[bcol] is not None:
                        d[bcol] = bool(d[bcol])
                out_field = _bundle_field_for(table)
                getattr(out, out_field).append(dto_cls(**d))
    return out


def _bundle_field_for(table: str) -> str:
    return {
        "todos": "todos",
        "habits": "habits",
        "habit_logs": "habit_logs",
        "habit_history": "habit_history",
        "folders": "folders",
        "notes": "notes",
        "chat_messages": "chat_messages",
    }[table]
