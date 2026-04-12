"use client";

import { useEffect } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { ChatInterface } from "@/components/chat/ChatInterface";
import { useChatStore } from "@/store/chatStore";

export default function HomePage() {
  const { activeId, newConversation, selectConversation } = useChatStore();

  // Ensure there is always an active conversation on mount
  useEffect(() => {
    if (!activeId) {
      newConversation();
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSelect = (id: string) => selectConversation(id);

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar onSelectConversation={handleSelect} />

      <main className="flex flex-1 flex-col overflow-hidden">
        {activeId ? (
          <ChatInterface conversationId={activeId} />
        ) : (
          <div className="flex flex-1 items-center justify-center text-[--color-muted]">
            Select or create a conversation
          </div>
        )}
      </main>
    </div>
  );
}
