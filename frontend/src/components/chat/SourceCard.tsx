"use client";

import { useState } from "react";
import { ChevronDown, ChevronUp, FileText, ExternalLink } from "lucide-react";
import { cn, truncate } from "@/lib/utils";
import { Badge } from "@/components/ui/Badge";
import type { SourceDocument } from "@/types/agent";

interface SourceCardProps {
  doc: SourceDocument;
  index: number;
}

export function SourceCard({ doc, index }: SourceCardProps) {
  const [expanded, setExpanded] = useState(false);

  const scorePercent = Math.round(doc.score * 100);
  const scoreVariant =
    scorePercent >= 85 ? "success" : scorePercent >= 70 ? "warning" : "default";

  return (
    <div className="rounded-lg border border-[--color-border] bg-[--color-surface-raised] text-sm overflow-hidden">
      <button
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-[--color-border]/30 transition-colors"
      >
        <FileText className="h-3.5 w-3.5 shrink-0 text-[--color-muted]" />
        <span className="flex-1 truncate font-medium text-xs">[{index + 1}] {doc.source}</span>
        <Badge variant={scoreVariant}>{scorePercent}%</Badge>
        {doc.category && (
          <Badge variant="info">{doc.category}</Badge>
        )}
        {expanded ? (
          <ChevronUp className="h-3.5 w-3.5 text-[--color-muted]" />
        ) : (
          <ChevronDown className="h-3.5 w-3.5 text-[--color-muted]" />
        )}
      </button>

      {expanded && (
        <div className="border-t border-[--color-border] px-3 py-2 text-xs text-[--color-muted] leading-relaxed">
          <p>{doc.content}</p>
          {doc.source.startsWith("http") && (
            <a
              href={doc.source}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-2 inline-flex items-center gap-1 underline hover:no-underline"
            >
              Open source <ExternalLink className="h-3 w-3" />
            </a>
          )}
        </div>
      )}
    </div>
  );
}
