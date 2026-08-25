"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTheme } from "@/hooks/useTheme";

const WIDGET_SRC = "https://s3.tradingview.com/external-embedding/embed-widget-mini-symbol-overview.js";
const CARD_W = 340;
const CARD_H = 220;
const OPEN_DELAY_MS = 200;

export function SymbolHoverChart({ tvSymbol, children }: {
  tvSymbol: string | null; children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLSpanElement>(null);
  const hostRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { theme } = useTheme();

  const show = () => {
    if (!tvSymbol) return;
    timerRef.current = setTimeout(() => {
      const rect = triggerRef.current?.getBoundingClientRect();
      if (!rect) return;
      let left = rect.left;
      let top = rect.bottom + 6;
      if (left + CARD_W > window.innerWidth - 8) left = window.innerWidth - CARD_W - 8;
      if (top + CARD_H > window.innerHeight - 8) top = rect.top - CARD_H - 6;
      setPos({ top, left });
      setOpen(true);
    }, OPEN_DELAY_MS);
  };

  const hide = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setOpen(false);
  };

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  useEffect(() => {
    if (!open || !tvSymbol) return;
    const host = hostRef.current;
    if (!host) return;
    host.innerHTML = "";
    const widgetDiv = document.createElement("div");
    widgetDiv.className = "tradingview-widget-container__widget";
    host.appendChild(widgetDiv);
    const script = document.createElement("script");
    script.src = WIDGET_SRC;
    script.type = "text/javascript";
    script.async = true;
    script.innerHTML = JSON.stringify({
      symbol: tvSymbol,
      width: "100%",
      height: "100%",
      locale: "en",
      dateRange: "1M",
      colorTheme: theme,
      isTransparent: false,
      autosize: true,
      largeChartUrl: "",
    });
    host.appendChild(script);
  }, [open, tvSymbol, theme]);

  return (
    <>
      <span ref={triggerRef} onMouseEnter={show} onMouseLeave={hide}
        className={tvSymbol ? "cursor-help" : undefined}>
        {children}
      </span>
      {open && pos && tvSymbol && createPortal(
        <div
          className="fixed z-[9998] overflow-hidden rounded-lg border border-[--color-border] bg-[--color-surface-raised] shadow-xl"
          style={{ top: pos.top, left: pos.left, width: CARD_W, height: CARD_H }}
          onMouseEnter={() => { if (timerRef.current) clearTimeout(timerRef.current); }}
          onMouseLeave={hide}
        >
          <div ref={hostRef} className="tradingview-widget-container h-full w-full" />
        </div>,
        document.body,
      )}
    </>
  );
}
