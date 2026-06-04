"use client";

import { useEffect, useState } from "react";
import {
  Users, UserPlus, Trash2, ArrowRightLeft, Menu, Crown,
  User, Check, X, BookOpen, Wrench, ClipboardCheck, Search,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { ResizableLayout } from "@/components/layout/ResizableLayout";
import { Sidebar } from "@/components/layout/Sidebar";
import { useChatStore } from "@/store/chatStore";
import { cn } from "@/lib/utils";

// ── Types ─────────────────────────────────────────────────────────────────────

interface OrgMember {
  orgId: string;
  email: string;
  role: "OWNER" | "MEMBER";
  joinedAt: string;
}

interface PendingKnowledge {
  id: number;
  source: string;
  label: string | null;
  category: string | null;
  ownerEmail: string;
  ingestedAt: string;
}

interface PendingSkill {
  id: string;
  name: string;
  fileName: string;
  fileType: string;
  size: number;
  ownerEmail: string;
  createdAt: string;
}

interface ApprovalsData {
  knowledge: PendingKnowledge[];
  skills: PendingSkill[];
}

type Tab = "members" | "approvals";

// ── Shared table styles ────────────────────────────────────────────────────────

const thCls = "px-3 py-2 text-left text-[11px] font-semibold uppercase tracking-wide text-[--color-muted]";
const tdCls = "px-3 py-2.5 text-sm align-middle";

// ── Tab bar ───────────────────────────────────────────────────────────────────

function TabBar({
  tab,
  setTab,
  pendingCount,
}: {
  tab: Tab;
  setTab: (t: Tab) => void;
  pendingCount: number;
}) {
  return (
    <div className="flex gap-1 border-b border-[--color-border]">
      {(["members", "approvals"] as Tab[]).map((t) => (
        <button
          key={t}
          onClick={() => setTab(t)}
          className={cn(
            "flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm font-medium transition-colors capitalize",
            tab === t
              ? "border-black text-black dark:border-white dark:text-white"
              : "border-transparent text-[--color-muted] hover:text-[--color-fg]",
          )}
        >
          {t === "members" && <Users className="h-3.5 w-3.5" />}
          {t === "approvals" && <ClipboardCheck className="h-3.5 w-3.5" />}
          {t === "members" ? "Members" : "Approvals"}
          {t === "approvals" && pendingCount > 0 && (
            <span className="rounded-full bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold text-white leading-none">
              {pendingCount}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}

// ── Members tab ───────────────────────────────────────────────────────────────

function MembersTab({
  members,
  myEmail,
  isOwner,
  onRemove,
  onAdd,
  onTransfer,
}: {
  members: OrgMember[];
  myEmail: string | null;
  isOwner: boolean;
  onRemove: (email: string) => Promise<void>;
  onAdd: (email: string, role: "MEMBER" | "OWNER") => Promise<string | null>;
  onTransfer: (email: string) => Promise<string | null>;
}) {
  const [search, setSearch] = useState("");

  const [addEmail, setAddEmail] = useState("");
  const [addRole, setAddRole]   = useState<"MEMBER" | "OWNER">("MEMBER");
  const [addLoading, setAddLoading] = useState(false);
  const [addError,   setAddError]   = useState<string | null>(null);

  const [transferEmail,   setTransferEmail]   = useState("");
  const [transferLoading, setTransferLoading] = useState(false);
  const [transferError,   setTransferError]   = useState<string | null>(null);
  const [transferDone,    setTransferDone]     = useState(false);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!addEmail.trim()) return;
    setAddLoading(true);
    setAddError(null);
    const err = await onAdd(addEmail.trim().toLowerCase(), addRole);
    if (err) { setAddError(err); } else { setAddEmail(""); setAddRole("MEMBER"); }
    setAddLoading(false);
  }

  async function handleTransfer(e: React.FormEvent) {
    e.preventDefault();
    if (!transferEmail) return;
    setTransferLoading(true);
    setTransferError(null);
    setTransferDone(false);
    const err = await onTransfer(transferEmail);
    if (err) { setTransferError(err); } else { setTransferDone(true); setTransferEmail(""); }
    setTransferLoading(false);
  }

  const filtered = members.filter((m) =>
    m.email.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <div className="space-y-6">
      {/* Member table */}
      <div className="overflow-hidden rounded-xl border border-[--color-border]">
        {/* Search bar */}
        <div className="border-b border-[--color-border] bg-[--color-surface] px-3 py-2">
          <div className="flex items-center gap-2 rounded-md border border-[--color-border] bg-[--color-surface-raised] px-2.5 py-1.5">
            <Search className="h-3.5 w-3.5 shrink-0 text-[--color-muted]" />
            <input
              type="text"
              placeholder="Search members…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="flex-1 bg-transparent text-sm outline-none placeholder:text-[--color-muted]"
            />
            {search && (
              <button
                onClick={() => setSearch("")}
                className="shrink-0 text-[--color-muted] hover:text-[--color-fg]"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
        </div>
        <table className="w-full table-fixed">
          <thead className="border-b border-[--color-border] bg-[--color-surface]">
            <tr>
              <th className={cn(thCls, "w-8")} />
              <th className={thCls}>Email</th>
              <th className={cn(thCls, "w-24")}>Role</th>
              <th className={cn(thCls, "w-28")}>Joined</th>
              {isOwner && <th className={cn(thCls, "w-12")} />}
            </tr>
          </thead>
          <tbody className="divide-y divide-[--color-border]">
            {filtered.map((m) => {
              const isMe = m.email.toLowerCase() === (myEmail ?? "").toLowerCase();
              return (
                <tr key={m.email} className="bg-[--color-surface] hover:bg-[--color-surface-raised] transition-colors">
                  <td className={tdCls}>
                    {m.role === "OWNER"
                      ? <Crown className="h-3.5 w-3.5 text-amber-400" />
                      : <User className="h-3.5 w-3.5 text-[--color-muted]" />}
                  </td>
                  <td className={cn(tdCls, "max-w-0")}>
                    <div className="flex items-center gap-1 min-w-0">
                      <span className={cn("truncate font-medium", isMe && "text-[--color-accent]")}>
                        {m.email}
                      </span>
                      {isMe && <span className="text-[10px] text-[--color-muted] shrink-0">(you)</span>}
                    </div>
                  </td>
                  <td className={tdCls}>
                    <span className={cn(
                      "rounded-full px-2 py-0.5 text-[10px] font-semibold",
                      m.role === "OWNER"
                        ? "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400"
                        : "bg-[--color-surface-raised] text-[--color-muted]",
                    )}>
                      {m.role.toLowerCase()}
                    </span>
                  </td>
                  <td className={cn(tdCls, "text-[--color-muted] text-xs")}>
                    {new Date(m.joinedAt).toLocaleDateString()}
                  </td>
                  {isOwner && (
                    <td className={tdCls}>
                      {!isMe && (
                        <button
                          onClick={() => onRemove(m.email)}
                          className="text-[--color-muted] hover:text-red-500 transition-colors"
                          title="Remove member"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={isOwner ? 5 : 4} className="px-3 py-8 text-center text-sm text-[--color-muted]">
                  {search ? `No members matching "${search}".` : "No members yet."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Add member — owner only */}
      {isOwner && (
        <div className="rounded-xl border border-[--color-border] bg-[--color-surface] overflow-hidden">
          <div className="flex items-center gap-2 border-b border-[--color-border] px-4 py-3">
            <UserPlus className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Add Member</h2>
          </div>
          <form onSubmit={handleAdd} className="flex flex-wrap items-end gap-3 px-4 py-4">
            <div className="flex-1 min-w-48">
              <label className="mb-1 block text-xs text-[--color-muted]">Email</label>
              <input
                type="email"
                placeholder="member@example.com"
                value={addEmail}
                onChange={(e) => setAddEmail(e.target.value)}
                className="w-full rounded-md border border-[--color-border] bg-[--color-surface-raised] px-3 py-1.5 text-sm outline-none focus:ring-1 focus:ring-[--color-border]"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-[--color-muted]">Role</label>
              <select
                value={addRole}
                onChange={(e) => setAddRole(e.target.value as "MEMBER" | "OWNER")}
                className="rounded-md border border-[--color-border] bg-[--color-surface-raised] px-3 py-1.5 text-sm outline-none"
              >
                <option value="MEMBER">Member</option>
                <option value="OWNER">Owner</option>
              </select>
            </div>
            <Button type="submit" size="sm" disabled={addLoading || !addEmail.trim()}>
              {addLoading ? <Spinner className="h-3.5 w-3.5" /> : "Add"}
            </Button>
          </form>
          {addError && <p className="px-4 pb-3 text-xs text-red-400">{addError}</p>}
        </div>
      )}

      {/* Transfer ownership — owner only */}
      {isOwner && (
        <div className="rounded-xl border border-[--color-border] bg-[--color-surface] overflow-hidden">
          <div className="flex items-center gap-2 border-b border-[--color-border] px-4 py-3">
            <ArrowRightLeft className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Transfer Ownership</h2>
          </div>
          <form onSubmit={handleTransfer} className="flex flex-wrap items-end gap-3 px-4 py-4">
            <div className="flex-1 min-w-48">
              <label className="mb-1 block text-xs text-[--color-muted]">New owner</label>
              <select
                value={transferEmail}
                onChange={(e) => setTransferEmail(e.target.value)}
                className="w-full rounded-md border border-[--color-border] bg-[--color-surface-raised] px-3 py-1.5 text-sm outline-none"
              >
                <option value="">Select member…</option>
                {members
                  .filter((m) => m.email.toLowerCase() !== (myEmail ?? "").toLowerCase())
                  .map((m) => (
                    <option key={m.email} value={m.email}>{m.email}</option>
                  ))}
              </select>
            </div>
            <Button type="submit" variant="destructive" size="sm" disabled={transferLoading || !transferEmail}>
              {transferLoading ? <Spinner className="h-3.5 w-3.5" /> : "Transfer"}
            </Button>
          </form>
          {transferError && <p className="px-4 pb-3 text-xs text-red-400">{transferError}</p>}
          {transferDone && <p className="px-4 pb-3 text-xs text-green-400">Ownership transferred successfully.</p>}
          <p className="px-4 pb-4 text-xs text-[--color-muted]">
            You will become a regular member after transferring.
          </p>
        </div>
      )}
    </div>
  );
}

// ── Approvals tab ─────────────────────────────────────────────────────────────

function ApprovalsTab({
  approvals,
  loading,
  actionLoading,
  onAction,
}: {
  approvals: ApprovalsData | null;
  loading: boolean;
  actionLoading: string | null;
  onAction: (type: "knowledge" | "skills", id: string | number, action: "approve" | "reject") => Promise<void>;
}) {
  if (loading) {
    return <div className="flex justify-center py-16"><Spinner /></div>;
  }

  const hasItems = approvals && (approvals.knowledge.length > 0 || approvals.skills.length > 0);

  if (!hasItems) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-16 text-[--color-muted]">
        <ClipboardCheck className="h-8 w-8 opacity-40" />
        <p className="text-sm">No pending submissions.</p>
      </div>
    );
  }

  function ActionButtons({ type, id }: { type: "knowledge" | "skills"; id: string | number }) {
    const approveKey = `${type}-${id}-approve`;
    const rejectKey  = `${type}-${id}-reject`;
    const busy = actionLoading !== null;
    return (
      <div className="flex gap-1">
        <button
          onClick={() => onAction(type, id, "approve")}
          disabled={busy}
          className="flex items-center justify-center h-7 w-7 rounded text-green-500 hover:bg-green-500/10 disabled:opacity-40 transition-colors"
          title="Approve"
        >
          {actionLoading === approveKey ? <Spinner className="h-3.5 w-3.5" /> : <Check className="h-3.5 w-3.5" />}
        </button>
        <button
          onClick={() => onAction(type, id, "reject")}
          disabled={busy}
          className="flex items-center justify-center h-7 w-7 rounded text-red-500 hover:bg-red-500/10 disabled:opacity-40 transition-colors"
          title="Reject"
        >
          {actionLoading === rejectKey ? <Spinner className="h-3.5 w-3.5" /> : <X className="h-3.5 w-3.5" />}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Knowledge Base table */}
      {approvals!.knowledge.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-[--color-border]">
          <div className="flex items-center gap-2 border-b border-[--color-border] bg-[--color-surface] px-4 py-3">
            <BookOpen className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Knowledge Base</h2>
            <span className="ml-auto rounded-full bg-yellow-100 px-2 py-0.5 text-[10px] font-semibold text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400">
              {approvals!.knowledge.length} pending
            </span>
          </div>
          <table className="w-full table-fixed">
            <thead className="border-b border-[--color-border] bg-[--color-surface]">
              <tr>
                <th className={thCls}>Name</th>
                <th className={cn(thCls, "w-40")}>Submitted by</th>
                <th className={cn(thCls, "w-28")}>Date</th>
                <th className={cn(thCls, "w-20")} />
              </tr>
            </thead>
            <tbody className="divide-y divide-[--color-border]">
              {approvals!.knowledge.map((ks) => (
                <tr key={ks.id} className="bg-[--color-surface] hover:bg-[--color-surface-raised] transition-colors">
                  <td className={cn(tdCls, "max-w-0")}>
                    <div className="truncate font-medium">{ks.label ?? ks.source}</div>
                    {ks.category && <div className="truncate text-[10px] text-[--color-muted]">{ks.category}</div>}
                  </td>
                  <td className={cn(tdCls, "max-w-0 text-xs text-[--color-muted]")}>
                    <div className="truncate">{ks.ownerEmail}</div>
                  </td>
                  <td className={cn(tdCls, "text-xs text-[--color-muted] whitespace-nowrap")}>
                    {new Date(ks.ingestedAt).toLocaleDateString()}
                  </td>
                  <td className={tdCls}>
                    <ActionButtons type="knowledge" id={ks.id} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Skills table */}
      {approvals!.skills.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-[--color-border]">
          <div className="flex items-center gap-2 border-b border-[--color-border] bg-[--color-surface] px-4 py-3">
            <Wrench className="h-4 w-4 text-[--color-muted]" />
            <h2 className="text-sm font-semibold">Skills</h2>
            <span className="ml-auto rounded-full bg-yellow-100 px-2 py-0.5 text-[10px] font-semibold text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400">
              {approvals!.skills.length} pending
            </span>
          </div>
          <table className="w-full table-fixed">
            <thead className="border-b border-[--color-border] bg-[--color-surface]">
              <tr>
                <th className={thCls}>Name</th>
                <th className={cn(thCls, "w-36")}>File</th>
                <th className={cn(thCls, "w-40")}>Submitted by</th>
                <th className={cn(thCls, "w-28")}>Date</th>
                <th className={cn(thCls, "w-20")} />
              </tr>
            </thead>
            <tbody className="divide-y divide-[--color-border]">
              {approvals!.skills.map((skill) => (
                <tr key={skill.id} className="bg-[--color-surface] hover:bg-[--color-surface-raised] transition-colors">
                  <td className={cn(tdCls, "max-w-0")}>
                    <div className="truncate font-medium">{skill.name}</div>
                  </td>
                  <td className={cn(tdCls, "max-w-0 text-xs text-[--color-muted]")}>
                    <div className="flex items-center gap-1 min-w-0">
                      <span className="font-mono shrink-0">{skill.fileType.toUpperCase()}</span>
                      <span className="truncate">{skill.fileName}</span>
                    </div>
                  </td>
                  <td className={cn(tdCls, "max-w-0 text-xs text-[--color-muted]")}>
                    <div className="truncate">{skill.ownerEmail}</div>
                  </td>
                  <td className={cn(tdCls, "text-xs text-[--color-muted] whitespace-nowrap")}>
                    {new Date(skill.createdAt).toLocaleDateString()}
                  </td>
                  <td className={tdCls}>
                    <ActionButtons type="skills" id={skill.id} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function TeamPage() {
  const { selectConversation } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [myEmail, setMyEmail] = useState<string | null>(null);
  const [orgId,   setOrgId]   = useState<string | null>(null);
  const [isTeam,  setIsTeam]  = useState(false);
  const [members, setMembers] = useState<OrgMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState<string | null>(null);

  const [tab, setTab] = useState<Tab>("members");

  const [approvals,       setApprovals]       = useState<ApprovalsData | null>(null);
  const [approvalsLoading, setApprovalsLoading] = useState(false);
  const [actionLoading,   setActionLoading]   = useState<string | null>(null);

  const isOwner = members.some(
    (m) => m.email.toLowerCase() === (myEmail ?? "").toLowerCase() && m.role === "OWNER",
  );

  const pendingCount = approvals
    ? approvals.knowledge.length + approvals.skills.length
    : 0;

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

  useEffect(() => {
    if (isOwner) loadApprovals();
  }, [isOwner]); // eslint-disable-line react-hooks/exhaustive-deps

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

  async function loadApprovals() {
    setApprovalsLoading(true);
    try {
      const r = await fetch("/api/team/approvals");
      if (r.ok) setApprovals(await r.json());
    } catch { /* ignore */ } finally {
      setApprovalsLoading(false);
    }
  }

  async function handleAdd(email: string, role: "MEMBER" | "OWNER"): Promise<string | null> {
    try {
      const r = await fetch("/api/team/members", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email, role }),
      });
      const d = await r.json();
      if (!r.ok) return (d as { error?: string }).error ?? "Failed to add member";
      await loadMembers();
      return null;
    } catch {
      return "Network error";
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

  async function handleTransfer(email: string): Promise<string | null> {
    try {
      const r = await fetch("/api/team/transfer-owner", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const d = await r.json();
      if (!r.ok) return (d as { error?: string }).error ?? "Transfer failed";
      await loadMembers();
      return null;
    } catch {
      return "Network error";
    }
  }

  async function handleApprovalAction(
    type: "knowledge" | "skills",
    id: string | number,
    action: "approve" | "reject",
  ) {
    const key = `${type}-${id}-${action}`;
    setActionLoading(key);
    try {
      const r = await fetch(`/api/team/approvals/${type}/${id}/${action}`, { method: "POST" });
      if (!r.ok) {
        const d = await r.json().catch(() => ({}));
        alert((d as { error?: string }).error ?? "Action failed");
        return;
      }
      await loadApprovals();
    } catch {
      alert("Network error");
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <>
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 bg-black/40 sm:hidden" onClick={() => setSidebarOpen(false)} />
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

          <div className="mx-auto w-full max-w-3xl px-4 py-8">
            {/* Header */}
            <div className="mb-6">
              <div className="flex items-center gap-2 mb-1">
                <Users className="h-5 w-5" />
                <h1 className="text-xl font-semibold">Team Management</h1>
              </div>
              {orgId && (
                <p className="text-xs text-[--color-muted] font-mono">org: {orgId}</p>
              )}
            </div>

            {!isTeam ? (
              <div className="rounded-xl border border-[--color-border] bg-[--color-surface] px-5 py-8 text-center text-sm text-[--color-muted]">
                Team management is only available in team mode.
              </div>
            ) : loading ? (
              <div className="flex justify-center py-16"><Spinner /></div>
            ) : error ? (
              <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-5 py-4 text-sm text-red-400">
                {error}
              </div>
            ) : (
              <div className="space-y-5">
                {/* Tab bar — approvals only visible to owners */}
                {isOwner && (
                  <TabBar tab={tab} setTab={setTab} pendingCount={pendingCount} />
                )}

                {(tab === "members" || !isOwner) && (
                  <MembersTab
                    members={members}
                    myEmail={myEmail}
                    isOwner={isOwner}
                    onRemove={handleRemove}
                    onAdd={handleAdd}
                    onTransfer={handleTransfer}
                  />
                )}

                {tab === "approvals" && isOwner && (
                  <ApprovalsTab
                    approvals={approvals}
                    loading={approvalsLoading}
                    actionLoading={actionLoading}
                    onAction={handleApprovalAction}
                  />
                )}
              </div>
            )}
          </div>
        </div>
      </ResizableLayout>
    </>
  );
}
