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
    HabitDTO,
    HabitHistoryEntryDTO,
    HabitLogDTO,
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
]


def sync(last_synced_at: int, changes: ChangesBundle) -> ChangesBundle:
    """ Wendet Client-Änderungen an und liefert Server-Änderungen seit
    last_synced_at. Liefert nie None — leere Bundle wenn nichts da. """
    _apply_changes(changes)
    return _collect_server_changes(last_synced_at)


# ----- Client -> Server (Apply) -----

def _apply_changes(changes: ChangesBundle) -> None:
    """ Client-Änderungen einspielen. Last-Write-Wins pro Zeile. """
    bundle_map = {
        "todos":         changes.todos,
        "habits":        changes.habits,
        "habit_logs":    changes.habit_logs,
        "habit_history": changes.habit_history,
    }
    with db.db() as conn:
        for table, dto_cls, _pk, change_field, _change_col in _SYNC_TABLES:
            rows = bundle_map[table]
            for dto in rows:
                _upsert_row(conn, table, dto_cls, dto, change_field)


def _upsert_row(
    conn, table: str, dto_cls, dto, change_field: str
) -> None:
    """Upsert mit Last-Write-Wins: nur überschreiben, wenn dto neuer ist."""
    data = dto.model_dump()
    pk = data["id"]
    new_change = data[change_field]
    # Bestehendes updated_at lesen (falls Zeile existiert).
    cur = conn.execute(
        f"SELECT {change_field} FROM {table} WHERE id = ?", (pk,)
    )
    existing = cur.fetchone()
    if existing is not None:
        # Append-only (logs/history): INSERT OR IGNORE (id-Kollision = bereits da)
        if change_field in ("timestamp", "loggedAt"):
            # Nur einfügen, wenn noch nicht vorhanden (id-PK). Echte Änderung
            # an einem Log macht keinen Sinn → ignorieren.
            return
        if existing[change_field] > new_change:
            # Server ist neuer → Client-Änderung verwerfen.
            return
        # Sonst: UPDATE (Client gewinnt).
        _update_row(conn, table, data)
    else:
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

def _collect_server_changes(last_synced_at: int) -> ChangesBundle:
    """Liefert alle Server-Zeilen, die seit last_synced_at geändert wurden."""
    out = ChangesBundle()
    with db.db() as conn:
        for table, dto_cls, _pk, _change_field, change_col in _SYNC_TABLES:
            rows = conn.execute(
                f"SELECT * FROM {table} WHERE {change_col} > ?",
                (last_synced_at,),
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
    }[table]
