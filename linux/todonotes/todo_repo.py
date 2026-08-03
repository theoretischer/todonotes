"""Todo Repository — Port des Kotlin TodoRepository.

CRUD für Todos. Recurrence wird über python-dateutil berechnet.
"""
from __future__ import annotations

from typing import Any

from . import db, util
from .recurrence import next_occurrence


def _row(row: dict[str, Any]) -> dict[str, Any]:
    return row


def fetch_open_todos() -> list[dict[str, Any]]:
    """Offene Todos (completedAt IS NULL AND deletedAt IS NULL).
    Zeitgesteuerte oben, zeitlose unten — nach dueAt sortiert."""
    with db.db() as conn:
        rows = conn.execute(
            """SELECT * FROM todos
               WHERE completedAt IS NULL AND deletedAt IS NULL
               ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
                        dueAt ASC, createdAt ASC"""
        ).fetchall()
    return [dict(r) for r in rows]


def fetch_completed_todos() -> list[dict[str, Any]]:
    """Erledigte Todos (Verlauf): completedAt IS NOT NULL."""
    with db.db() as conn:
        rows = conn.execute(
            """SELECT * FROM todos
               WHERE completedAt IS NOT NULL AND deletedAt IS NULL
               ORDER BY completedAt DESC"""
        ).fetchall()
    return [dict(r) for r in rows]


def get_by_id(todo_id: str) -> dict[str, Any] | None:
    return db.fetch_one("todos", todo_id)


def create_todo(
    title: str,
    notes: str = "",
    due_at: int | None = None,
    recurrence: str | None = None,
    log_to_history: bool = True,
) -> dict[str, Any]:
    now = util.now_millis()
    todo = {
        "id": util.new_id(),
        "title": title.strip(),
        "notes": notes.strip(),
        "dueAt": due_at,
        "recurrence": recurrence,
        "completedAt": None,
        "createdAt": now,
        "updatedAt": now,
        "deletedAt": None,
        "logToHistory": log_to_history,
    }
    db.upsert("todos", todo)
    return todo


def complete_todo(todo_id: str) -> None:
    """Todo abhaken: completedAt setzen, ggf. nächste Occurrence anlegen."""
    now = util.now_millis()
    todo = get_by_id(todo_id)
    if not todo:
        return

    if todo["logToHistory"]:
        # completedAt setzen → landet im Verlauf
        _update_fields(todo_id, completedAt=now, updatedAt=now)
    else:
        # Soft-delete → verschwindet, nicht im Verlauf
        _update_fields(todo_id, deletedAt=now, updatedAt=now)

    # Wiederkehrende: nächste Occurrence
    rrule = todo.get("recurrence")
    if rrule:
        next_due = next_occurrence(rrule, todo.get("dueAt"), now)
        if next_due is not None:
            create_todo(
                title=todo["title"],
                notes=todo["notes"],
                due_at=next_due,
                recurrence=rrule,
                log_to_history=todo["logToHistory"],
            )


def reopen_todo(todo_id: str) -> None:
    """Todo wieder öffnen (Verlauf → offene Liste)."""
    now = util.now_millis()
    todo = get_by_id(todo_id)
    if not todo:
        return
    _update_fields(todo_id, completedAt=None, updatedAt=now)


def soft_delete(todo_id: str) -> None:
    now = util.now_millis()
    _update_fields(todo_id, deletedAt=now, updatedAt=now)


def update_todo_from_form(
    todo_id: str,
    title: str,
    notes: str,
    due_at: int | None,
    recurrence: str | None,
    log_to_history: bool,
) -> None:
    existing = get_by_id(todo_id)
    if not existing:
        return
    _update_fields(
        todo_id,
        title=title.strip(),
        notes=notes.strip(),
        dueAt=due_at,
        recurrence=recurrence,
        logToHistory=log_to_history,
        updatedAt=util.now_millis(),
    )


def _update_fields(todo_id: str, **fields) -> None:
    if not fields:
        return
    sets = ",".join(f"{k} = ?" for k in fields)
    vals = [db._bool_to_int(v) for v in fields.values()]
    vals.append(todo_id)
    with db.db() as conn:
        conn.execute(f"UPDATE todos SET {sets} WHERE id = ?", vals)
