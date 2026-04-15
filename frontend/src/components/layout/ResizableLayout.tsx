"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";

const MIN_WIDTH     = 180;
const MAX_WIDTH     = 480;
const DEFAULT_WIDTH = 256;
const WIDTH_KEY     = "rag-sidebar-width";
const COLLAPSED_KEY = "rag-sidebar-collapsed";

interface ResizableLayoutProps {
  /**
   * Render prop that receives:
   *  - width       current sidebar pixel width (for desktop)
   *  - onCollapse  call to fold the sidebar
   */
  sidebar: (width: number, onCollapse: () => void) => React.ReactNode;
  children: React.ReactNode;
}

/**
 * Top-level layout with a draggable + collapsible sidebar divider (desktop only).
 *
 * - Drag the divider to resize freely between 180 – 480 px.
 * - Click the collapse button in the sidebar header to fold it to a thin strip.
 * - Click the strip (ChevronRight) to expand again.
 * - Both width and collapsed state are persisted to localStorage.
 * - On mobile the sidebar uses its own fixed overlay; the divider is hidden.
 */
export function ResizableLayout({ sidebar, children }: ResizableLayoutProps) {
  const [width,     setWidth]     = useState(DEFAULT_WIDTH);
  const [collapsed, setCollapsed] = useState(false);
  const [dragging,  setDragging]  = useState(false);

  // Ref so event-handler closures always read the latest width without needing
  // to be torn down and re-registered on every mouse-move update.
  const widthRef  = useRef(DEFAULT_WIDTH);
  const startRef  = useRef<{ x: number; w: number } | null>(null);
  widthRef.current = width;

  // ── Restore from localStorage (client-only) ──────────────────────────────
  useEffect(() => {
    const storedW = localStorage.getItem(WIDTH_KEY);
    if (storedW) {
      const n = parseInt(storedW, 10);
      if (n >= MIN_WIDTH && n <= MAX_WIDTH) {
        setWidth(n);
        widthRef.current = n;
      }
    }
    if (localStorage.getItem(COLLAPSED_KEY) === "true") setCollapsed(true);
  }, []);

  // ── Collapse / expand ────────────────────────────────────────────────────
  const collapse = useCallback(() => {
    setCollapsed(true);
    localStorage.setItem(COLLAPSED_KEY, "true");
  }, []);

  const expand = useCallback(() => {
    setCollapsed(false);
    localStorage.setItem(COLLAPSED_KEY, "false");
  }, []);

  // ── Drag-to-resize ───────────────────────────────────────────────────────
  const startDrag = useCallback((clientX: number) => {
    startRef.current = { x: clientX, w: widthRef.current };
    setDragging(true);
  }, []);

  useEffect(() => {
    if (!dragging) return;

    const move = (clientX: number) => {
      if (!startRef.current) return;
      const next = Math.min(
        MAX_WIDTH,
        Math.max(MIN_WIDTH, startRef.current.w + (clientX - startRef.current.x)),
      );
      setWidth(next);
    };

    const stop = () => {
      setDragging(false);
      localStorage.setItem(WIDTH_KEY, String(widthRef.current));
      startRef.current = null;
    };

    const onMouseMove = (e: MouseEvent)  => move(e.clientX);
    const onTouchMove = (e: TouchEvent)  => { if (e.touches[0]) move(e.touches[0].clientX); };

    window.addEventListener("mousemove",  onMouseMove);
    window.addEventListener("mouseup",    stop);
    window.addEventListener("touchmove",  onTouchMove, { passive: true });
    window.addEventListener("touchend",   stop);
    return () => {
      window.removeEventListener("mousemove",  onMouseMove);
      window.removeEventListener("mouseup",    stop);
      window.removeEventListener("touchmove",  onTouchMove);
      window.removeEventListener("touchend",   stop);
    };
  }, [dragging]);

  return (
    <div
      className={cn(
        "flex h-screen overflow-hidden",
        dragging && "select-none cursor-col-resize",
      )}
    >
      {/* ── Sidebar ────────────────────────────────────────────────────────
          Always in the DOM so the mobile fixed-overlay still works.
          Hidden on desktop via sm:hidden when collapsed.              */}
      <div className={cn(collapsed && "sm:hidden")}>
        {sidebar(width, collapse)}
      </div>

      {/* ── Collapsed strip — desktop only ─────────────────────────────── */}
      {collapsed && (
        <button
          type="button"
          onClick={expand}
          title="Expand sidebar"
          className="hidden sm:flex w-8 shrink-0 flex-col items-center justify-center
                     border-r border-[--color-border] bg-[--color-surface-raised]
                     text-[--color-muted] transition-colors
                     hover:bg-[--color-border]/50 hover:text-inherit"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      )}

      {/* ── Resize handle — desktop only, only when expanded ───────────── */}
      {!collapsed && (
        <div
          className="group relative hidden w-1 shrink-0 cursor-col-resize sm:block"
          onMouseDown={(e) => { e.preventDefault(); startDrag(e.clientX); }}
          onTouchStart={(e) => { if (e.touches[0]) startDrag(e.touches[0].clientX); }}
          aria-hidden
        >
          <div
            className={cn(
              "absolute inset-y-0 left-1/2 w-0.5 -translate-x-1/2 rounded-full transition-colors duration-150",
              dragging
                ? "bg-blue-500"
                : "bg-[--color-border] group-hover:bg-blue-400",
            )}
          />
        </div>
      )}

      {/* ── Main content ────────────────────────────────────────────────── */}
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        {children}
      </div>
    </div>
  );
}
