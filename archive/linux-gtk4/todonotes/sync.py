"""Sync-Manager — Port des Kotlin SyncManager.

Führt einen Sync-Zyklus aus:
  1. Alle lokalen Zeilen einsammeln → ChangesBundle (dict)
  2. POST /sync mit last_synced_at + client_id
  3. Server-Änderungen lokal einspielen (INSERT OR REPLACE)
  4. last_synced_at = new_synced_at persistieren

Conflict Resolution passiert SERVER-seitig (LWW über updated_at).
Client-seitig akzeptieren wir alles vom Server.
"""
from __future__ import annotations

import logging
from typing import Any

import requests

from . import config, db

log = logging.getLogger("todonotes.sync")

# Tabellen, die im ChangesBundle gesendet/empfangen werden.
# (table_name, bundle_key, change_col)
_SYNC_TABLES = [
    ("todos", "todos", "updatedAt"),
    ("habits", "habits", "updatedAt"),
    ("habit_logs", "habit_logs", "timestamp"),
    ("habit_history", "habit_history", "loggedAt"),
    ("folders", "folders", "updatedAt"),
    ("notes", "notes", "updatedAt"),
    ("chat_messages", "chat_messages", "updatedAt"),
]


def collect_local_changes() -> dict[str, list[dict[str, Any]]]:
    """Sammelt alle lokalen Zeilen für den Sync-Upload."""
    bundle: dict[str, list[dict[str, Any]]] = {}
    for table, key, _ in _SYNC_TABLES:
        bundle[key] = db.fetch_all(table)
    return bundle


def apply_server_changes(changes: dict[str, list[dict[str, Any]]]) -> None:
    """Spielt Server-Änderungen lokal ein (INSERT OR REPLACE)."""
    for table, key, _ in _SYNC_TABLES:
        rows = changes.get(key, [])
        if rows:
            db.upsert_many(table, rows)


def sync() -> bool:
    """Führt einen Sync aus. True bei Erfolg, False bei Fehler."""
    if not config.is_configured():
        config.set_last_sync_result("Nicht konfiguriert (Server-URL/Token fehlt)")
        return False

    url = config.server_url().rstrip("/") + "/sync"
    payload = {
        "last_synced_at": config.last_synced_at(),
        "client_id": config.client_id(),
        "changes": collect_local_changes(),
    }
    headers = {
        "Authorization": f"Bearer {config.token()}",
        "Content-Type": "application/json",
    }

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        apply_server_changes(data.get("server_changes", {}))
        config.set_last_synced_at(data.get("new_synced_at", 0))
        config.set_last_sync_at(int(__import__("time").time() * 1000))
        config.set_last_sync_result("OK")
        log.info("Sync erfolgreich: newSyncedAt=%s", data.get("new_synced_at"))
        return True
    except Exception as e:
        msg = f"Fehler: {e}"
        config.set_last_sync_result(msg)
        log.error("Sync fehlgeschlagen: %s", e)
        return False


def health() -> bool:
    """Health-Check (ohne Token). True wenn Server antwortet."""
    if not config.server_url():
        return False
    url = config.server_url().rstrip("/") + "/health"
    try:
        resp = requests.get(url, timeout=10)
        return resp.status_code == 200
    except Exception as e:
        log.error("Health-Check fehlgeschlagen: %s", e)
        return False
