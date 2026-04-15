"use client";

import { useEffect, useState } from "react";
import { ResizableLayout } from "@/components/layout/ResizableLayout";
import { Sidebar } from "@/components/layout/Sidebar";
import { ChatInterface } from "@/components/chat/ChatInterface";
import { useChatStore } from "@/store/chatStore";
import { fetchConversations, fetchConversationMessages } from "@/lib/api";
import type { BackendMessage } from "@/types/agent";

export default function HomePage() {
  const { activeId, newConversation, selectConversation, syncFromBackend } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // On mount: load conversations from the backend to enable cross-device sync
  useEffect(() => {
    async function loadFromBackend() {
      const backendConversations = await fetchConversations();
      if (backendConversations.length === 0) {
        if (!activeId) newConversation();
        return;
      }

      const messageEntries = await Promise.all(
        backendConversations.map(async (bc) => {
          const msgs = await fetchConversationMessages(bc.id);
          return [bc.id, msgs] as [string, BackendMessage[]];
        }),
      );
      const messagesByBackendId = Object.fromEntries(messageEntries);
      syncFromBackend(backendConversations, messagesByBackendId);
    }

    loadFromBackend();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSelect = (id: string) => {
    selectConversation(id);
    setSidebarOpen(false);
  };

  return (
    <>
      {/* Mobile backdrop — fixed overlay, outside layout flow */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 sm:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <ResizableLayout
        sidebar={(width, onCollapse) => (
          <Sidebar
            onSelectConversation={handleSelect}
            isOpen={sidebarOpen}
            onClose={() => setSidebarOpen(false)}
            desktopWidth={width}
            onCollapse={onCollapse}
          />
        )}
      >
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
      </ResizableLayout>
    </>
  );
}
