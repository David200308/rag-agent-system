"use client";

import { useState } from "react";
import { ResizableLayout } from "@/components/layout/ResizableLayout";
import { Sidebar } from "@/components/layout/Sidebar";
import { useRouter } from "next/navigation";
import { WorkflowSidebarContext } from "./WorkflowSidebarContext";

export default function WorkflowLayout({ children }: { children: React.ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const router = useRouter();

  return (
    <>
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 sm:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}
      <WorkflowSidebarContext.Provider value={() => setSidebarOpen(true)}>
        <ResizableLayout
          sidebar={(width, onCollapse) => (
            <Sidebar
              onSelectConversation={id => {
                router.push(`/?conv=${id}`);
                setSidebarOpen(false);
              }}
              isOpen={sidebarOpen}
              onClose={() => setSidebarOpen(false)}
              desktopWidth={width}
              onCollapse={onCollapse}
            />
          )}
        >
          {children}
        </ResizableLayout>
      </WorkflowSidebarContext.Provider>
    </>
  );
}
