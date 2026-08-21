"""Persistierte Sync-Einstellungen + App-Config.

Liegt als JSON unter ~/.config/todonotes/config.json.
Enthält: server_url, token, last_synced_at, client_id.
"""
from __future__ import annotations

import json
import time
from pathlib import Path
from threading import Lock

_lock = Lock()
_state: dict = {}
_config_path: str = ""


def _default_config() -> dict:
    return {
        "server_url": "",
        "token": "",
        "last_synced_at": 0,
        "client_id": f"linux-{int(time.time() * 1000):x}",
        "last_sync_result": "",
        "last_sync_at": 0,
    }


def init_config(config_dir: str) -> None:
    """Initialisiert die Config aus config.json (oder legt sie an)."""
    global _config_path
    Path(config_dir).mkdir(parents=True, exist_ok=True)
    _config_path = str(Path(config_dir) / "config.json")
    _load()


def _load() -> None:
    global _state
    with _lock:
        p = Path(_config_path)
        if p.exists():
            try:
                _state = json.loads(p.read_text("utf-8"))
            except (json.JSONDecodeError, OSError):
                _state = _default_config()
        else:
            _state = _default_config()
        # Defaults ergänzen (falls neue Keys dazukommen)
        defaults = _default_config()
        for k, v in defaults.items():
            _state.setdefault(k, v)
        _save_unlocked()


def _save_unlocked() -> None:
    Path(_config_path).write_text(
        json.dumps(_state, indent=2, ensure_ascii=False), "utf-8"
    )


def _save() -> None:
    with _lock:
        _save_unlocked()


# ── Accessors ───────────────────────────────────────────────────

def get(key: str, default=None):
    with _lock:
        return _state.get(key, default)


def set(key: str, value) -> None:
    with _lock:
        _state[key] = value
        _save_unlocked()


# ── Sync-specific convenience ──────────────────────────────────

def server_url() -> str:
    return get("server_url", "")


def set_server_url(url: str) -> None:
    u = url.strip().rstrip("/")
    if u and not u.startswith("http://") and not u.startswith("https://"):
        u = "https://" + u
    set("server_url", u)


def token() -> str:
    return get("token", "")


def set_token(t: str) -> None:
    set("token", t.strip())


def last_synced_at() -> int:
    return get("last_synced_at", 0)


def set_last_synced_at(v: int) -> None:
    set("last_synced_at", v)


def client_id() -> str:
    return get("client_id", "linux-unknown")


def is_configured() -> bool:
    return bool(server_url()) and bool(token())


def last_sync_result() -> str:
    return get("last_sync_result", "")


def set_last_sync_result(s: str) -> None:
    set("last_sync_result", s)


def last_sync_at() -> int:
    return get("last_sync_at", 0)


def set_last_sync_at(v: int) -> None:
    set("last_sync_at", v)
