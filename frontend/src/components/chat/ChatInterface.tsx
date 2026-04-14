"use client";

import { useEffect, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import { MessageSquare, Menu } from "lucide-react";
import { queryAgent } from "@/lib/api";
import { useChatStore } from "@/store/chatStore";
import { MessageBubble } from "./MessageBubble";
import { MessageInput } from "./MessageInput";
import { Spinner } from "@/components/ui/Spinner";
import type { AgentRequest } from "@/types/agent";

interface ChatInterfaceProps {
  conversationId: string;
  onMenuOpen?: () => void;
}

export function ChatInterface({ conversationId, onMenuOpen }: ChatInterfaceProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const { conversations, addMessage, setBackendConversationId } = useChatStore();
  const conversation = conversations.find((c) => c.id === conversationId);

  const mutation = useMutation({
    mutationFn: (req: AgentRequest) => queryAgent(req),
    onSuccess: (response) => {
      // Capture the backend-assigned conversationId so subsequent turns link correctly
      const backendId = response.metadata?.conversationId;
      if (backendId && !conversation?.backendConversationId) {
        setBackendConversationId(conversationId, backendId);
      }
      addMessage(conversationId, {
        role: "assistant",
        content: response.answer,
        sources: response.sources,
        routeDecision: response.routeDecision,
        fallbackActivated: response.fallbackActivated,
        metadata: response.metadata,
      });
    },
    onError: (err: Error) => {
      addMessage(conversationId, {
        role: "error",
        content: `Request failed: ${err.message}`,
      });
    },
  });

  // Scroll to bottom on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [conversation?.messages.length]);

  const handleSend = (query: string, topK: number) => {
    const history = (conversation?.messages ?? [])
      .filter((m) => m.role !== "error")
      .map((m) => ({ role: m.role as "user" | "assistant", content: m.content }));

    addMessage(conversationId, { role: "user", content: query });

    mutation.mutate({
      query,
      topK,
      conversationHistory: history,
      stream: false,
      conversationId: conversation?.backendConversationId,
    });
  };

  if (!conversation) return null;

  return (
    <div className="flex h-full flex-col">
      {/* Mobile header */}
      <div className="flex items-center gap-3 border-b border-[--color-border] px-4 py-3 sm:hidden">
        <button
          onClick={onMenuOpen}
          className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50"
          aria-label="Open menu"
        >
          <Menu className="h-5 w-5" />
        </button>
        <span className="truncate text-sm font-medium">
          {conversation?.title ?? "Chat"}
        </span>
      </div>

      {/* Message list */}
      <div className="chat-scroll flex-1 overflow-y-auto px-4 py-6">
        {conversation.messages.length === 0 ? (
          <EmptyState />
        ) : (
          <div className="mx-auto max-w-3xl space-y-6">
            {conversation.messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}

            {mutation.isPending && (
              <div className="flex items-center gap-2 text-sm text-[--color-muted]">
                <Spinner className="h-4 w-4" />
                Thinking…
              </div>
            )}
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <MessageInput onSend={handleSend} disabled={mutation.isPending} />
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 text-center text-[--color-muted]">
      <div className="rounded-full bg-[--color-surface-raised] p-4">
        <MessageSquare className="h-8 w-8" />
      </div>
      <h2 className="text-lg font-semibold text-inherit">Ask anything</h2>
      <p className="max-w-sm text-sm">
        Query your knowledge base or ask general questions. Upload documents
        from the{" "}
        <a href="/upload" className="underline hover:no-underline">
          Upload
        </a>{" "}
        page to enrich the context.
      </p>
    </div>
  );
}
