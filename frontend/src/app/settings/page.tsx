"use client";

import { useEffect, useState } from "react";
import { Moon, Sun, Globe, Trash2, Plus, User, Shield } from "lucide-react";
import { useTheme } from "@/hooks/useTheme";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { ResizableLayout } from "@/components/layout/ResizableLayout";
import { Sidebar } from "@/components/layout/Sidebar";
import { useChatStore } from "@/store/chatStore";
import {
  fetchWebFetchWhitelist,
  addWebFetchDomain,
  removeWebFetchDomain,
} from "@/lib/api";
import type { WebFetchWhitelistEntry } from "@/types/agent";
import { cn } from "@/lib/utils";

function SectionCard({ title, icon, children }: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-[--color-border] bg-[--color-surface] overflow-hidden">
      <div className="flex items-center gap-2 border-b border-[--color-border] px-5 py-3.5">
        <span className="text-[--color-muted]">{icon}</span>
        <h2 className="text-sm font-semibold">{title}</h2>
      </div>
      <div className="px-5 py-4">{children}</div>
    </div>
  );
}

export default function SettingsPage() {
  const { theme, toggle, mounted } = useTheme();
  const { selectConversation } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // ── Account ──────────────────────────────────────────────────────────────────
  const [email, setEmail] = useState<string | null>(null);
  const [authEnabled, setAuthEnabled] = useState(false);

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { enabled: boolean; email?: string }) => {
        setAuthEnabled(d.enabled);
        setEmail(d.email ?? null);
      })
      .catch(() => {});
  }, []);

  // ── Web-fetch whitelist ───────────────────────────────────────────────────────
  const [whitelist, setWhitelist]   = useState<WebFetchWhitelistEntry[]>([]);
  const [wlLoading, setWlLoading]   = useState(true);
  const [newDomain, setNewDomain]   = useState("");
  const [addError, setAddError]     = useState<string | null>(null);
  const [addLoading, setAddLoading] = useState(false);

  useEffect(() => {
    fetchWebFetchWhitelist()
      .then(setWhitelist)
      .finally(() => setWlLoading(false));
  }, []);

  const handleAddDomain = async () => {
    const domain = newDomain.trim();
    if (!domain) return;
    setAddLoading(true);
    setAddError(null);
    try {
      const entry = await addWebFetchDomain(domain);
      setWhitelist((prev) => [...prev, entry].sort((a, b) => a.domain.localeCompare(b.domain)));
      setNewDomain("");
    } catch {
      setAddError("Failed to add domain. It may already be whitelisted.");
    } finally {
      setAddLoading(false);
    }
  };

  const handleRemoveDomain = async (domain: string) => {
    try {
      await removeWebFetchDomain(domain);
      setWhitelist((prev) => prev.filter((e) => e.domain !== domain));
    } catch {
      /* ignore */
    }
  };

  return (
    <>
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 sm:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <ResizableLayout
        sidebar={(width, onCollapse) => (
          <Sidebar
            onSelectConversation={(id) => { selectConversation(id); window.location.href = "/"; }}
            isOpen={sidebarOpen}
            onClose={() => setSidebarOpen(false)}
            desktopWidth={width}
            onCollapse={onCollapse}
          />
        )}
      >
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-2xl px-4 py-8 space-y-6">
          <h1 className="text-xl font-semibold">Settings</h1>

          {/* ── Account ──────────────────────────────────────────────────── */}
          <SectionCard title="Account" icon={<User className="h-4 w-4" />}>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-[--color-muted] mb-0.5">Signed in as</p>
                <p className="text-sm font-medium">
                  {authEnabled
                    ? (email ?? "—")
                    : <span className="text-[--color-muted]">Auth disabled</span>}
                </p>
              </div>
              {authEnabled && (
                <span className="inline-flex items-center gap-1 rounded-full border border-green-500/30 bg-green-500/10 px-2.5 py-0.5 text-xs text-green-600 dark:text-green-400">
                  <Shield className="h-3 w-3" /> Authenticated
                </span>
              )}
            </div>
          </SectionCard>

          {/* ── Appearance ───────────────────────────────────────────────── */}
          <SectionCard title="Appearance" icon={<Sun className="h-4 w-4" />}>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium">Theme</p>
                <p className="text-xs text-[--color-muted]">
                  {mounted ? (theme === "dark" ? "Dark mode" : "Light mode") : "—"}
                </p>
              </div>
              {mounted && (
                <button
                  type="button"
                  onClick={toggle}
                  className={cn(
                    "relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none",
                    theme === "dark" ? "bg-gray-900 dark:bg-gray-100" : "bg-gray-200",
                  )}
                  aria-label="Toggle theme"
                >
                  <span
                    className={cn(
                      "inline-block h-4 w-4 transform rounded-full transition-transform",
                      theme === "dark"
                        ? "translate-x-6 bg-white dark:bg-black"
                        : "translate-x-1 bg-white",
                    )}
                  />
                  <span className="sr-only">Toggle theme</span>
                </button>
              )}
            </div>
          </SectionCard>

          {/* ── Web-fetch whitelist ───────────────────────────────────────── */}
          <SectionCard title="Web Fetch Whitelist" icon={<Globe className="h-4 w-4" />}>
            <p className="mb-4 text-xs text-[--color-muted]">
              Only these domains can be fetched when using the Web Fetch source toggle in chat.
              Subdomains are automatically included (e.g. adding <code>example.com</code> also
              allows <code>www.example.com</code>).
            </p>

            {/* Add domain form */}
            <div className="flex gap-2 mb-4">
              <input
                type="text"
                placeholder="example.com"
                value={newDomain}
                onChange={(e) => setNewDomain(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") handleAddDomain(); }}
                className="flex-1 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-gray-900 dark:focus:ring-gray-100"
              />
              <Button size="sm" onClick={handleAddDomain} loading={addLoading}>
                <Plus className="h-3.5 w-3.5" />
                Add
              </Button>
            </div>
            {addError && <p className="mb-3 text-xs text-red-500">{addError}</p>}

            {/* Domain list */}
            {wlLoading ? (
              <div className="flex justify-center py-4">
                <Spinner className="h-5 w-5" />
              </div>
            ) : whitelist.length === 0 ? (
              <p className="text-center text-xs text-[--color-muted] py-3">
                No domains whitelisted yet.
              </p>
            ) : (
              <ul className="divide-y divide-[--color-border] rounded-lg border border-[--color-border]">
                {whitelist.map((entry) => (
                  <li
                    key={entry.domain}
                    className="flex items-center justify-between px-3 py-2.5"
                  >
                    <div>
                      <p className="text-sm font-mono">{entry.domain}</p>
                      {entry.addedBy && (
                        <p className="text-[10px] text-[--color-muted]">
                          Added by {entry.addedBy}
                        </p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveDomain(entry.domain)}
                      className="rounded p-1 text-[--color-muted] hover:bg-red-500/10 hover:text-red-400 transition-colors"
                      title="Remove"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </SectionCard>
        </div>
      </main>
    </ResizableLayout>
  </>
  );
}
