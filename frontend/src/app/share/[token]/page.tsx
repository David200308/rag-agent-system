"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import type { BackendMessage } from "@/types/agent";

type Status = "loading" | "ok" | "expired" | "error";

export default function SharedConversationPage() {
  const { token } = useParams<{ token: string }>();
  const [messages, setMessages] = useState<BackendMessage[]>([]);
  const [status, setStatus] = useState<Status>("loading");

  useEffect(() => {
    if (!token) return;

    fetch(`/api/share/${token}`)
      .then(async (res) => {
        if (res.status === 404) { setStatus("expired"); return; }
        if (!res.ok) { setStatus("error"); return; }
        const data = (await res.json()) as BackendMessage[];
        setMessages(data);
        setStatus("ok");
      })
      .catch(() => setStatus("error"));
  }, [token]);

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

  return (
    <div className="min-h-screen bg-[--color-surface]">
      <div className="mx-auto max-w-2xl px-4 py-10 space-y-6">
        <div className="border-b border-[--color-border] pb-4">
          <h1 className="text-lg font-semibold">Shared Conversation</h1>
          <p className="text-xs text-[--color-muted] mt-0.5">Read-only view</p>
        </div>

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
      </div>
    </div>
  );
}
