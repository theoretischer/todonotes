"""Einfacher In-Memory Event-Bus für SSE-Push-Benachrichtigungen.

Wenn ein Client synced (POST /sync), wird ein Event an alle anderen
verbundenen Clients desselben Users gepusht → diese pullen sofort.

asyncio-basiert: pro Subscriber eine Queue. publish() fan-out an alle.
Single-Process (uvicorn) — für Multi-Worker müsste man Redis o.ä. nutzen.
"""
from __future__ import annotations

import asyncio
from typing import Set


class _EventBus:
    """Prozess-globaler Event-Bus. Gruppenbasiert (pro user_id)."""

    def __init__(self) -> None:
        # user_id -> set of queues
        self._subscribers: dict[str, Set[asyncio.Queue]] = {}

    def subscribe(self, user_id: str) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue()
        if user_id not in self._subscribers:
            self._subscribers[user_id] = set()
        self._subscribers[user_id].add(q)
        return q

    def unsubscribe(self, user_id: str, q: asyncio.Queue) -> None:
        if user_id in self._subscribers:
            self._subscribers[user_id].discard(q)
            if not self._subscribers[user_id]:
                del self._subscribers[user_id]

    def publish(self, user_id: str, event: str = "sync") -> None:
        """Event an alle Subscriber eines Users senden (async, non-blocking)."""
        subs = self._subscribers.get(user_id, set())
        for q in subs:
            # put_nowait: non-blocking, drop if full (we don't block sync).
            try:
                q.put_nowait(event)
            except asyncio.QueueFull:
                pass


# Singleton
event_bus = _EventBus()
