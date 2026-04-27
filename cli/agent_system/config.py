"""Persistent CLI config stored at ~/.config/agent-system/config.json."""

from __future__ import annotations

import json
from pathlib import Path

_CONFIG_DIR = Path.home() / ".config" / "agent-system"
_CONFIG_FILE = _CONFIG_DIR / "config.json"

_DEFAULTS: dict = {
    "base_url": "http://localhost:8081",
    "token": None,
    "email": None,
}


def _load() -> dict:
    if _CONFIG_FILE.exists():
        try:
            return {**_DEFAULTS, **json.loads(_CONFIG_FILE.read_text())}
        except (json.JSONDecodeError, OSError):
            pass
    return dict(_DEFAULTS)


def _save(data: dict) -> None:
    _CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    _CONFIG_FILE.write_text(json.dumps(data, indent=2))


def get(key: str):
    return _load().get(key)


def set(key: str, value) -> None:
    data = _load()
    data[key] = value
    _save(data)


def clear_auth() -> None:
    data = _load()
    data["token"] = None
    data["email"] = None
    _save(data)


def base_url() -> str:
    return _load().get("base_url") or _DEFAULTS["base_url"]


def token() -> str | None:
    return _load().get("token")


def email() -> str | None:
    return _load().get("email")
