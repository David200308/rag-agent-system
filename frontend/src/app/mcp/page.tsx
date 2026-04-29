"use client";

import { useState } from "react";
import { Menu } from "lucide-react";
import { ResizableLayout } from "@/components/layout/ResizableLayout";
import { Sidebar } from "@/components/layout/Sidebar";
import { McpConnector } from "@/components/mcp/McpConnector";
import { useChatStore } from "@/store/chatStore";
import { useRouter } from "next/navigation";

export default function McpPage() {
  const router = useRouter();
  const { selectConversation } = useChatStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleSelect = (id: string) => {
    selectConversation(id);
    router.push("/");
  };

  return (
    <>
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
        <div className="flex h-full flex-col">
          <div className="flex items-center gap-2 border-b border-[--color-border] px-4 py-3 sm:hidden">
            <button
              onClick={() => setSidebarOpen(true)}
              className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50"
              aria-label="Open menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <span className="text-sm font-medium">MCP</span>
          </div>
          <main className="flex-1 overflow-y-auto bg-[--color-surface]">
            <McpConnector />
          </main>
        </div>
      </ResizableLayout>
    </>
  );
}
