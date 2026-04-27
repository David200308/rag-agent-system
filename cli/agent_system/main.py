"""agent-system CLI entry point."""

from __future__ import annotations

import typer
from rich.console import Console

from agent_system import config
from agent_system.commands import auth, chat, workflow
from agent_system.commands import verify

app = typer.Typer(
    name="agent-system",
    help="CLI for the RAG Agent System.",
    no_args_is_help=True,
    rich_markup_mode="rich",
)

console = Console()

# ── Sub-command groups ────────────────────────────────────────────────────────

app.add_typer(auth.app,     name="auth",     help="[cyan]login / logout / status[/cyan]")
app.add_typer(verify.app,   name="verify",   help="[cyan]verify otp <code>[/cyan]")
app.add_typer(chat.app,     name="chat",     help="[cyan]list · id · ask · delete[/cyan]")
app.add_typer(workflow.app, name="workflow",  help="[cyan]list · id · run · runs · logs[/cyan]")

# ── Top-level shortcuts (mirrors the examples in the spec) ────────────────────

@app.command("login")
def login(email: str = typer.Argument(..., help="Your whitelisted email address")):
    """Shortcut for [bold]agent-system auth login <email>[/bold]."""
    auth.login(email)


@app.command("logout")
def logout():
    """Shortcut for [bold]agent-system auth logout[/bold]."""
    auth.logout()


@app.command("status")
def status():
    """Show current authentication status."""
    auth.status()


@app.command("config")
def show_config(
    base_url: str = typer.Option(None, "--base-url", help="Set the backend URL"),
):
    """Show or update CLI configuration."""
    if base_url:
        config.set("base_url", base_url)
        console.print(f"[green]base_url set to[/green] [bold]{base_url}[/bold]")
    else:
        console.print(f"base_url : [cyan]{config.base_url()}[/cyan]")
        em = config.email()
        tok = config.token()
        console.print(f"email    : [cyan]{em or '(not set)'}[/cyan]")
        console.print(f"token    : [cyan]{'(set)' if tok else '(not set)'}[/cyan]")


if __name__ == "__main__":
    app()
