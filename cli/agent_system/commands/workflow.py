"""workflow commands: list, id, run, logs."""

from __future__ import annotations

import typer
from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.text import Text

from agent_system import api

app = typer.Typer(help="Workflow commands.")
console = Console()


def _ts(val) -> str:
    if not val:
        return ""
    try:
        from datetime import datetime, timezone
        if isinstance(val, (int, float)):
            dt = datetime.fromtimestamp(val / 1000, tz=timezone.utc)
        else:
            dt = datetime.fromisoformat(str(val).replace("Z", "+00:00"))
        return dt.strftime("%Y-%m-%d %H:%M")
    except Exception:
        return str(val)


@app.command("list")
def list_workflows():
    """List your workflows."""
    workflows = api.get("/api/v1/workflow")
    if not workflows:
        console.print("[dim]No workflows found.[/dim]")
        return

    table = Table(title="Workflows", show_lines=True)
    table.add_column("ID", style="cyan", no_wrap=True)
    table.add_column("Name", style="bold white")
    table.add_column("Pattern", style="yellow")
    table.add_column("Agents", justify="right")
    table.add_column("Created", style="dim")

    for w in workflows:
        agents = w.get("agents") or []
        table.add_row(
            w.get("id", ""),
            w.get("name", ""),
            w.get("agentPattern", ""),
            str(len(agents)),
            _ts(w.get("createdAt")),
        )

    console.print(table)


@app.command("id")
def get_workflow(workflow_id: str = typer.Argument(..., help="Workflow ID")):
    """Show details of a workflow including its agents."""
    w = api.get(f"/api/v1/workflow/{workflow_id}")

    console.print(Panel(
        f"[bold]{w.get('name', '')}[/bold]\n"
        f"[dim]{w.get('description', '')}[/dim]\n\n"
        f"Pattern: [yellow]{w.get('agentPattern', '')}[/yellow]   "
        f"Exec mode: [cyan]{w.get('teamExecMode', 'N/A')}[/cyan]",
        title=f"Workflow [cyan]{workflow_id}[/cyan]",
        border_style="cyan",
    ))

    agents = api.get(f"/api/v1/workflow/{workflow_id}/agents")
    if not agents:
        console.print("[dim]No agents configured.[/dim]")
        return

    table = Table(title="Agents", show_lines=True)
    table.add_column("#", style="dim", width=4)
    table.add_column("Name", style="bold white")
    table.add_column("Role", style="yellow")
    table.add_column("Tools", style="cyan")
    table.add_column("System prompt", style="dim")

    for a in sorted(agents, key=lambda x: x.get("orderIndex", 0)):
        tools = ", ".join(a.get("tools") or []) or "—"
        prompt = (a.get("systemPrompt") or "")[:50]
        if len(a.get("systemPrompt") or "") > 50:
            prompt += "…"
        table.add_row(
            str(a.get("orderIndex", "")),
            a.get("name", ""),
            a.get("role", ""),
            tools,
            prompt,
        )

    console.print(table)


@app.command("run")
def run_workflow(
    workflow_id: str = typer.Argument(..., help="Workflow ID"),
    input: str = typer.Argument(..., help="User input / task description"),
    notify: bool = typer.Option(False, "--notify", "-n", help="Email notification on completion"),
):
    """Trigger a workflow run."""
    with console.status("[cyan]Starting run…[/cyan]"):
        result = api.post(
            f"/api/v1/workflow/{workflow_id}/runs",
            {"userInput": input, "emailNotify": notify},
        )
    run_id = result.get("runId", "")
    console.print(Panel(
        f"Run started: [bold cyan]{run_id}[/bold cyan]\n\n"
        f"Check logs with:\n  [bold]agent-system workflow logs {run_id}[/bold]",
        title="[green]Run started[/green]",
        border_style="green",
    ))


@app.command("runs")
def list_runs(workflow_id: str = typer.Argument(..., help="Workflow ID")):
    """List recent runs for a workflow."""
    runs = api.get(f"/api/v1/workflow/{workflow_id}/runs")
    if not runs:
        console.print("[dim]No runs found.[/dim]")
        return

    table = Table(title=f"Runs for workflow {workflow_id}", show_lines=True)
    table.add_column("Run ID", style="cyan", no_wrap=True)
    table.add_column("Status", style="bold")
    table.add_column("Started", style="dim")
    table.add_column("Finished", style="dim")

    status_colors = {"COMPLETED": "green", "FAILED": "red", "RUNNING": "yellow", "PENDING": "dim"}
    for r in runs:
        status = r.get("status", "UNKNOWN")
        color  = status_colors.get(status, "white")
        table.add_row(
            r.get("id", ""),
            Text(status, style=color),
            _ts(r.get("startedAt")),
            _ts(r.get("finishedAt")),
        )

    console.print(table)


@app.command("logs")
def get_logs(run_id: str = typer.Argument(..., help="Run ID")):
    """Show logs for a completed workflow run."""
    logs = api.get(f"/api/v1/workflow/runs/{run_id}/logs")
    if not logs:
        console.print("[dim]No logs found for this run.[/dim]")
        return

    level_styles = {"ERROR": "bold red", "WARN": "yellow", "INFO": "cyan", "DEBUG": "dim"}
    for entry in logs:
        level   = entry.get("level", "INFO")
        agent   = entry.get("agentName", "")
        message = entry.get("message", "")
        ts      = _ts(entry.get("createdAt"))
        style   = level_styles.get(level, "white")
        console.print(f"[dim]{ts}[/dim]  [{style}]{level:<5}[/{style}]  [bold]{agent}[/bold]  {message}")
