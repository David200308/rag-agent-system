"""chat commands: list, id, ask."""

from __future__ import annotations

import typer
from rich.console import Console
from rich.markdown import Markdown
from rich.panel import Panel
from rich.table import Table

from agent_system import api

app = typer.Typer(help="Chat / conversation commands.")
console = Console()


def _short(text: str, width: int = 60) -> str:
    text = (text or "").replace("\n", " ").strip()
    return text[:width] + "…" if len(text) > width else text


@app.command("list")
def list_chats():
    """List your conversations."""
    convos = api.get("/api/v1/agent/conversations")
    if not convos:
        console.print("[dim]No conversations found.[/dim]")
        return

    table = Table(title="Conversations", show_lines=True)
    table.add_column("ID", style="cyan", no_wrap=True)
    table.add_column("Title / first message", style="white")
    table.add_column("Created", style="dim")

    for c in convos:
        table.add_row(
            c.get("id", ""),
            _short(c.get("title") or c.get("id", ""), 55),
            _ts(c.get("createdAt")),
        )

    console.print(table)


@app.command("id")
def get_chat(conversation_id: str = typer.Argument(..., help="Conversation ID")):
    """Display the full message history for a conversation."""
    messages = api.get(f"/api/v1/agent/conversations/{conversation_id}")
    if not messages:
        console.print(f"[yellow]No messages found for conversation [bold]{conversation_id}[/bold].[/yellow]")
        return

    for msg in messages:
        role    = msg.get("role", "unknown")
        content = msg.get("content", "")
        ts      = _ts(msg.get("createdAt"))
        if role == "user":
            console.print(Panel(content, title=f"[bold blue]You[/bold blue]  [dim]{ts}[/dim]", border_style="blue"))
        else:
            console.print(Panel(Markdown(content), title=f"[bold green]Assistant[/bold green]  [dim]{ts}[/dim]", border_style="green"))


@app.command("ask")
def ask(
    message: str = typer.Argument(..., help="Your question"),
    conversation_id: str = typer.Option(None, "--conversation", "-c", help="Continue an existing conversation"),
):
    """Send a message to the RAG agent and print the reply."""
    body: dict = {"query": message}
    if conversation_id:
        body["conversationId"] = conversation_id

    with console.status("[cyan]Thinking…[/cyan]"):
        result = api.post("/api/v1/agent/query", body)

    answer  = result.get("answer", "")
    cid     = result.get("metadata", {}).get("conversationId", "")
    sources = result.get("sources", [])
    route   = result.get("routeDecision", {}).get("route", "")

    console.print()
    console.print(Panel(Markdown(answer), title="[bold green]Assistant[/bold green]", border_style="green"))

    if sources:
        console.print(f"[dim]Sources ({len(sources)}):[/dim]", *[f"[dim]• {s.get('source', '')}[/dim]" for s in sources[:5]], sep="\n  ")

    if cid:
        console.print(f"[dim]conversation: {cid}  route: {route}[/dim]")


@app.command("delete")
def delete_chat(conversation_id: str = typer.Argument(..., help="Conversation ID to delete")):
    """Delete a conversation and all its messages."""
    confirm = typer.confirm(f"Delete conversation {conversation_id}?")
    if not confirm:
        raise typer.Abort()
    api.delete(f"/api/v1/agent/conversations/{conversation_id}")
    console.print(f"[green]Deleted[/green] conversation [bold]{conversation_id}[/bold].")


def _ts(val) -> str:
    if not val:
        return ""
    # val may be an ISO string or epoch millis
    try:
        from datetime import datetime, timezone
        if isinstance(val, (int, float)):
            dt = datetime.fromtimestamp(val / 1000, tz=timezone.utc)
        else:
            dt = datetime.fromisoformat(str(val).replace("Z", "+00:00"))
        return dt.strftime("%Y-%m-%d %H:%M")
    except Exception:
        return str(val)
