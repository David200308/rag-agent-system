import { Bot, User, AlertTriangle, Zap, Search, ArrowRight, ExternalLink, FileText, Table2, Presentation } from "lucide-react";
import { cn, formatTime, formatDuration } from "@/lib/utils";
import { Badge } from "@/components/ui/Badge";
import { MarkdownContent } from "@/components/ui/MarkdownContent";
import { SourceCard } from "./SourceCard";
import { useTimezone } from "@/hooks/useTimezone";
import type { ChatMessage } from "@/types/agent";

type WorkspaceLink = { url: string; label: string; icon: React.ReactNode; color: string };

function extractWorkspaceLinks(content: string): WorkspaceLink[] {
  const urlRegex = /https:\/\/(?:docs|sheets|slides)\.google\.com\/[^\s)>\]"']+/g;
  const matches = content.match(urlRegex) ?? [];
  return matches.map((url) => {
    if (url.includes("docs.google.com"))
      return { url, label: "Open in Google Docs",   icon: <FileText className="h-4 w-4" />,     color: "text-blue-600 dark:text-blue-400" };
    if (url.includes("sheets.google.com"))
      return { url, label: "Open in Google Sheets", icon: <Table2 className="h-4 w-4" />,       color: "text-green-600 dark:text-green-400" };
    return   { url, label: "Open in Google Slides", icon: <Presentation className="h-4 w-4" />, color: "text-orange-500 dark:text-orange-400" };
  });
}

interface MessageBubbleProps {
  message: ChatMessage;
}

const routeIcon = {
  RETRIEVE: <Search className="h-3 w-3" />,
  DIRECT:   <Zap className="h-3 w-3" />,
  FALLBACK: <AlertTriangle className="h-3 w-3" />,
  ERROR:    <AlertTriangle className="h-3 w-3" />,
};

const routeVariant = {
  RETRIEVE: "info",
  DIRECT:   "success",
  FALLBACK: "warning",
  ERROR:    "danger",
} as const;

export function MessageBubble({ message }: MessageBubbleProps) {
  const { timezone } = useTimezone();
  const isUser = message.role === "user";
  const isError = message.role === "error";
  const workspaceLinks = (!isUser && !isError) ? extractWorkspaceLinks(message.content) : [];

  return (
    <div className={cn("flex gap-3", isUser && "flex-row-reverse")}>
      {/* Avatar */}
      <div
        className={cn(
          "flex h-8 w-8 shrink-0 items-center justify-center rounded-full",
          isUser  ? "bg-black text-white dark:bg-white dark:text-black" :
          isError ? "bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-200" :
                    "bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-200",
        )}
      >
        {isUser ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
      </div>

      {/* Bubble + metadata */}
      <div className={cn("flex max-w-[85%] sm:max-w-[75%] flex-col gap-2", isUser && "items-end")}>
        {/* Text */}
        <div
          className={cn(
            "rounded-2xl px-4 py-2.5 text-sm leading-relaxed",
            isUser
              ? "rounded-tr-sm bg-black text-white dark:bg-white dark:text-black whitespace-pre-wrap"
              : isError
              ? "rounded-tl-sm bg-gray-100 text-gray-700 border border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700 whitespace-pre-wrap"
              : "rounded-tl-sm bg-[--color-surface-raised] text-inherit border border-[--color-border]",
          )}
        >
          {isUser || isError ? (
            message.content
          ) : (
            <MarkdownContent content={message.content} />
          )}
        </div>

        {/* Google Workspace tool-call cards */}
        {workspaceLinks.length > 0 && (
          <div className="flex flex-col gap-1.5 w-full">
            {workspaceLinks.map((link) => (
              <a
                key={link.url}
                href={link.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2.5 rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-3 py-2 text-sm transition-colors hover:bg-[--color-surface] group"
              >
                <span className={link.color}>{link.icon}</span>
                <span className="flex-1 font-medium truncate">{link.label}</span>
                <ExternalLink className="h-3.5 w-3.5 text-[--color-muted] group-hover:text-inherit transition-colors shrink-0" />
              </a>
            ))}
          </div>
        )}

        {/* Route + fallback badges */}
        {message.routeDecision && (
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge variant={routeVariant[message.routeDecision.route]}>
              <span className="flex items-center gap-1">
                {routeIcon[message.routeDecision.route]}
                {message.routeDecision.route}
              </span>
            </Badge>

            {message.fallbackActivated && (
              <Badge variant="warning">Fallback activated</Badge>
            )}

            {message.metadata && (
              <>
                <Badge variant="default">{message.metadata.modelUsed}</Badge>
                <Badge variant="default">
                  {message.metadata.documentsRetrieved} docs
                </Badge>
                <Badge variant="default">
                  {formatDuration(message.metadata.durationMs)}
                </Badge>
              </>
            )}
          </div>
        )}

        {/* Source documents */}
        {message.sources && message.sources.length > 0 && (
          <div className="w-full space-y-1.5">
            <p className="flex items-center gap-1 text-xs text-[--color-muted]">
              <ArrowRight className="h-3 w-3" />
              {message.sources.length} source{message.sources.length > 1 ? "s" : ""}
            </p>
            {message.sources.map((doc, i) => (
              <SourceCard key={doc.id} doc={doc} index={i} />
            ))}
          </div>
        )}

        {/* Timestamp */}
        <span className="text-[10px] text-[--color-muted]">
          {formatTime(message.timestamp, timezone)}
        </span>
      </div>
    </div>
  );
}
