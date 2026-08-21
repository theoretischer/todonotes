"""Habit Engine — Port des Kotlin HabitEngine.

Berechnet den Start der aktuellen Periode (Reset-Punkt) für ein Habit.
Reset-Logik identisch zu Android:
  - DAY / NDAYS: Periode ist [interval] Tage, beginnt am startDate.
  - WEEK: reset am resetWeekday, alle [interval] Wochen.
  - MONTH: reset am resetAnchorDay, alle [interval] Monate.
  - YEAR: reset am resetAnchorDay des resetAnchorMonth, alle [interval] Jahre.

Python datetime statt java.util.Calendar.
Wochentag-Mapping: Android Calendar.MONDAY=2 ... SUNDAY=1.
Python: Monday=0 ... Sunday=6.
"""
from __future__ import annotations

from datetime import datetime, timedelta

# Android Calendar weekday → Python weekday (0=Monday)
# Calendar.SUNDAY=1, MONDAY=2, TUESDAY=3, WEDNESDAY=4, THURSDAY=5, FRIDAY=6, SATURDAY=7
_ANDROID_TO_PY_WEEKDAY = {
    1: 6,  # Sunday → 6
    2: 0,  # Monday → 0
    3: 1,  # Tuesday → 1
    4: 2,  # Wednesday → 2
    5: 3,  # Thursday → 3
    6: 4,  # Friday → 4
    7: 5,  # Saturday → 5
}


def _to_date(millis: int) -> datetime:
    return datetime.fromtimestamp(millis / 1000)


