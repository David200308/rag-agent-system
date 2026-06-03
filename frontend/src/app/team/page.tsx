"use client";

import { useEffect, useState } from "react";
import { Users, UserPlus, Trash2, ArrowRightLeft, Menu, Crown, User } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { ResizableLayout } from "@/components/layout/ResizableLayout";
import { Sidebar } from "@/components/layout/Sidebar";
import { useChatStore } from "@/store/chatStore";
import { cn } from "@/lib/utils";

interface OrgMember {
  orgId: string;
  email: string;
  role: "OWNER" | "MEMBER";
  joinedAt: string;
}

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

export default function TeamPage() {
  const { selectConversation } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [myEmail, setMyEmail]   = useState<string | null>(null);
  const [orgId, setOrgId]       = useState<string | null>(null);
  const [isTeam, setIsTeam]     = useState(false);
  const [members, setMembers]   = useState<OrgMember[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState<string | null>(null);

  const [addEmail, setAddEmail]   = useState("");
  const [addRole, setAddRole]     = useState<"MEMBER" | "OWNER">("MEMBER");
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError]   = useState<string | null>(null);

  const [transferEmail, setTransferEmail]     = useState("");
  const [transferLoading, setTransferLoading] = useState(false);
  const [transferError, setTransferError]     = useState<string | null>(null);
  const [transferDone, setTransferDone]       = useState(false);

  const isOwner = members.some(
    (m) => m.email.toLowerCase() === (myEmail ?? "").toLowerCase() && m.role === "OWNER",
  );

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { enabled: boolean; mode?: string; orgId?: string; email?: string }) => {
        setIsTeam(d.mode === "TEAM");
        setOrgId(d.orgId ?? null);
        setMyEmail(d.email ?? null);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!isTeam) { setLoading(false); return; }
    loadMembers();
  }, [isTeam]); // eslint-disable-line react-hooks/exhaustive-deps

  async function loadMembers() {
    setLoading(true);
    setError(null);
    try {
      const r = await fetch("/api/team/members");
      if (!r.ok) {
        const d = await r.json().catch(() => ({}));
        setError((d as { error?: string }).error ?? "Failed to load members");
        return;
      }
      setMembers(await r.json());
    } catch {
      setError("Network error loading members");
    } finally {
      setLoading(false);
    }
  }

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!addEmail.trim()) return;
    setAddLoading(true);
    setAddError(null);
    try {
      const r = await fetch("/api/team/members", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: addEmail.trim().toLowerCase(), role: addRole }),
      });
      const d = await r.json();
      if (!r.ok) { setAddError((d as { error?: string }).error ?? "Failed to add member"); return; }
      setAddEmail("");
      setAddRole("MEMBER");
      await loadMembers();
    } catch {
      setAddError("Network error");
    } finally {
      setAddLoading(false);
    }
  }

  async function handleRemove(email: string) {
    if (!confirm(`Remove ${email} from the team?`)) return;
    try {
      const r = await fetch(`/api/team/members/${encodeURIComponent(email)}`, { method: "DELETE" });
      if (!r.ok) {
        const d = await r.json().catch(() => ({}));
        alert((d as { error?: string }).error ?? "Failed to remove member");
        return;
      }
      await loadMembers();
    } catch {
      alert("Network error");
    }
  }

  async function handleTransfer(e: React.FormEvent) {
    e.preventDefault();
    if (!transferEmail.trim()) return;
    setTransferLoading(true);
    setTransferError(null);
    setTransferDone(false);
    try {
      const r = await fetch("/api/team/transfer-owner", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: transferEmail.trim().toLowerCase() }),
      });
      const d = await r.json();
      if (!r.ok) { setTransferError((d as { error?: string }).error ?? "Transfer failed"); return; }
      setTransferDone(true);
      setTransferEmail("");
      await loadMembers();
    } catch {
      setTransferError("Network error");
    } finally {
      setTransferLoading(false);
    }
  }

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
            onSelectConversation={(id) => { selectConversation(id); setSidebarOpen(false); }}
            isOpen={sidebarOpen}
            onClose={() => setSidebarOpen(false)}
            desktopWidth={width}
            onCollapse={onCollapse}
          />
        )}
      >
        <div className="flex flex-1 flex-col overflow-y-auto">
          {/* Mobile header */}
          <div className="flex items-center gap-3 border-b border-[--color-border] px-4 py-3 sm:hidden">
            <Button size="icon" variant="ghost" onClick={() => setSidebarOpen(true)}>
              <Menu className="h-5 w-5" />
            </Button>
            <span className="font-semibold">Team</span>
          </div>

          <div className="mx-auto w-full max-w-2xl space-y-5 px-4 py-8">
            <div>
              <h1 className="text-xl font-bold">Team Management</h1>
              {orgId && (
                <p className="mt-1 text-xs text-[--color-muted] font-mono">org: {orgId}</p>
              )}
            </div>

            {!isTeam ? (
              <div className="rounded-xl border border-[--color-border] bg-[--color-surface] px-5 py-8 text-center text-sm text-[--color-muted]">
                Team management is only available in team mode.
              </div>
            ) : loading ? (
              <div className="flex justify-center py-10"><Spinner /></div>
            ) : error ? (
              <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-5 py-4 text-sm text-red-400">
                {error}
              </div>
            ) : (
              <>
                {/* Member list */}
                <SectionCard title="Members" icon={<Users className="h-4 w-4" />}>
                  <div className="space-y-2">
                    {members.map((m) => (
                      <div
                        key={m.email}
                        className="flex items-center justify-between rounded-lg border border-[--color-border] px-3 py-2.5"
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          {m.role === "OWNER"
                            ? <Crown className="h-3.5 w-3.5 shrink-0 text-amber-400" />
                            : <User className="h-3.5 w-3.5 shrink-0 text-[--color-muted]" />}
                          <div className="min-w-0">
                            <p className={cn("truncate text-sm font-medium", m.email.toLowerCase() === (myEmail ?? "").toLowerCase() && "text-[--color-accent]")}>
                              {m.email}
                              {m.email.toLowerCase() === (myEmail ?? "").toLowerCase() && (
                                <span className="ml-1.5 text-[10px] text-[--color-muted]">(you)</span>
                              )}
                            </p>
                            <p className="text-[10px] text-[--color-muted] capitalize">{m.role.toLowerCase()}</p>
                          </div>
                        </div>
                        {isOwner && m.email.toLowerCase() !== (myEmail ?? "").toLowerCase() && (
                          <Button
                            size="icon"
                            variant="ghost"
                            className="h-7 w-7 shrink-0"
                            onClick={() => handleRemove(m.email)}
                            title="Remove member"
                          >
                            <Trash2 className="h-3.5 w-3.5 text-red-400" />
                          </Button>
                        )}
                      </div>
                    ))}
                    {members.length === 0 && (
                      <p className="text-sm text-[--color-muted]">No members yet.</p>
                    )}
                  </div>
                </SectionCard>

                {/* Add member — owner only */}
                {isOwner && (
                  <SectionCard title="Add Member" icon={<UserPlus className="h-4 w-4" />}>
                    <form onSubmit={handleAdd} className="space-y-3">
                      <div className="flex gap-2">
                        <input
                          type="email"
                          placeholder="member@example.com"
                          value={addEmail}
                          onChange={(e) => setAddEmail(e.target.value)}
                          className="flex-1 rounded-md border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-[--color-border]"
                        />
                        <select
                          value={addRole}
                          onChange={(e) => setAddRole(e.target.value as "MEMBER" | "OWNER")}
                          className="rounded-md border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm outline-none"
                        >
                          <option value="MEMBER">Member</option>
                          <option value="OWNER">Owner</option>
                        </select>
                      </div>
                      {addError && <p className="text-xs text-red-400">{addError}</p>}
                      <Button type="submit" disabled={addLoading || !addEmail.trim()}>
                        {addLoading ? <Spinner className="h-4 w-4" /> : "Add Member"}
                      </Button>
                    </form>
                  </SectionCard>
                )}

                {/* Transfer ownership — owner only */}
                {isOwner && (
                  <SectionCard title="Transfer Ownership" icon={<ArrowRightLeft className="h-4 w-4" />}>
                    <p className="mb-3 text-xs text-[--color-muted]">
                      Transfer owner role to another member. You will become a regular member.
                    </p>
                    <form onSubmit={handleTransfer} className="space-y-3">
                      <select
                        value={transferEmail}
                        onChange={(e) => setTransferEmail(e.target.value)}
                        className="w-full rounded-md border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm outline-none"
                      >
                        <option value="">Select new owner…</option>
                        {members
                          .filter((m) => m.email.toLowerCase() !== (myEmail ?? "").toLowerCase())
                          .map((m) => (
                            <option key={m.email} value={m.email}>{m.email}</option>
                          ))}
                      </select>
                      {transferError && <p className="text-xs text-red-400">{transferError}</p>}
                      {transferDone && <p className="text-xs text-green-400">Ownership transferred successfully.</p>}
                      <Button
                        type="submit"
                        variant="destructive"
                        disabled={transferLoading || !transferEmail}
                      >
                        {transferLoading ? <Spinner className="h-4 w-4" /> : "Transfer Ownership"}
                      </Button>
                    </form>
                  </SectionCard>
                )}
              </>
            )}
          </div>
        </div>
      </ResizableLayout>
    </>
  );
}
