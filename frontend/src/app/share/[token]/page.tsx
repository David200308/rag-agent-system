"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { Send } from "lucide-react";
import { fetchSharedConversation, submitSharedQuery } from "@/lib/api";
import type { BackendMessage, ShareMetaResponse } from "@/types/agent";

type Status = "loading" | "ok" | "expired" | "error" | "forbidden";

export default function SharedConversationPage() {
  const { token } = useParams<{ token: string }>();

  const [meta, setMeta]         = useState<ShareMetaResponse | null>(null);
  const [messages, setMessages] = useState<BackendMessage[]>([]);
  const [status, setStatus]     = useState<Status>("loading");

  const [query, setQuery]       = useState("");
  const [sending, setSending]   = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

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

  const handleSend = async () => {
    if (!query.trim() || sending) return;
    setSending(true);
    setSendError(null);

    const userMsg: BackendMessage = {
      id: Date.now(),
      conversationId: "",
      role: "user",
      content: query.trim(),
      runId: null,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    const sentQuery = query.trim();
    setQuery("");

    try {
      const response = await submitSharedQuery(token, sentQuery);
      const assistantMsg: BackendMessage = {
        id: Date.now() + 1,
        conversationId: "",
        role: "assistant",
        content: response.answer,
        runId: response.metadata?.runId ?? null,
        createdAt: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: unknown) {
      const errText = err instanceof Error ? err.message : String(err);
      if (errText.includes("401") || errText.includes("Authentication")) {
        setSendError("Authentication required. Please log in and try again.");
      } else if (errText.includes("403") || errText.includes("whitelist")) {
        setSendError("Access denied — your email is not on the whitelist.");
      } else {
        setSendError("Failed to get a response. Please try again.");
      }
      // Remove the optimistic user message on error
      setMessages((prev) => prev.filter((m) => m.id !== userMsg.id));
    } finally {
      setSending(false);
    }
  };

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

  const isInteractive = meta?.shareMode === "INTERACTIVE";

  // ── Main view ──────────────────────────────────────────────────────────────

  return (
    <div className="flex h-screen flex-col bg-[--color-surface]">
      {/* Header */}
      <div className="border-b border-[--color-border] px-4 py-3 flex items-center gap-3">
        <div className="flex-1 min-w-0">
          <h1 className="text-sm font-semibold truncate">Shared Conversation</h1>
          <p className="text-xs text-[--color-muted] mt-0.5">
            {isInteractive ? "Interactive — you can ask questions" : "Read-only view"}
          </p>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <span className={[
            "rounded-full px-2 py-0.5 text-[11px] font-medium",
            isInteractive
              ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
              : "bg-gray-100 text-gray-600 dark:bg-neutral-800 dark:text-gray-400",
          ].join(" ")}>
            {isInteractive ? "Interactive" : "Read only"}
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

      {/* Interactive input */}
      {isInteractive && (
        <div className="border-t border-[--color-border] px-4 py-3">
          <div className="mx-auto max-w-2xl">
            {sendError && (
              <p className="mb-2 text-xs text-red-500">{sendError}</p>
            )}
            <div className="flex items-end gap-2 rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-3 py-2">
              <textarea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                placeholder="Ask a question… (Enter to send)"
                rows={1}
                className="flex-1 resize-none bg-transparent text-sm outline-none placeholder:text-[--color-muted] max-h-32 overflow-y-auto"
              />
              <button
                type="button"
                onClick={handleSend}
                disabled={!query.trim() || sending}
                className="shrink-0 rounded-lg p-1.5 bg-gray-900 text-white dark:bg-gray-100 dark:text-black disabled:opacity-40 transition-opacity"
              >
                {sending
                  ? <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  : <Send className="h-3.5 w-3.5" />}
              </button>
            </div>
            <p className="mt-1 text-[10px] text-[--color-muted]">
              {meta?.accessType === "WHITELIST"
                ? "Login required — your email must be on the whitelist."
                : "Messages are added to the shared conversation."}
              {" In interactive mode, the Telegram tool can notify the owner."}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
