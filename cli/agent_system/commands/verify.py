"""verify command: verify otp <code>."""

from __future__ import annotations

import typer
from rich.console import Console
from rich.panel import Panel

from agent_system import api, config

app = typer.Typer(help="Verify commands.")
console = Console()


@app.command()
def otp(code: str = typer.Argument(..., help="6-digit OTP from your email")):
    """Verify the OTP and save the session token locally."""
    em = config.email()
    if not em:
        console.print("[red]No pending login.[/red] Run [bold]agent-system login <email>[/bold] first.")
        raise typer.Exit(1)

    result = api.post("/api/v1/auth/verify-otp", {"email": em, "code": code})
    tok = result.get("token")
    if not tok:
        console.print("[red]Verification failed.[/red] Check your code and try again.")
        raise typer.Exit(1)

    config.set("token", tok)
    console.print(Panel(
        f"[green]Authenticated[/green] as [bold]{em}[/bold]\n\n"
        "Your session token has been saved. You can now use all commands.",
        title="Verified",
        border_style="green",
    ))
