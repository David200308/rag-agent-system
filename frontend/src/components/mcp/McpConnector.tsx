"use client";

import { useEffect, useState } from "react";
import { CheckCircle2, Link2Off, AlertCircle } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/Button";

// ── types ─────────────────────────────────────────────────────────────────────

type Status = Record<string, boolean>;

interface Connector {
  id: string;
  provider: string;   // "google" | "figma"
  name: string;
  description: string;
  icon: React.ReactNode;
}

// ── connector definitions ─────────────────────────────────────────────────────

const CONNECTORS: Connector[] = [
  {
    id: "google-docs",
    provider: "google",
    name: "Google Docs",
    description: "Import and sync documents from your Google Drive.",
    icon: (
      <svg viewBox="0 0 48 48" className="h-8 w-8" fill="none">
        <path d="M30 4H12C9.8 4 8 5.8 8 8v32c0 2.2 1.8 4 4 4h24c2.2 0 4-1.8 4-4V18L30 4z" fill="#4285F4" />
        <path d="M30 4v14h14L30 4z" fill="#A8C7FA" />
        <path d="M14 28h20v2H14zm0-6h20v2H14zm0 12h14v2H14z" fill="#fff" />
      </svg>
    ),
  },
  {
    id: "google-sheets",
    provider: "google",
    name: "Google Sheets",
    description: "Connect spreadsheets to query and analyse tabular data.",
    icon: (
      <svg viewBox="0 0 48 48" className="h-8 w-8" fill="none">
        <path d="M30 4H12C9.8 4 8 5.8 8 8v32c0 2.2 1.8 4 4 4h24c2.2 0 4-1.8 4-4V18L30 4z" fill="#34A853" />
        <path d="M30 4v14h14L30 4z" fill="#81C995" />
        <path d="M14 22h20v14H14z" fill="#fff" opacity=".9" />
        <path d="M14 22h20v2H14zm0 4h20v2H14zm0 4h20v2H14zm0 4h20v2H14z" fill="#34A853" opacity=".3" />
        <path d="M22 22v14M30 22v14" stroke="#34A853" strokeWidth="1" opacity=".3" />
      </svg>
    ),
  },
  {
    id: "google-slides",
    provider: "google",
    name: "Google Slides",
    description: "Access presentations and extract slide content for search.",
    icon: (
      <svg viewBox="0 0 48 48" className="h-8 w-8" fill="none">
        <path d="M30 4H12C9.8 4 8 5.8 8 8v32c0 2.2 1.8 4 4 4h24c2.2 0 4-1.8 4-4V18L30 4z" fill="#FBBC04" />
        <path d="M30 4v14h14L30 4z" fill="#FFE082" />
        <rect x="13" y="22" width="22" height="14" rx="1.5" fill="#fff" opacity=".9" />
        <rect x="16" y="25" width="16" height="8" rx="1" fill="#FBBC04" opacity=".4" />
      </svg>
    ),
  },
  {
    id: "figma",
    provider: "figma",
    name: "Figma",
    description: "Pull design files, comments, and component docs into context.",
    icon: (
      <svg viewBox="0 0 38 57" className="h-8 w-8" fill="none">
        <path d="M19 28.5A9.5 9.5 0 1 1 28.5 19 9.5 9.5 0 0 1 19 28.5z" fill="#1ABCFE" />
        <path d="M9.5 57A9.5 9.5 0 0 1 9.5 38H19v9.5A9.5 9.5 0 0 1 9.5 57z" fill="#0ACF83" />
        <path d="M19 0H9.5a9.5 9.5 0 0 0 0 19H19V0z" fill="#FF7262" />
        <path d="M28.5 0H19v19h9.5a9.5 9.5 0 0 0 0-19z" fill="#F24E1E" />
        <path d="M28.5 19H19v19h9.5a9.5 9.5 0 0 0 0-19z" fill="#A259FF" />
      </svg>
    ),
  },
];

// ── connector card ────────────────────────────────────────────────────────────

