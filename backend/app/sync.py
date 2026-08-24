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
) -> tuple[ChangesBundle, int]:
    """Wendet Client-Änderungen an und liefert **alle** Server-Zeilen des Users.

    Bombensichere Sync-Strategie (Full Down-Sync):
      - UP:   Client pusht nur geänderte Zeilen (getSince). Server wendet
              via LWW an (existing.updatedAt >= incoming → skip).
      - DOWN: Server liefert **alle** Zeilen des Users (nicht nur seit
              last_synced_at). Client upsertet alles. Das heilt lokal
              verlorene Zeilen selbst — kein „Daten weg nach Reload".
      - newSyncedAt = server_now (KEIN +1 mehr). Das +1 machte Rows
        (updatedAt=server_now) unsichtbar für Clients mit
        lastSyncedAt >= server_now → Datenverlust bei lokalem DB-Verlust.

    user_id: alle Änderungen werden auf diesen User eingeschränkt.
    server_now: Server-Zeit (ms) — wird als updatedAt für alle angewendeten
    Änderungen gesetzt (Source of Truth, eliminiert Clock-Skew).

    Liefert (server_changes, applied_count). applied_count = Anzahl
    tatsächlich geänderter Rows (für SSE-Notify: nur bei >0).

    Wipe-Schutz: wenn wipe_epoch > last_synced_at, hat der Client seit dem
    letzten Server-Wipe nicht mehr gesynced → seine lokalen Daten sind
    veraltet. Push wird IGNORIERT. Der Client muss beim Erhalt der wipeEpoch
    seine lokale DB leeren und neu pullen (full down-sync liefert dann
    die aktuellen Server-Daten).
    """
    with db.db() as conn:
        wipe_epoch = int(db.get_setting(conn, "wipe_epoch", "0"))
    if wipe_epoch > last_synced_at:
        applied = 0
    else:
        applied = _apply_changes(changes, user_id, server_now)
    return _collect_all_server_changes(user_id), applied


# ----- Client -> Server (Apply) -----

def _apply_changes(changes: ChangesBundle, user_id: str, server_now: int) -> int:
    """Client-Änderungen einspielen. Server setzt updatedAt = server_now
    (Server-Zeit als Source of Truth, eliminiert Clock-Skew).

    Liefert die Anzahl tatsächlich geänderter/neu eingefügter Rows
    (für SSE-Notify: nur bei >0 andere Clients benachrichtigen).
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
    applied = 0
    with db.db() as conn:
        for table, dto_cls, _pk, change_field, _change_col in _SYNC_TABLES:
            rows = bundle_map[table]
            for dto in rows:
                if _upsert_row(conn, table, dto_cls, dto, change_field, user_id, server_now):
                    applied += 1
    return applied


def _upsert_row(
    conn, table: str, dto_cls, dto, change_field: str, user_id: str, server_now: int
) -> bool:
    """Upsert mit LWW: nur ueberschreiben wenn incoming >= existing.

    Liefert True wenn die Row geändert/eingefügt wurde, False bei Skip.

    Server-Zeit wird NACH dem LWW-Check als updatedAt gesetzt
    (monoton, vergleichbar ueber Clients). Der LWW-Check vergleicht
    die INCOMING client-Zeit gegen die existing SERVER-Zeit:
    - incoming >= existing → apply (client hat neuere Daten)
    - incoming < existing → skip (client hat stale Daten)

    Anti-Resurrektion: wenn der Server ein Item als geloescht hat
    (deletedAt != null) und der Client eine nicht-geloeschte Version
    schickt (deletedAt = null), wird das Update SKIPED — egal wie neu
    die client-Zeit ist. Einmal geloescht = geloescht.
    """
    data = dto.model_dump()
    new_change = data[change_field]  # client's updatedAt (vor LWW-Check)
    pk = data["id"]
    has_deleted_at = "deletedAt" in data
    select_cols = f"{change_field}, userId"
    if has_deleted_at:
        select_cols += ", deletedAt"
    cur = conn.execute(
        f"SELECT {select_cols} FROM {table} WHERE id = ?", (pk,)
    )
    existing = cur.fetchone()
    if existing is not None:
        if change_field in ("timestamp", "loggedAt"):
            return False  # append-only
        if existing["userId"] is not None and existing["userId"] != user_id:
            return False  # fremde Daten
        if has_deleted_at and existing["deletedAt"] is not None and data.get("deletedAt") is None:
            return False  # Anti-Resurrektion
        if existing[change_field] >= new_change:
            return False  # stale → skip
        data[change_field] = server_now
        if "userId" in data:
            del data["userId"]
        _update_row(conn, table, data)
        return True
    else:
        data["userId"] = user_id
        if change_field not in ("timestamp", "loggedAt"):
            data[change_field] = server_now
        _insert_row(conn, table, data)
        return True


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

def _collect_all_server_changes(user_id: str) -> ChangesBundle:
    """Liefert **alle** Server-Zeilen des Users (Full Down-Sync).

    Bombensicher: der Client bekommt jedes Mal die komplette Wahrheit.
    Verlorene lokale Zeilen (OPFS-Flush-Race, Flow-Cache, etc.) werden
    automatisch geheilt — kein last_synced_at-Window mehr, in dem Rows
    unsichtbar werden können.

    LWW verhindert Churn auf der UP-Seite: unveränderte Zeilen werden
    serverseitig geskippt → kein SSE-Notify → andere Clients pullen nicht.
    Das Bundle ist klein (persönliche App: < 1000 Rows, < 100KB).
    """
    out = ChangesBundle()
    with db.db() as conn:
        for table, dto_cls, _pk, _change_field, change_col in _SYNC_TABLES:
            rows = conn.execute(
                f"SELECT * FROM {table} WHERE userId = ?",
                (user_id,),
            ).fetchall()
            for row in rows:
                d = dict(row)
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
