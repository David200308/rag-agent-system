import { Bot, User, AlertTriangle, Zap, Search, ArrowRight } from "lucide-react";
import { cn, formatTime, formatDuration } from "@/lib/utils";
import { Badge } from "@/components/ui/Badge";
import { MarkdownContent } from "@/components/ui/MarkdownContent";
import { SourceCard } from "./SourceCard";
import { useTimezone } from "@/hooks/useTimezone";
import type { ChatMessage } from "@/types/agent";

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
