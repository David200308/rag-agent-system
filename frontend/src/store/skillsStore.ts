import { create } from "zustand";
import { persist } from "zustand/middleware";

interface SkillsState {
  /** agentId (number as string) → selected skill IDs */
  agentSkills: Record<string, string[]>;

  setAgentSkills: (agentId: number, skillIds: string[]) => void;
  getAgentSkills: (agentId: number) => string[];
  clearAgentSkills: (agentId: number) => void;
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
    }),
    { name: "agent-skills" }
  )
);
