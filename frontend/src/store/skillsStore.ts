import { create } from "zustand";
import { persist } from "zustand/middleware";

interface SkillsState {
  /** agentId (number as string) → selected skill IDs */
  agentSkills: Record<string, string[]>;

  setAgentSkills: (agentId: number, skillIds: string[]) => void;
  getAgentSkills: (agentId: number) => string[];
  clearAgentSkills: (agentId: number) => void;
  /** Reset in-memory and persisted state. Called on logout so the next user on this device doesn't see the previous user's skill selections. */
  clearAll: () => void;
}

export const useSkillsStore = create<SkillsState>()(
  persist(
    (set, get) => ({
      agentSkills: {},

      setAgentSkills: (agentId, skillIds) =>
        set((s) => ({
          agentSkills: { ...s.agentSkills, [String(agentId)]: skillIds },
        })),

      getAgentSkills: (agentId) => get().agentSkills[String(agentId)] ?? [],

      clearAgentSkills: (agentId) =>
        set((s) => {
          const next = { ...s.agentSkills };
          delete next[String(agentId)];
          return { agentSkills: next };
        }),

      clearAll: () => set({ agentSkills: {} }),
    }),
    { name: "agent-skills" }
  )
);
