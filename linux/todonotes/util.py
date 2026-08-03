"""Hilfsfunktionen: Zeit, UUIDs, Datumsformatate (German)."""
from __future__ import annotations

import time
import uuid
from datetime import datetime, timezone


def now_millis() -> int:
    """Aktuelle Zeit in Millisekunden (wie Android System.currentTimeMillis)."""
    return int(time.time() * 1000)


def new_id() -> str:
    """UUID v4 als String (wie Android UUID.randomUUID)."""
    return str(uuid.uuid4())


def format_time(millis: int) -> str:
    """HH:mm Format (wie Android HH:mm)."""
    dt = datetime.fromtimestamp(millis / 1000)
    return dt.strftime("%H:%M")


def format_date(millis: int) -> str:
    """dd.MM.yyyy Format."""
    dt = datetime.fromtimestamp(millis / 1000)
    return dt.strftime("%d.%m.%Y")


def format_date_short(millis: int) -> str:
    """dd.MM. Format (für Trennzeilen im Chat)."""
    dt = datetime.fromtimestamp(millis / 1000)
    return dt.strftime("%d.%m.")


def format_date_time(millis: int) -> str:
    """dd.MM.yyyy HH:mm."""
    dt = datetime.fromtimestamp(millis / 1000)
    return dt.strftime("%d.%m.%Y %H:%M")


def is_same_day(a: int, b: int) -> bool:
    """True wenn beide Timestamps am selben Tag liegen."""
    da = datetime.fromtimestamp(a / 1000)
    db_ = datetime.fromtimestamp(b / 1000)
    return da.date() == db_.date()
