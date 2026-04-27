"""Thin httpx wrapper that injects the stored JWT and base URL."""

from __future__ import annotations

import sys
from typing import Any

import httpx

from agent_system import config


def _client() -> httpx.Client:
    headers = {"Content-Type": "application/json"}
    tok = config.token()
    if tok:
        headers["Authorization"] = f"Bearer {tok}"
    return httpx.Client(base_url=config.base_url(), headers=headers, timeout=30)


def _handle(resp: httpx.Response) -> dict | list:
    if resp.status_code == 401:
        from rich.console import Console
        Console().print("[red]Not authenticated.[/red] Run [bold]agent-system login <email>[/bold] first.")
        sys.exit(1)
    try:
        resp.raise_for_status()
    except httpx.HTTPStatusError as exc:
        from rich.console import Console
        Console().print(f"[red]HTTP {exc.response.status_code}:[/red] {exc.response.text}")
        sys.exit(1)
    if resp.content:
        return resp.json()
    return {}


def post(path: str, body: dict) -> Any:
    with _client() as c:
        return _handle(c.post(path, json=body))


def get(path: str, params: dict | None = None) -> Any:
    with _client() as c:
        return _handle(c.get(path, params=params))


def delete(path: str) -> Any:
    with _client() as c:
        return _handle(c.delete(path))