def _to_millis(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


def _midnight(dt: datetime) -> datetime:
    return dt.replace(hour=0, minute=0, second=0, microsecond=0)


def current_period_start(habit: dict, now: int) -> int:
    """Start der aktuellen Periode (Millis), ab dem gezählt wird. Liegt <= now."""
    cadence = habit["cadenceType"]
    now_dt = _to_date(now)

    if cadence in ("DAY", "NDAYS"):
        computed = _anchored_day_start(habit, now_dt)
    elif cadence == "WEEK":
        computed = _week_start(habit, now_dt)
    elif cadence == "MONTH":
        computed = _month_start(habit, now_dt)
    elif cadence == "YEAR":
        computed = _year_start(habit, now_dt)
    else:
        computed = _to_date(habit["startDate"])

    # Vor dem Startdatum → Start ist startDate
    start_millis = habit["startDate"]
    result = _to_millis(computed)
    return result if result >= start_millis else start_millis


def next_period_start(habit: dict, now: int) -> int:
    """Start der nächsten Periode nach der aktuellen."""
    current = _to_date(current_period_start(habit, now))
    interval = habit["interval"]
    cadence = habit["cadenceType"]

    if cadence in ("DAY", "NDAYS"):
        nxt = current + timedelta(days=interval)
    elif cadence == "WEEK":
        nxt = current + timedelta(weeks=interval)
    elif cadence == "MONTH":
        nxt = _add_months(current, interval)
    elif cadence == "YEAR":
        nxt = _add_months(current, interval * 12)
    else:
        nxt = current + timedelta(days=interval)

    return _to_millis(nxt)


def progress(
    habit: dict, now: int, count_since_fn
) -> tuple[int, int, int]:
    """(count, goal, period_start)."""
    start = current_period_start(habit, now)
    count = count_since_fn(habit["id"], start)
    return (count, habit["goalCount"], start)


# ── DAY / NDAYS ─────────────────────────────────────────────────

def _anchored_day_start(habit: dict, now_dt: datetime) -> datetime:
    start_dt = _midnight(_to_date(habit["startDate"]))
    if now_dt <= start_dt:
        return start_dt
    day = timedelta(days=1)
    elapsed_days = (now_dt - start_dt).days
    periods_passed = elapsed_days // habit["interval"]
    period_start_days = periods_passed * habit["interval"]
    return start_dt + timedelta(days=period_start_days)


# ── WEEK ───────────────────────────────────────────────────────

def _week_start(habit: dict, now_dt: datetime) -> datetime:
    reset_wd_android = habit.get("resetWeekday") or 2  # Monday default
    reset_wd = _ANDROID_TO_PY_WEEKDAY[reset_wd_android]

    cal = _midnight(now_dt)
    # Zurück zum letzten resetWd
    diff = (cal.weekday() - reset_wd) % 7
    cal = cal - timedelta(days=diff)

    if habit["interval"] > 1:
        start_cal = _midnight(_to_date(habit["startDate"]))
        d0 = (start_cal.weekday() - reset_wd) % 7
        start_cal = start_cal - timedelta(days=d0)
        weeks_between = (cal - start_cal).days // 7
        periods_passed = weeks_between // habit["interval"]
        period_start_weeks = periods_passed * habit["interval"]
        cal = start_cal + timedelta(weeks=period_start_weeks)

    return cal


# ── MONTH ──────────────────────────────────────────────────────

def _month_start(habit: dict, now_dt: datetime) -> datetime:
    anchor_day = habit.get("resetAnchorDay") or 1
    cal = now_dt.replace(day=anchor_day)
    cal = _midnight(cal)
    if cal > now_dt:
        cal = _add_months(cal, -1)

    if habit["interval"] > 1:
        start_cal = _to_date(habit["startDate"]).replace(day=anchor_day)
        start_cal = _midnight(start_cal)
        if start_cal.timestamp() * 1000 > habit["startDate"]:
            start_cal = _add_months(start_cal, -1)
        months_between = _months_between(start_cal, cal)
        periods_passed = months_between // habit["interval"]
        period_start_months = periods_passed * habit["interval"]
        cal = _add_months(start_cal, period_start_months)

    return cal


# ── YEAR ───────────────────────────────────────────────────────

def _year_start(habit: dict, now_dt: datetime) -> datetime:
    anchor_day = habit.get("resetAnchorDay") or 1
    anchor_month = (habit.get("resetAnchorMonth") or 1) - 1  # 0-based
    cal = now_dt.replace(month=anchor_month + 1, day=anchor_day)
    cal = _midnight(cal)
    if cal > now_dt:
        cal = _add_months(cal, -12)

    if habit["interval"] > 1:
        start_cal = _to_date(habit["startDate"]).replace(
            month=anchor_month + 1, day=anchor_day
        )
        start_cal = _midnight(start_cal)
        if start_cal.timestamp() * 1000 > habit["startDate"]:
            start_cal = _add_months(start_cal, -12)
        years_between = cal.year - start_cal.year
        periods_passed = years_between // habit["interval"]
        period_start_years = periods_passed * habit["interval"]
        cal = _add_months(start_cal, period_start_years * 12)

    return cal


# ── Helpers ─────────────────────────────────────────────────────

def _add_months(dt: datetime, months: int) -> datetime:
    """Addiert Monate (kann Overflow am Monatsende geben → Tag anpassen)."""
    total = dt.month - 1 + months
    year = dt.year + total // 12
    month = total % 12 + 1
    # Tag ggf. kürzen (z.B. 31. Jan + 1 Monat → 28. Feb)
    import calendar
    max_day = calendar.monthrange(year, month)[1]
    day = min(dt.day, max_day)
    return dt.replace(year=year, month=month, day=day)


def _months_between(a: datetime, b: datetime) -> int:
    return (b.year - a.year) * 12 + (b.month - a.month)


def cadence_label(habit: dict) -> str:
    """Kurzes Label wie '2x pro Woche' / '1x alle 3 Tage'."""
    cadence = habit["cadenceType"]
    per = {
        "DAY": "Tag",
        "WEEK": "Woche",
        "MONTH": "Monat",
        "YEAR": "Jahr",
        "NDAYS": f"{habit['interval']} Tage",
    }.get(cadence, cadence)
    if cadence == "NDAYS":
        return f"{habit['goalCount']}x alle {per}"
    return f"{habit['goalCount']}x pro {per}"
