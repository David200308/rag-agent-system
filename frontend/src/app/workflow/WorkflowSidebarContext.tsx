"use client";

import { createContext, useContext } from "react";

export const WorkflowSidebarContext = createContext<() => void>(() => {});
export const useWorkflowSidebar = () => useContext(WorkflowSidebarContext);
