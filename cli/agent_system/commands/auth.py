"""auth commands: login, verify, logout, status."""

from __future__ import annotations

import typer
from rich.console import Console
from rich.panel import Panel

from agent_system import api, config

app = typer.Typer(help="Authentication commands.")
console = Console()


@app.command()
def login(email: str = typer.Argument(..., help="Your whitelisted email address")):
    """Request a one-time password sent to your email."""
    result = api.post("/api/v1/auth/request-otp", {"email": email})
    config.set("email", email)
    console.print(Panel(
        f"[green]OTP sent[/green] to [bold]{email}[/bold]\n\n"
        "Run [bold cyan]agent-system verify otp <code>[/bold cyan] to finish logging in.",
        title="Login",
        border_style="cyan",
    ))


@app.command()
def logout():
    """Clear stored credentials."""
    config.clear_auth()
    console.print("[green]Logged out.[/green] Credentials cleared from local config.")


@app.command()
def status():
    """Show current login status."""
    tok = config.token()
    em  = config.email()
    if not tok:
        console.print("[yellow]Not logged in.[/yellow]")
        raise typer.Exit(1)
    result = api.get("/api/v1/auth/validate")
    if result.get("valid"):
        console.print(f"[green]Logged in[/green] as [bold]{result.get('email', em)}[/bold]")
    else:
        console.print("[yellow]Session expired.[/yellow] Please log in again.")
        config.clear_auth()
        raise typer.Exit(1)
