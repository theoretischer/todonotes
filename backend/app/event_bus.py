"""Einfacher In-Memory Event-Bus für SSE-Push-Benachrichtigungen.

Wenn ein Client synced (POST /sync), wird ein Event an alle anderen
verbundenen Clients desselben Users gepusht → diese pullen sofort.

Wichtig: der syncende Client wird NICHT benachrichtigt (client_id-
Exclude), sonst entsteht eine Ping-Pong-Endlosschleife
(sync → SSE → sync → SSE → ...).

asyncio-basiert: pro Subscriber eine Queue. publish() fan-out an alle
ausser dem Sender. Single-Process (uvicorn) — für Multi-Worker müsste
man Redis o.ä. nutzen.
"""
from __future__ import annotations

import asyncio
from typing import Dict, Set, Tuple


class _EventBus:
    """Prozess-globaler Event-Bus. Gruppenbasiert (pro user_id)."""

    def __init__(self) -> None:
        # user_id -> set of (queue, client_id) tuples
        self._subscribers: Dict[str, Set[Tuple[asyncio.Queue, str]]] = {}

    def subscribe(self, user_id: str, client_id: str) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue()
        if user_id not in self._subscribers:
            self._subscribers[user_id] = set()
        self._subscribers[user_id].add((q, client_id))
        return q

    def unsubscribe(self, user_id: str, q: asyncio.Queue) -> None:
        if user_id in self._subscribers:
            # Entferne alle Einträge mit dieser Queue (unabhängig von client_id).
            self._subscribers[user_id] = {
                (queue, cid) for (queue, cid) in self._subscribers[user_id]
                if queue is not q
            }
            if not self._subscribers[user_id]:
                del self._subscribers[user_id]

    def publish(
        self, user_id: str, event: str = "sync", except_client_id: str = ""
    ) -> None:
        """Event an alle Subscriber eines Users senden (async, non-blocking).

        except_client_id: der Client, der das Event ausgelöst hat (z.B. der
        syncende Client). Wird NICHT benachrichtigt → kein Ping-Pong.
        """
        subs = self._subscribers.get(user_id, set())
        for q, cid in subs:
            if cid == except_client_id:
                continue  # Sender nicht benachrichtigen.
            try:
                q.put_nowait(event)
            except asyncio.QueueFull:
                pass


# Singleton
event_bus = _EventBus()
