"use client";

import { Sidebar } from "@/components/layout/Sidebar";
import { SkillsManager } from "@/components/skills/SkillsManager";
import { useChatStore } from "@/store/chatStore";
import { useRouter } from "next/navigation";

export default function SkillsPage() {
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
        <SkillsManager />
      </main>
    </div>
  );
}
