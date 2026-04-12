"use client";

import { Plus, MessageSquare, Trash2, BookOpen, LogOut } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { cn, formatTime } from "@/lib/utils";
import { useChatStore } from "@/store/chatStore";
import { Button } from "@/components/ui/Button";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

interface SidebarProps {
  onSelectConversation: (id: string) => void;
}

export function Sidebar({ onSelectConversation }: SidebarProps) {
  const pathname = usePathname();
  const router   = useRouter();
  const { conversations, activeId, newConversation, selectConversation, deleteConversation } =
    useChatStore();

  const [authEnabled, setAuthEnabled] = useState(false);

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
    <aside className="flex h-full w-64 shrink-0 flex-col border-r border-[--color-border] bg-[--color-surface-raised]">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[--color-border] px-3 py-3">
        <span className="text-sm font-semibold">RAG Agent</span>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <Button size="icon" variant="ghost" onClick={handleNew} title="New conversation">
            <Plus className="h-4 w-4" />
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
        {conversations.length === 0 ? (
          <p className="px-2 py-4 text-center text-xs text-[--color-muted]">
            No conversations yet
          </p>
        ) : (
          conversations.map((c) => (
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
                  {formatTime(c.updatedAt)} · {c.messages.length} msg
                </p>
              </div>
              <Button
                size="icon"
                variant="ghost"
                className="ml-1 h-6 w-6 shrink-0 opacity-0 group-hover:opacity-100"
                onClick={(e) => {
                  e.stopPropagation();
                  deleteConversation(c.id);
                }}
                title="Delete"
              >
                <Trash2 className="h-3 w-3 text-red-400" />
              </Button>
            </div>
          ))
        )}
      </div>
      {/* Footer — logout (only when auth is on) */}
      {authEnabled && (
        <div className="border-t border-[--color-border] px-3 py-2">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-xs text-[--color-muted]
                       transition-colors hover:bg-[--color-border]/50 hover:text-red-400"
          >
            <LogOut className="h-3.5 w-3.5" />
            Sign out
          </button>
        </div>
      )}
    </aside>
  );
}
