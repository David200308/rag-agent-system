"use client";

import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import katex from "katex";
import { Monitor, Smartphone } from "lucide-react";
import { cn } from "@/lib/utils";

interface MarkdownContentProps {
  content: string;
  className?: string;
  /** Skip Tailwind Typography prose — use compact flat styling for dense contexts (log viewer, etc.) */
  compact?: boolean;
}

type PreviewMode = "desktop" | "mobile";

function HtmlPreview({ source }: { source: string }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<PreviewMode>("desktop");

  return (
    <>
      {/* Code block with "Preview" button */}
      <div className="rounded-lg border border-[--color-border] overflow-hidden my-2">
        <div className="flex items-center justify-between bg-gray-950 px-4 py-1.5">
          <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide">HTML</span>
          <button
            onClick={() => setOpen(true)}
            className="text-[11px] text-blue-400 hover:text-blue-300 font-medium transition-colors"
          >
            Preview
          </button>
        </div>
        <pre className="overflow-x-auto px-4 py-3 text-xs text-gray-100 bg-gray-950 m-0">
          <code className="language-html">{source}</code>
        </pre>
      </div>

      {/* Full-screen popup modal */}
      {open && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60"
          onClick={e => { if (e.target === e.currentTarget) setOpen(false); }}
        >
          <div className="relative flex flex-col bg-white rounded-xl shadow-2xl overflow-hidden"
            style={{ width: "min(90vw, 1100px)", height: "min(90vh, 800px)" }}
          >
            {/* Modal toolbar */}
            <div className="flex shrink-0 items-center gap-3 bg-gray-100 border-b border-gray-200 px-4 py-2">
              <span className="text-xs font-semibold text-gray-600 uppercase tracking-wide">HTML Preview</span>

              {/* Desktop / Mobile toggle */}
              <div className="ml-auto flex items-center gap-1 rounded-lg bg-gray-200 p-0.5">
                <button
                  onClick={() => setMode("desktop")}
                  title="Desktop view"
                  className={cn(
                    "flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                    mode === "desktop"
                      ? "bg-white text-gray-800 shadow-sm"
                      : "text-gray-500 hover:text-gray-700",
                  )}
                >
                  <Monitor size={13} />
                  Desktop
                </button>
                <button
                  onClick={() => setMode("mobile")}
                  title="Mobile view"
                  className={cn(
                    "flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                    mode === "mobile"
                      ? "bg-white text-gray-800 shadow-sm"
                      : "text-gray-500 hover:text-gray-700",
                  )}
                >
                  <Smartphone size={13} />
                  Mobile
                </button>
              </div>

              <button
                onClick={() => setOpen(false)}
                className="rounded px-3 py-1 text-xs font-medium bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors"
              >
                Close
              </button>
            </div>

            {/* Preview area */}
            <div className={cn(
              "flex-1 overflow-auto",
              mode === "mobile" ? "flex items-center justify-center bg-gray-200" : "",
            )}>
              {mode === "desktop" ? (
                <iframe
                  srcDoc={source}
                  sandbox="allow-scripts"
                  className="w-full h-full border-0"
                  title="HTML Preview — Desktop"
                />
              ) : (
                /* Phone frame */
                <div className="relative flex flex-col rounded-[2.5rem] border-[6px] border-gray-800 bg-gray-800 shadow-2xl"
                  style={{ width: 390, height: 720 }}
                >
                  {/* Notch */}
                  <div className="absolute top-0 left-1/2 -translate-x-1/2 w-24 h-5 bg-gray-800 rounded-b-2xl z-10" />
                  <iframe
                    srcDoc={source}
                    sandbox="allow-scripts"
                    className="flex-1 w-full border-0 rounded-[2rem]"
                    title="HTML Preview — Mobile"
                  />
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function LatexBlock({ source }: { source: string }) {
  let html = "";
  let err = false;
  try {
    html = katex.renderToString(source.trim(), { displayMode: true, throwOnError: true });
  } catch {
    err = true;
  }

  if (err) {
    return (
      <pre className="overflow-x-auto rounded-lg bg-gray-950 px-4 py-3 text-xs text-gray-100 my-2">
        <code>{source}</code>
      </pre>
    );
  }

  return (
    <div
      className="overflow-x-auto py-3 text-center"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}

export function MarkdownContent({ content, className, compact }: MarkdownContentProps) {
  const wrapperClass = compact
    ? cn(
        "text-xs leading-relaxed",
        // paragraphs / lists — tight spacing
        "[&_p]:my-1 [&_ul]:my-1 [&_ol]:my-1 [&_li]:my-0.5 [&_ul]:pl-4 [&_ol]:pl-4",
        "[&_ul]:list-disc [&_ol]:list-decimal",
        // headings — small & bold, no giant margins
        "[&_h1]:text-sm [&_h1]:font-bold [&_h1]:my-1",
        "[&_h2]:text-xs [&_h2]:font-bold [&_h2]:my-1",
        "[&_h3]:text-xs [&_h3]:font-semibold [&_h3]:my-0.5",
        "[&_h4]:text-xs [&_h4]:font-semibold [&_h4]:my-0.5",
        // bold / italic just inherit the browser defaults
        className,
      )
    : cn("prose prose-sm dark:prose-invert max-w-none", className);

  return (
    <div className={wrapperClass}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={compact ? [rehypeKatex] : [rehypeKatex, rehypeRaw]}
        components={{
          code({ className: cls, children, ...props }) {
            const lang = cls?.replace("language-", "") ?? "";
            const src = String(children).replace(/\n$/, "");

            if (lang === "html") return <HtmlPreview source={src} />;
            if (lang === "latex") return <LatexBlock source={src} />;

            if (lang) {
              return (
                <pre className="overflow-x-auto rounded-md bg-gray-950 px-3 py-2 text-[11px] text-gray-100 my-1.5">
                  <code className={cls} {...props}>
                    {children}
                  </code>
                </pre>
              );
            }
            return (
              <code
                className={cn(
                  "rounded font-mono text-[0.85em]",
                  compact
                    ? "bg-black/10 dark:bg-white/10 px-1 py-0.5"
                    : "bg-gray-100 dark:bg-gray-800 px-1 py-0.5",
                )}
                {...props}
              >
                {children}
              </code>
            );
          },
          table({ children }) {
            return (
              <div className={cn("overflow-x-auto", compact ? "my-1 max-w-full" : "my-2")}>
                <table className={cn("border-collapse", compact ? "text-[11px] w-full" : "w-full text-sm")}>
                  {children}
                </table>
              </div>
            );
          },
          th({ children }) {
            return (
              <th className={cn(
                "border border-[--color-border] px-2 py-1 text-left font-semibold",
                !compact && "bg-[--color-surface-raised]",
              )}>
                {children}
              </th>
            );
          },
          td({ children }) {
            return (
              <td className="border border-[--color-border] px-2 py-1">{children}</td>
            );
          },
          blockquote({ children }) {
            return (
              <blockquote className={cn(
                "border-l-2 pl-3 italic text-[--color-muted]",
                compact ? "my-1 border-[--color-border]" : "my-2 border-l-4 border-[--color-border]",
              )}>
                {children}
              </blockquote>
            );
          },
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
