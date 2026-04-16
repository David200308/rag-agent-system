"use client";

import { Plus, MessageSquare, Trash2, BookOpen, LogOut, PanelLeftClose, Settings, X, Archive, ArchiveRestore, ChevronDown, ChevronRight } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { cn, formatTime } from "@/lib/utils";
import { useTimezone } from "@/hooks/useTimezone";
import { useChatStore } from "@/store/chatStore";
import { Button } from "@/components/ui/Button";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import {
  deleteConversation as apiDeleteConversation,
  archiveConversation as apiArchiveConversation,
  unarchiveConversation as apiUnarchiveConversation,
} from "@/lib/api";

interface SidebarProps {
  onSelectConversation: (id: string) => void;
  isOpen?: boolean;
  onClose?: () => void;
  /** Current desktop sidebar width in px — drives sm:w-[var(--sidebar-w)]. */
  desktopWidth?: number;
  /** Called when the user clicks the collapse button (desktop only). */
  onCollapse?: () => void;
}

export function Sidebar({ onSelectConversation, isOpen = false, onClose, desktopWidth, onCollapse }: SidebarProps) {
  const pathname = usePathname();
  const router   = useRouter();
  const {
    conversations, activeId,
    newConversation, selectConversation, deleteConversation,
    archiveConversation, unarchiveConversation,
  } = useChatStore();

  const { timezone } = useTimezone();
  const [authEnabled, setAuthEnabled] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { enabled: boolean }) => setAuthEnabled(d.enabled))
      .catch(() => {});
  }, []);

  async function handleLogout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
  }

  const handleNew = () => {
    const id = newConversation();
    onSelectConversation(id);
  };

  return (
    <aside
      className={cn(
        // Mobile: fixed width overlay
        "flex h-full w-64 shrink-0 flex-col border-r border-[--color-border]",
        "fixed inset-y-0 left-0 z-50 transition-transform duration-200 ease-in-out",
        isOpen ? "translate-x-0" : "-translate-x-full",
        // Desktop: in-flow, width driven by CSS variable set below
        "sm:relative sm:translate-x-0 sm:transition-none sm:w-[var(--sidebar-w)]",
      )}
      style={{
        backgroundColor: "var(--color-surface-raised)",
        // CSS variable consumed by sm:w-[var(--sidebar-w)] above
        "--sidebar-w": `${desktopWidth ?? 256}px`,
      } as React.CSSProperties}
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[--color-border] px-3 py-3">
        <span className="text-sm font-semibold">RAG Agent</span>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <Button size="icon" variant="ghost" onClick={handleNew} title="New conversation">
            <Plus className="h-4 w-4" />
          </Button>
          {/* Collapse button — desktop only */}
          {onCollapse && (
            <Button
              size="icon"
              variant="ghost"
              onClick={onCollapse}
              title="Collapse sidebar"
              className="hidden sm:flex"
            >
              <PanelLeftClose className="h-4 w-4" />
            </Button>
          )}
          {/* Close button — mobile only */}
          <Button
            size="icon"
            variant="ghost"
            onClick={onClose}
            title="Close menu"
            className="sm:hidden"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Nav links */}
      <nav className="flex gap-1 px-2 py-2">
        <Link
          href="/"
          className={cn(
            "flex flex-1 items-center justify-center gap-1.5 rounded-md py-1.5 text-xs font-medium transition-colors",
            pathname === "/"
              ? "bg-black text-white dark:bg-white dark:text-black"
              : "text-[--color-muted] hover:bg-[--color-border]/50",
          )}
        >
          <MessageSquare className="h-3.5 w-3.5" /> Chat
        </Link>
        <Link
          href="/knowledge"
          className={cn(
            "flex flex-1 items-center justify-center gap-1.5 rounded-md py-1.5 text-xs font-medium transition-colors",
            pathname === "/knowledge"
              ? "bg-black text-white dark:bg-white dark:text-black"
              : "text-[--color-muted] hover:bg-[--color-border]/50",
          )}
        >
          <BookOpen className="h-3.5 w-3.5" /> Knowledge
        </Link>
      </nav>

      {/* Conversation list */}
      <div className="flex-1 overflow-y-auto px-2 py-1">
        {/* Active conversations */}
        {conversations.filter((c) => !c.archived).length === 0 ? (
          <p className="px-2 py-4 text-center text-xs text-[--color-muted]">
            No conversations yet
          </p>
        ) : (
          conversations.filter((c) => !c.archived).map((c) => (
            <div
              key={c.id}
              role="button"
              tabIndex={0}
              onClick={() => {
                selectConversation(c.id);
                onSelectConversation(c.id);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  selectConversation(c.id);
                  onSelectConversation(c.id);
                }
              }}
              className={cn(
                "group flex w-full cursor-pointer items-start justify-between rounded-lg border px-2 py-2 text-left transition-colors",
                activeId === c.id
                  ? "border-[--color-border]"
                  : "border-transparent hover:bg-[--color-border]/40",
              )}
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs font-medium">{c.title}</p>
                <p className="text-[10px] text-[--color-muted]">
                  {formatTime(c.updatedAt, timezone)} · {c.messages.length} msg
                </p>
              </div>
              <div className="ml-1 flex shrink-0 items-center gap-0.5 opacity-0 group-hover:opacity-100">
                <Button
                  size="icon"
                  variant="ghost"
                  className="h-6 w-6"
                  onClick={(e) => {
                    e.stopPropagation();
                    archiveConversation(c.id);
                    const backendId = c.backendConversationId ?? c.id;
                    apiArchiveConversation(backendId).catch(() => {});
                  }}
                  title="Archive"
                >
                  <Archive className="h-3 w-3 text-[--color-muted]" />
                </Button>
                <Button
                  size="icon"
                  variant="ghost"
                  className="h-6 w-6"
                  onClick={(e) => {
                    e.stopPropagation();
                    deleteConversation(c.id);
                    const backendId = c.backendConversationId ?? c.id;
                    apiDeleteConversation(backendId).catch(() => {});
                  }}
                  title="Delete"
                >
                  <Trash2 className="h-3 w-3 text-red-400" />
                </Button>
              </div>
            </div>
          ))
        )}

        {/* Archived section */}
        {conversations.filter((c) => c.archived).length > 0 && (
          <div className="mt-2">
            <button
              onClick={() => setShowArchived((v) => !v)}
              className="flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-xs text-[--color-muted] hover:bg-[--color-border]/40"
            >
              {showArchived
                ? <ChevronDown className="h-3 w-3" />
                : <ChevronRight className="h-3 w-3" />
              }
              <Archive className="h-3 w-3" />
              Archived ({conversations.filter((c) => c.archived).length})
            </button>

            {showArchived && conversations.filter((c) => c.archived).map((c) => (
              <div
                key={c.id}
                className="group flex w-full cursor-pointer items-start justify-between rounded-lg border border-transparent px-2 py-2 text-left opacity-60 hover:opacity-100 hover:bg-[--color-border]/40 transition-all"
              >
                <div
                  className="min-w-0 flex-1"
                  role="button"
                  tabIndex={0}
                  onClick={() => { selectConversation(c.id); onSelectConversation(c.id); }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      selectConversation(c.id);
                      onSelectConversation(c.id);
                    }
                  }}
                >
                  <p className="truncate text-xs font-medium">{c.title}</p>
                  <p className="text-[10px] text-[--color-muted]">
                    {formatTime(c.updatedAt, timezone)} · {c.messages.length} msg
                  </p>
                </div>
                <div className="ml-1 flex shrink-0 items-center gap-0.5 opacity-0 group-hover:opacity-100">
                  <Button
                    size="icon"
                    variant="ghost"
                    className="h-6 w-6"
                    onClick={(e) => {
                      e.stopPropagation();
                      unarchiveConversation(c.id);
                      const backendId = c.backendConversationId ?? c.id;
                      apiUnarchiveConversation(backendId).catch(() => {});
                    }}
                    title="Unarchive"
                  >
                    <ArchiveRestore className="h-3 w-3 text-[--color-muted]" />
                  </Button>
                  <Button
                    size="icon"
                    variant="ghost"
                    className="h-6 w-6"
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteConversation(c.id);
                      const backendId = c.backendConversationId ?? c.id;
                      apiDeleteConversation(backendId).catch(() => {});
                    }}
                    title="Delete"
                  >
                    <Trash2 className="h-3 w-3 text-red-400" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      {/* Footer — settings + logout */}
      <div className="border-t border-[--color-border] px-3 py-2 space-y-0.5">
        <Link
          href="/settings"
          className={cn(
            "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs transition-colors",
            pathname === "/settings"
              ? "bg-black text-white dark:bg-white dark:text-black"
              : "text-[--color-muted] hover:bg-[--color-border]/50",
          )}
        >
          <Settings className="h-3.5 w-3.5" />
          Settings
        </Link>
        {authEnabled && (
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-[--color-muted]
                       transition-colors hover:bg-[--color-border]/50 hover:text-red-400"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </button>
        )}
      </div>
    </aside>
  );
}
