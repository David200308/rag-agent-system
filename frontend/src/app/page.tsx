"use client";

import { useEffect, useState } from "react";
import { Sidebar } from "@/components/layout/Sidebar";
import { ChatInterface } from "@/components/chat/ChatInterface";
import { useChatStore } from "@/store/chatStore";

export default function HomePage() {
  const { activeId, newConversation, selectConversation } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Ensure there is always an active conversation on mount
  useEffect(() => {
    if (!activeId) {
      newConversation();
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSelect = (id: string) => {
    selectConversation(id);
    setSidebarOpen(false);
  };

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Mobile backdrop */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 sm:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <Sidebar
        onSelectConversation={handleSelect}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <main className="flex flex-1 flex-col overflow-hidden">
        {activeId ? (
          <ChatInterface
            conversationId={activeId}
            onMenuOpen={() => setSidebarOpen(true)}
          />
        ) : (
          <div className="flex flex-1 items-center justify-center text-[--color-muted]">
            Select or create a conversation
          </div>
        )}
      </main>
    </div>
  );
}
