"use client";

import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import katex from "katex";
import { cn } from "@/lib/utils";

interface MarkdownContentProps {
  content: string;
  className?: string;
}

function HtmlPreview({ source }: { source: string }) {
  const [preview, setPreview] = useState(false);

  function autoResize(iframe: HTMLIFrameElement | null) {
    if (!iframe) return;
    iframe.onload = () => {
      try {
        const h = iframe.contentDocument?.body?.scrollHeight;
        if (h) iframe.style.height = `${h + 24}px`;
      } catch {
        // cross-origin or sandboxed — leave default height
      }
    };
  }

  return (
    <div className="rounded-lg border border-[--color-border] overflow-hidden my-2">
      <div className="flex items-center justify-between bg-gray-950 px-4 py-1.5">
        <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide">HTML</span>
        <button
          onClick={() => setPreview(v => !v)}
          className="text-[11px] text-blue-400 hover:text-blue-300 font-medium transition-colors"
        >
          {preview ? "Source" : "Preview"}
        </button>
      </div>
      {preview ? (
        <iframe
          ref={autoResize}
          srcDoc={source}
          sandbox="allow-scripts"
          className="w-full border-0 bg-white"
          style={{ minHeight: "180px" }}
          title="HTML Preview"
        />
      ) : (
        <pre className="overflow-x-auto px-4 py-3 text-xs text-gray-100 bg-gray-950 m-0">
          <code className="language-html">{source}</code>
        </pre>
      )}
    </div>
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

export function MarkdownContent({ content, className }: MarkdownContentProps) {
  return (
    <div className={cn("prose prose-sm dark:prose-invert max-w-none", className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex, rehypeRaw]}
        components={{
          code({ className: cls, children, ...props }) {
            const lang = cls?.replace("language-", "") ?? "";
            const src = String(children).replace(/\n$/, "");

            if (lang === "html") return <HtmlPreview source={src} />;
            if (lang === "latex") return <LatexBlock source={src} />;

            if (lang) {
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
          blockquote({ children }) {
            return (
              <blockquote className="border-l-4 border-[--color-border] pl-4 italic text-[--color-muted]">
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
