"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { fetchSharedConversation } from "@/lib/api";
import type { BackendMessage, ShareMetaResponse } from "@/types/agent";

type Status = "loading" | "ok" | "expired" | "error";

export default function SharedConversationPage() {
  const { token } = useParams<{ token: string }>();

  const [meta, setMeta]         = useState<ShareMetaResponse | null>(null);
  const [messages, setMessages] = useState<BackendMessage[]>([]);
  const [status, setStatus]     = useState<Status>("loading");

  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!token) return;
    fetchSharedConversation(token)
      .then((data) => {
        if (!data) { setStatus("expired"); return; }
        setMeta(data);
        setMessages(data.messages);
        setStatus("ok");
      })
      .catch(() => setStatus("error"));
  }, [token]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // ── Status screens ─────────────────────────────────────────────────────────

  if (status === "loading") {
    return (
      <div className="flex h-screen items-center justify-center bg-[--color-surface]">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-gray-900 dark:border-gray-100 border-t-transparent" />
      </div>
    );
  }

  if (status === "expired") {
    return (
      <div className="flex h-screen items-center justify-center bg-[--color-surface]">
        <div className="text-center space-y-2">
          <p className="text-lg font-semibold">Link expired or not found</p>
          <p className="text-sm text-[--color-muted]">This share link is invalid or has expired.</p>
        </div>
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="flex h-screen items-center justify-center bg-[--color-surface]">
        <div className="text-center space-y-2">
          <p className="text-lg font-semibold">Something went wrong</p>
          <p className="text-sm text-[--color-muted]">Could not load the shared conversation.</p>
        </div>
      </div>
    );
  }

  // ── Main view ──────────────────────────────────────────────────────────────

  return (
    <div className="flex h-screen flex-col bg-[--color-surface]">
      {/* Header */}
      <div className="border-b border-[--color-border] px-4 py-3 flex items-center gap-3">
        <div className="flex-1 min-w-0">
          <h1 className="text-sm font-semibold truncate">Shared Conversation</h1>
          <p className="text-xs text-[--color-muted] mt-0.5">Read-only view</p>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <span className="rounded-full px-2 py-0.5 text-[11px] font-medium bg-gray-100 text-gray-600 dark:bg-neutral-800 dark:text-gray-400">
            Read only
          </span>
          {meta?.accessType === "WHITELIST" && (
            <span className="rounded-full border border-[--color-border] px-2 py-0.5 text-[11px] text-[--color-muted]">
              Whitelist
            </span>
          )}
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
        <div className="mx-auto max-w-2xl space-y-4">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={[
                "flex",
                msg.role === "user" ? "justify-end" : "justify-start",
              ].join(" ")}
            >
              <div
                className={[
                  "max-w-[80%] rounded-2xl px-4 py-2.5 text-sm whitespace-pre-wrap",
                  msg.role === "user"
                    ? "bg-gray-900 text-white dark:bg-gray-100 dark:text-black rounded-br-sm"
                    : "bg-[--color-surface-raised] border border-[--color-border] rounded-bl-sm",
                ].join(" ")}
              >
                {msg.content}
              </div>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  );
}