function ConnectorCard({
  connector,
  connected,
  onConnect,
  onDisconnect,
  loading,
}: {
  connector: Connector;
  connected: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
  loading: boolean;
}) {
  return (
    <div
      className={cn(
        "flex items-start gap-4 rounded-xl border p-4 transition-colors",
        connected
          ? "border-emerald-200 bg-emerald-50/40 dark:border-emerald-800 dark:bg-emerald-950/30"
          : "border-[--color-border] bg-[--color-surface-raised]",
      )}
    >
      <div className="shrink-0 mt-0.5">{connector.icon}</div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-semibold">{connector.name}</p>
          {connected && (
            <span className="flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-medium text-emerald-700 dark:bg-emerald-900/60 dark:text-emerald-300">
              <CheckCircle2 className="h-3 w-3" />
              Connected
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs text-[--color-muted]">{connector.description}</p>
      </div>

      <div className="shrink-0">
        {connected ? (
          <button
            onClick={onDisconnect}
            disabled={loading}
            className="flex items-center gap-1.5 rounded-lg border border-[--color-border] px-3 py-1.5 text-xs text-[--color-muted] transition-colors hover:border-red-300 hover:text-red-500 disabled:opacity-50"
          >
            <Link2Off className="h-3.5 w-3.5" />
            Disconnect
          </button>
        ) : (
          <Button
            size="sm"
            loading={loading}
            onClick={onConnect}
            className="text-xs px-3 py-1.5"
          >
            Connect
          </Button>
        )}
      </div>
    </div>
  );
}

// ── main component ────────────────────────────────────────────────────────────

export function McpConnector() {
  const searchParams  = useSearchParams();
  const [status, setStatus]       = useState<Status>({});
  const [loadingMap, setLoadingMap] = useState<Record<string, boolean>>({});
  const [banner, setBanner]       = useState<{ type: "success" | "error"; msg: string } | null>(null);

  // Read ?connected= or ?error= params set by the OAuth callback redirect
  useEffect(() => {
    const connected = searchParams.get("connected");
    const error     = searchParams.get("error");
    if (connected) setBanner({ type: "success", msg: `${connected} connected successfully.` });
    if (error)     setBanner({ type: "error",   msg: `OAuth failed: ${error.replace(/_/g, " ")}.` });
    if (connected || error) {
      // Clean URL without re-render loop
      window.history.replaceState(null, "", "/mcp");
    }
  }, [searchParams]);

  const fetchStatus = async () => {
    try {
      const res = await fetch("/api/connectors/status", { cache: "no-store" });
      if (res.ok) setStatus(await res.json() as Status);
    } catch { /* ignore */ }
  };

  useEffect(() => { fetchStatus(); }, []);

  const setLoading = (provider: string, val: boolean) =>
    setLoadingMap((prev) => ({ ...prev, [provider]: val }));

  const handleConnect = (provider: string) => {
    // Full-page redirect → OAuth provider → callback → /mcp?connected=provider
    window.location.href = `/api/connectors/${provider}/connect`;
  };

  const handleDisconnect = async (provider: string) => {
    setLoading(provider, true);
    try {
      await fetch(`/api/connectors/${provider}/disconnect`, { method: "DELETE" });
      await fetchStatus();
    } finally {
      setLoading(provider, false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold">Connectors</h1>
        <p className="mt-1 text-sm text-[--color-muted]">
          Connect external services to bring their content into the agent&apos;s knowledge base.
        </p>
      </div>

      {banner && (
        <div
          className={cn(
            "flex items-center gap-2 rounded-lg border px-3 py-2.5 text-sm",
            banner.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300"
              : "border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/40 dark:text-red-300",
          )}
        >
          {banner.type === "success"
            ? <CheckCircle2 className="h-4 w-4 shrink-0" />
            : <AlertCircle  className="h-4 w-4 shrink-0" />}
          <span className="capitalize">{banner.msg}</span>
        </div>
      )}

      <div className="space-y-3">
        {CONNECTORS.map((c) => (
          <ConnectorCard
            key={c.id}
            connector={c}
            connected={!!status[c.provider]}
            loading={!!loadingMap[c.provider]}
            onConnect={() => handleConnect(c.provider)}
            onDisconnect={() => handleDisconnect(c.provider)}
          />
        ))}
      </div>
    </div>
  );
}
