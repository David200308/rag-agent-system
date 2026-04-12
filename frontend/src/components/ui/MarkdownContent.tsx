"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import { cn } from "@/lib/utils";

interface MarkdownContentProps {
  content: string;
  className?: string;
}

/**
 * Renders assistant message content with full Markdown + LaTeX support.
 *
 * Markdown features: headings, bold/italic, inline code, code blocks,
 * tables, blockquotes, ordered/unordered lists (via remark-gfm).
 * Math: $inline$ and $$block$$ LaTeX via remark-math + rehype-katex.
 */
export function MarkdownContent({ content, className }: MarkdownContentProps) {
  return (
    <div className={cn("prose prose-sm dark:prose-invert max-w-none", className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex, rehypeRaw]}
        components={{
          // Code blocks and inline code
          code({ className: cls, children, ...props }) {
            const isBlock = cls?.startsWith("language-");
            if (isBlock) {
              return (
                <pre className="overflow-x-auto rounded-lg bg-gray-950 px-4 py-3 text-xs text-gray-100 dark:bg-gray-900">
                  <code className={cls} {...props}>
                    {children}
                  </code>
                </pre>
              );
            }
            return (
              <code
                className="rounded bg-gray-100 px-1 py-0.5 font-mono text-[0.8em] dark:bg-gray-800"
                {...props}
              >
                {children}
              </code>
            );
          },
          // Tables
          table({ children }) {
            return (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">{children}</table>
              </div>
            );
          },
          th({ children }) {
            return (
              <th className="border border-[--color-border] bg-[--color-surface-raised] px-3 py-1.5 text-left font-semibold">
                {children}
              </th>
            );
          },
          td({ children }) {
            return (
              <td className="border border-[--color-border] px-3 py-1.5">{children}</td>
            );
          },
          // Blockquotes
          blockquote({ children }) {
            return (
              <blockquote className="border-l-4 border-[--color-border] pl-4 italic text-[--color-muted]">
                {children}
              </blockquote>
            );
          },
          // Links — open in new tab
          a({ href, children }) {
            return (
              <a href={href} target="_blank" rel="noopener noreferrer" className="underline">
                {children}
              </a>
            );
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
