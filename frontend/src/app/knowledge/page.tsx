"use client";

import { Sidebar } from "@/components/layout/Sidebar";
import { KnowledgeBase } from "@/components/knowledge/KnowledgeBase";
import { useChatStore } from "@/store/chatStore";
import { useRouter } from "next/navigation";

export default function KnowledgePage() {
  const router = useRouter();
  const { selectConversation } = useChatStore();

  const handleSelect = (id: string) => {
    selectConversation(id);
    router.push("/");
  };

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar onSelectConversation={handleSelect} />
      <main className="flex-1 overflow-y-auto bg-[--color-surface]">
        <KnowledgeBase />
      </main>
    </div>
  );
}
