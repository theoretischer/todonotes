"""Recurrence-Engine — Port des Kotlin RecurrenceEngine.

Nutzt python-dateutil.rrule für RFC 5545 RRULE.
next_occurrence liefert die nächste Occurrence strikt nach from_due.
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta

from dateutil.rrule import rrulestr

log = logging.getLogger("todonotes.recurrence")


def next_occurrence(rrule_str: str, from_due: int | None, now: int) -> int | None:
    """Nächste Occurrence strikt nach from_due (oder now falls from_due None).

    @param rrule_str: RFC 5545 RRULE, z.B. "FREQ=DAILY;BYDAY=MO,TU"
    @param from_due: Originaler Startzeitpunkt (millis). Iteration startet hier.
    @param now: Fallback falls from_due None.
    @return nächste Occurrence in millis, oder None wenn RRULE abgelaufen.
    """
    try:
        start_millis = from_due if from_due is not None else now
        after_millis = from_due if from_due is not None else now
        start_dt = datetime.fromtimestamp(start_millis / 1000)

        rule = rrulestr(rrule_str, dtstart=start_dt)
        # Erste Occurrence strikt nach after_millis
        after_dt = datetime.fromtimestamp(after_millis / 1000)
        for occurrence in rule:
            if occurrence.timestamp() * 1000 > after_millis:
                return int(occurrence.timestamp() * 1000)
            # Safety: nicht endlos iterieren (COUNT/UNTIL begrenzt zwar,
            # aber bei FREQ ohne LIMIT wär's infinite — wir brechen nach
            # 10000 ab, das reicht für jede echte RRULE).
            if occurrence > after_dt + timedelta(days=365 * 100):
                break
        log.info("RRULE erschöpft oder keine Occurrence nach fromDue: %s", rrule_str)
        return None
    except Exception as e:
        log.error("Fehler beim Parsen/Berechnen der RRULE '%s': %s", rrule_str, e)
        return None
