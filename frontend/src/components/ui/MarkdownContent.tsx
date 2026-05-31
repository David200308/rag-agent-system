"use client";

import { useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import katex from "katex";
import { Download, Expand, Image, Monitor, Shrink, Smartphone } from "lucide-react";
import { cn } from "@/lib/utils";

interface MarkdownContentProps {
  content: string;
  className?: string;
  /** Skip Tailwind Typography prose — use compact flat styling for dense contexts (log viewer, etc.) */
  compact?: boolean;
}

type PreviewMode = "desktop" | "mobile";

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function HtmlPreview({ source }: { source: string }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<PreviewMode>("desktop");
  const [fullscreen, setFullscreen] = useState(false);
  const [toImgBusy, setToImgBusy] = useState(false);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  function handleDownload() {
    const blob = new Blob([source], { type: "text/html" });
    downloadBlob(blob, "preview.html");
  }

  async function handleToImage() {
    if (!iframeRef.current?.contentDocument?.body) return;
    setToImgBusy(true);
    try {
      const html2canvas = (await import("html2canvas")).default;
      const canvas = await html2canvas(iframeRef.current.contentDocument.body, {
        useCORS: true,
        allowTaint: true,
        backgroundColor: "#ffffff",
      });
      canvas.toBlob(blob => {
        if (blob) downloadBlob(blob, "preview.png");
      }, "image/png");
    } finally {
      setToImgBusy(false);
    }
  }

  const modalStyle = fullscreen
    ? { width: "100vw", height: "100vh", borderRadius: 0 }
    : { width: "min(90vw, 1100px)", height: "min(90vh, 800px)" };

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
          <div
            className="relative flex flex-col bg-white shadow-2xl overflow-hidden transition-all"
            style={{ ...modalStyle, borderRadius: fullscreen ? 0 : "0.75rem" }}
          >
            {/* Modal toolbar */}
            <div className="flex shrink-0 items-center gap-1.5 bg-gray-100 border-b border-gray-200 px-3 py-2">
              <span className="hidden sm:block text-xs font-semibold text-gray-600 uppercase tracking-wide shrink-0">
                HTML Preview
              </span>

              {/* Desktop / Mobile toggle */}
              <div className="flex items-center gap-0.5 rounded-lg bg-gray-200 p-0.5 shrink-0">
                <button
                  onClick={() => setMode("desktop")}
                  title="Desktop view"
                  className={cn(
                    "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                    mode === "desktop"
                      ? "bg-white text-gray-800 shadow-sm"
                      : "text-gray-500 hover:text-gray-700",
                  )}
                >
                  <Monitor size={13} />
                  <span className="hidden sm:inline">Desktop</span>
                </button>
                <button
                  onClick={() => setMode("mobile")}
                  title="Mobile view"
                  className={cn(
                    "flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
                    mode === "mobile"
                      ? "bg-white text-gray-800 shadow-sm"
                      : "text-gray-500 hover:text-gray-700",
                  )}
                >
                  <Smartphone size={13} />
                  <span className="hidden sm:inline">Mobile</span>
                </button>
              </div>

              {/* Action buttons */}
              <div className="ml-auto flex items-center gap-0.5">
                <button
                  onClick={handleDownload}
                  title="Download HTML"
                  className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200 transition-colors"
                >
                  <Download size={13} />
                  <span className="hidden sm:inline">Download</span>
                </button>
                <button
                  onClick={handleToImage}
                  disabled={toImgBusy}
                  title="Save as PNG image"
                  className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200 transition-colors disabled:opacity-50"
                >
                  <Image size={13} />
                  <span className="hidden sm:inline">{toImgBusy ? "Saving…" : "To Image"}</span>
                </button>
                <button
                  onClick={() => setFullscreen(f => !f)}
                  title={fullscreen ? "Exit fullscreen" : "Fullscreen"}
                  className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-200 transition-colors"
                >
                  {fullscreen ? <Shrink size={13} /> : <Expand size={13} />}
                  <span className="hidden sm:inline">{fullscreen ? "Exit" : "Fullscreen"}</span>
                </button>
                <button
                  onClick={() => setOpen(false)}
                  title="Close"
                  className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors"
                >
                  <span className="hidden sm:inline">Close</span>
                  <span className="sm:hidden text-gray-500">✕</span>
                </button>
              </div>
            </div>

            {/* Preview area */}
            <div className={cn(
              "flex-1 overflow-auto",
              mode === "mobile" ? "flex items-center justify-center bg-gray-200" : "",
            )}>
              {mode === "desktop" ? (
                <iframe
                  ref={iframeRef}
                  srcDoc={source}
                  sandbox="allow-scripts"
                  className="w-full h-full border-0"
                  title="HTML Preview — Desktop"
                />
              ) : (
                /* Phone frame */
                <div
                  className="relative flex flex-col rounded-[2.5rem] border-[6px] border-gray-800 bg-gray-800 shadow-2xl"
                  style={{ width: 390, height: 720 }}
                >
                  {/* Notch */}
                  <div className="absolute top-0 left-1/2 -translate-x-1/2 w-24 h-5 bg-gray-800 rounded-b-2xl z-10" />
                  <iframe
                    ref={iframeRef}
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
