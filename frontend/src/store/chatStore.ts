import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { BackendConversation, BackendMessage, ChatMessage, Conversation } from "@/types/agent";
import { randomId, deriveTitle } from "@/lib/utils";

interface ChatState {
  conversations: Conversation[];
  activeId: string | null;

  // Derived
  activeConversation: () => Conversation | undefined;

  // Actions
  newConversation: () => string;
  selectConversation: (id: string) => void;
  deleteConversation: (id: string) => void;
  archiveConversation: (id: string) => void;
  unarchiveConversation: (id: string) => void;
  addMessage: (conversationId: string, message: Omit<ChatMessage, "id" | "timestamp">) => string;
  updateLastAssistantMessage: (conversationId: string, patch: Partial<ChatMessage>) => void;
  setBackendConversationId: (conversationId: string, backendId: string) => void;
  /**
   * Replace the conversation list with data fetched from the backend.
   * Called on app load when the user is authenticated.
   */
  syncFromBackend: (
    backendConversations: BackendConversation[],
    messagesByBackendId: Record<string, BackendMessage[]>,
  ) => void;
}

export const useChatStore = create<ChatState>()(
  persist(
    (set, get) => ({
      conversations: [],
      activeId: null,

      activeConversation: () =>
        get().conversations.find((c) => c.id === get().activeId),

      newConversation: () => {
        const id = randomId();
        const now = new Date();
        set((s) => ({
          conversations: [
            {
              id,
              title: "New conversation",
              messages: [],
              createdAt: now,
              updatedAt: now,
            },
            ...s.conversations,
          ],
          activeId: id,
        }));
        return id;
      },

      selectConversation: (id) => set({ activeId: id }),

      deleteConversation: (id) =>
        set((s) => ({
          conversations: s.conversations.filter((c) => c.id !== id),
          activeId: s.activeId === id ? null : s.activeId,
        })),

      archiveConversation: (id) =>
        set((s) => ({
          conversations: s.conversations.map((c) =>
            c.id === id ? { ...c, archived: true } : c,
          ),
          activeId: s.activeId === id ? null : s.activeId,
        })),

      unarchiveConversation: (id) =>
        set((s) => ({
          conversations: s.conversations.map((c) =>
            c.id === id ? { ...c, archived: false } : c,
          ),
        })),

      addMessage: (conversationId, message) => {
        const msgId = randomId();
        set((s) => ({
          conversations: s.conversations.map((c) => {
            if (c.id !== conversationId) return c;
            const newMsg: ChatMessage = {
              ...message,
              id: msgId,
              timestamp: new Date(),
            };
            // Auto-set title from first user message
            const title =
              c.messages.length === 0 && message.role === "user"
                ? deriveTitle(message.content)
                : c.title;
            return {
              ...c,
              title,
              messages: [...c.messages, newMsg],
              updatedAt: new Date(),
            };
          }),
        }));
        return msgId;
      },

      updateLastAssistantMessage: (conversationId, patch) =>
        set((s) => ({
          conversations: s.conversations.map((c) => {
            if (c.id !== conversationId) return c;
            const messages = [...c.messages];
            const lastIdx = messages.findLastIndex((m) => m.role === "assistant");
            if (lastIdx === -1) return c;
            messages[lastIdx] = { ...messages[lastIdx]!, ...patch };
            return { ...c, messages };
          }),
        })),

      setBackendConversationId: (conversationId, backendId) =>
        set((s) => ({
          conversations: s.conversations.map((c) =>
            c.id === conversationId
              ? { ...c, backendConversationId: backendId }
              : c,
          ),
        })),

      syncFromBackend: (backendConversations, messagesByBackendId) => {
        const synced: Conversation[] = backendConversations.map((bc) => {
          const msgs = messagesByBackendId[bc.id] ?? [];
          const chatMessages: ChatMessage[] = msgs.map((m) => ({
            id: String(m.id),
            role: m.role,
            content: m.content,
            timestamp: new Date(m.createdAt),
          }));
          const firstUserMsg = chatMessages.find((m) => m.role === "user");
          return {
            id: bc.id,
            backendConversationId: bc.id,
            title: firstUserMsg ? deriveTitle(firstUserMsg.content) : "Conversation",
            messages: chatMessages,
            archived: bc.archived ?? false,
            createdAt: new Date(bc.createdAt),
            updatedAt: new Date(bc.updatedAt),
          };
        });

        set((s) => {
          const keepActive = synced.some((c) => c.id === s.activeId);
          return {
            conversations: synced,
            // Keep current selection if still valid; otherwise open the most recent
            activeId: keepActive ? s.activeId : (synced[0]?.id ?? null),
          };
        });
      },
    }),
    {
      name: "rag-chat-store",
      // Revive Date objects from JSON strings
      onRehydrateStorage: () => (state) => {
        if (!state) return;
        state.conversations = state.conversations.map((c) => ({
          ...c,
          createdAt: new Date(c.createdAt),
          updatedAt: new Date(c.updatedAt),
          messages: c.messages.map((m) => ({
            ...m,
            timestamp: new Date(m.timestamp),
          })),
        }));
      },
    },
  ),
);
