"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { WorkflowBuilder } from "@/components/workflow/WorkflowBuilder";
import { fetchWorkflow } from "@/lib/api";
import type { Workflow } from "@/types/agent";

export default function WorkflowDetailPage() {
  const { id }   = useParams<{ id: string }>();
  const router   = useRouter();
  const [workflow, setWorkflow] = useState<Workflow | null>(null);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    fetchWorkflow(id).then(wf => {
      setWorkflow(wf);
      setLoading(false);
    });
  }, [id]);

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-[--color-muted] text-sm">
        Loading…
      </div>
    );
  }

  if (!workflow) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 text-[--color-muted]">
        <p className="text-sm">Workflow not found.</p>
        <Button size="sm" onClick={() => router.push("/workflow")}>Back to Workflows</Button>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-[--color-border] px-4 py-3">
        <Button size="icon" variant="ghost" onClick={() => router.push("/workflow")}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-sm font-semibold">{workflow.name}</h1>
          {workflow.description && (
            <p className="text-xs text-[--color-muted]">{workflow.description}</p>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-hidden">
        <WorkflowBuilder workflow={workflow} />
      </div>
    </div>
  );
}
