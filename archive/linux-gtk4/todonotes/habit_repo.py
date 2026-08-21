"""Habit Repository — Port des Kotlin HabitRepository.

CRUD für Habits + Log/Undo/FinishPeriod/CheckPeriodChange.
"""
from __future__ import annotations

from typing import Any

from . import db, habit_engine, util


def fetch_habits() -> list[dict[str, Any]]:
    """Alle nicht-gelöschten Habits."""
    with db.db() as conn:
        rows = conn.execute(
            "SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY createdAt ASC"
        ).fetchall()
    return [dict(r) for r in rows]


def fetch_history() -> list[dict[str, Any]]:
    with db.db() as conn:
        rows = conn.execute(
            "SELECT * FROM habit_history ORDER BY loggedAt DESC"
        ).fetchall()
    return [dict(r) for r in rows]


def get_by_id(habit_id: str) -> dict[str, Any] | None:
    return db.fetch_one("habits", habit_id)


def create_habit(habit: dict[str, Any]) -> dict[str, Any]:
    db.upsert("habits", habit)
    return habit


def update_habit(habit: dict[str, Any]) -> None:
    habit = {**habit, "updatedAt": util.now_millis()}
    db.upsert("habits", habit)


def soft_delete(habit_id: str) -> None:
    _update_fields(habit_id, deletedAt=util.now_millis(), updatedAt=util.now_millis())


def delete_history_entry(entry_id: str) -> None:
    with db.db() as conn:
        conn.execute("DELETE FROM habit_history WHERE id = ?", (entry_id,))


def log_habit(habit_id: str, now: int | None = None) -> None:
    """+1: neuen Log-Eintrag für jetzt."""
    now = now or util.now_millis()
    log_entry = {
        "id": util.new_id(),
        "habitId": habit_id,
        "timestamp": now,
        "note": "",
    }
    db.upsert("habit_logs", log_entry)


def undo_latest_log(habit_id: str, now: int | None = None) -> None:
    """Letzten Log der aktuellen Periode löschen."""
    now = now or util.now_millis()
    habit = get_by_id(habit_id)
    if not habit:
        return
    period_start = habit_engine.current_period_start(habit, now)
    with db.db() as conn:
        row = conn.execute(
            """SELECT id FROM habit_logs
               WHERE habitId = ? AND timestamp >= ?
               ORDER BY timestamp DESC LIMIT 1""",
            (habit_id, period_start),
        ).fetchone()
        if row:
            conn.execute("DELETE FROM habit_logs WHERE id = ?", (row["id"],))


def count_since(habit_id: str, since: int) -> int:
    """Anzahl Logs seit 'since' (für aktuellen Count)."""
    with db.db() as conn:
        row = conn.execute(
            "SELECT COUNT(*) as c FROM habit_logs WHERE habitId = ? AND timestamp >= ?",
            (habit_id, since),
        ).fetchone()
    return row["c"] if row else 0


def count_between(habit_id: str, start: int, end: int) -> int:
    with db.db() as conn:
        row = conn.execute(
            "SELECT COUNT(*) as c FROM habit_logs WHERE habitId = ? AND timestamp >= ? AND timestamp < ?",
            (habit_id, start, end),
        ).fetchone()
    return row["c"] if row else 0


def force_finish_current_period(habit_id: str, now: int | None = None) -> None:
    """Schließt die aktuelle Periode manuell ab."""
    now = now or util.now_millis()
    habit = get_by_id(habit_id)
    if not habit:
        return
    current_start = habit_engine.current_period_start(habit, now)
    next_start = habit_engine.next_period_start(habit, now)
    count = count_between(habit["id"], current_start, now + 1)

    if habit["logToHistory"]:
        entry = {
            "id": util.new_id(),
            "habitId": habit["id"],
            "title": habit["title"],
            "cadenceLabel": habit_engine.cadence_label(habit),
            "periodStart": current_start,
            "count": count,
            "goal": habit["goalCount"],
            "loggedAt": now,
        }
        db.upsert("habit_history", entry)

    # Logs der aktuellen Periode löschen
    with db.db() as conn:
        conn.execute(
            "DELETE FROM habit_logs WHERE habitId = ? AND timestamp >= ?",
            (habit["id"], current_start),
        )
    _update_fields(
        habit["id"],
        lastLoggedPeriodStart=next_start,
        updatedAt=now,
    )


def check_and_log_period_change(habit: dict, now: int | None = None) -> dict:
    """Erkennt Periodenwechsel und legt ggf. History-Eintrag an."""
    now = now or util.now_millis()
    current_start = habit_engine.current_period_start(habit, now)
    last_logged = habit.get("lastLoggedPeriodStart")

    if last_logged is None:
        updated = {**habit, "lastLoggedPeriodStart": current_start, "updatedAt": now}
        db.upsert("habits", updated)
        return updated

    if current_start <= last_logged:
        return habit

    # Periodenwechsel
    if habit["logToHistory"]:
        count_prev = count_between(habit["id"], last_logged, current_start)
        entry = {
            "id": util.new_id(),
            "habitId": habit["id"],
            "title": habit["title"],
            "cadenceLabel": habit_engine.cadence_label(habit),
            "periodStart": last_logged,
            "count": count_prev,
            "goal": habit["goalCount"],
            "loggedAt": now,
        }
        db.upsert("habit_history", entry)

    updated = {**habit, "lastLoggedPeriodStart": current_start, "updatedAt": now}
    db.upsert("habits", updated)
    return updated


def _update_fields(habit_id: str, **fields) -> None:
    if not fields:
        return
    sets = ",".join(f"{k} = ?" for k in fields)
    vals = [db._bool_to_int(v) for v in fields.values()]
    vals.append(habit_id)
    with db.db() as conn:
        conn.execute(f"UPDATE habits SET {sets} WHERE id = ?", vals)
