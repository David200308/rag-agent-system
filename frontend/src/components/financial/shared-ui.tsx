"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, ChevronUp, ChevronsUpDown } from "lucide-react";
import { type SortConfig, inputCls } from "./utils";
import { formatAmount } from "@/types/financial";

// ── Sort header cell ──────────────────────────────────────────────────────────

export function Th({ label, column, sort, onSort, right }: {
  label: string; column: string; sort: SortConfig | null;
  onSort: (c: string) => void; right?: boolean;
}) {
  const active = sort?.column === column;
  const Icon = active ? (sort!.dir === "asc" ? ChevronUp : ChevronDown) : ChevronsUpDown;
  return (
    <th
      className={`cursor-pointer select-none px-4 py-2.5 font-medium hover:text-inherit ${right ? "text-right" : "text-left"}`}
      onClick={() => onSort(column)}
    >
      <span className={`inline-flex items-center gap-0.5 ${right ? "flex-row-reverse" : ""}`}>
        {label}
        <Icon className={`h-3 w-3 ${active ? "opacity-100" : "opacity-30"}`} />
      </span>
    </th>
  );
}

// ── Combobox input ────────────────────────────────────────────────────────────

export function ComboInput({ value, onChange, suggestions, placeholder, required }: {
  value: string; onChange: (v: string) => void;
  suggestions: string[]; placeholder?: string; required?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const filtered = suggestions.filter(
    (s) => s.toLowerCase().includes(value.toLowerCase()) && s !== value,
  );
  useEffect(() => {
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);
  return (
    <div ref={ref} className="relative">
      <input className={inputCls} value={value} required={required} placeholder={placeholder}
        autoComplete="off"
        onChange={(e) => { onChange(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)} />
      {open && filtered.length > 0 && (
        <ul className="absolute z-50 mt-1 w-full rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 py-1 shadow-lg">
          {filtered.map((s) => (
            <li key={s} className="cursor-pointer px-3 py-1.5 text-sm hover:bg-[--color-border]/50"
              onMouseDown={(e) => { e.preventDefault(); onChange(s); setOpen(false); }}>
              {s}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── Modal ─────────────────────────────────────────────────────────────────────

export function Modal({ title, onClose, children }: {
  title: string; onClose: () => void; children: React.ReactNode;
}) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => { setMounted(true); }, []);
  if (!mounted) return null;
  return createPortal(
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/20 backdrop-blur-sm p-4">
      <div className="w-full max-w-md rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold">{title}</h2>
          <button onClick={onClose}
            className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50">✕</button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-[--color-muted]">{label}</label>
      {children}
    </div>
  );
}

// ── Symbol icon (stock/crypto logo with fallback initial) ──────────────────────

export function SymbolIcon({ logoUrl, symbol }: { logoUrl: string | null; symbol: string }) {
  const [failed, setFailed] = useState(false);
  if (!logoUrl || failed) {
    return (
      <span className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[--color-border]/40 text-[9px] font-semibold text-[--color-muted]">
        {symbol.slice(0, 1)}
      </span>
    );
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={logoUrl}
      alt=""
      className="h-5 w-5 shrink-0 rounded-full bg-white object-contain"
      onError={() => setFailed(true)}
    />
  );
}

// ── P&L badge ─────────────────────────────────────────────────────────────────

export function PnlBadge({ percent, amount, currency, hide }: {
  percent: number; amount?: number | null; currency?: string; hide?: boolean;
}) {
  const isPositive = percent >= 0;
  return (
    <span className={`text-xs font-medium ${isPositive ? "text-green-500" : "text-red-500"}`}>
      {isPositive ? "+" : ""}{percent.toFixed(2)}%
      {amount != null && currency && (
        <span className="ml-1 opacity-80">
          ({hide ? "***" : `${isPositive ? "+" : ""}${formatAmount(amount, currency)}`})
        </span>
      )}
    </span>
  );
}

// ── Summary card ──────────────────────────────────────────────────────────────

export function SummaryCard({ label, value, currency, pnlPercent, pnlAmount, share, usdValue, usdLabel, hide }: {
  label: string; value: number; currency: string; pnlPercent?: number | null; pnlAmount?: number | null; share?: number;
  /** Secondary USD line under the main value. Unlabeled it reads as "same total, in USD" (Deposits/Stocks/Crypto);
   *  pass `usdLabel` when it represents a different quantity instead (e.g. Futures' actual margin, not notional). */
  usdValue?: number | null; usdLabel?: string; hide?: boolean;
}) {
  return (
    <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-4 min-w-0">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-[--color-muted]">{label}</p>
        {share != null && (
          <span className="text-xs font-medium text-[--color-muted] tabular-nums">{share.toFixed(1)}%</span>
        )}
      </div>
      <p className="mt-1 text-lg font-semibold tabular-nums">{hide ? "***" : formatAmount(value, currency)}</p>
      {usdValue != null && (
        <p className="mt-0.5 text-xs text-[--color-muted] tabular-nums">
          {usdLabel && <span>{usdLabel}: </span>}{hide ? "***" : formatAmount(usdValue, "USD")}
        </p>
      )}
      {pnlPercent != null && (
        <p className="mt-0.5"><PnlBadge percent={pnlPercent} amount={pnlAmount} currency={currency} hide={hide} /></p>
      )}
    </div>
  );
}

// ── Download modal building blocks ────────────────────────────────────────────

export function SegBtn({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button onClick={onClick}
      className={`flex-1 rounded-md py-1.5 text-xs font-medium transition-colors ${
        active
          ? "bg-black text-white dark:bg-white dark:text-black"
          : "text-[--color-muted] hover:bg-[--color-border]/50"
      }`}>
      {label}
    </button>
  );
}

export function SwitchRow({ label, checked, onChange }: { label: string; checked: boolean; onChange: () => void }) {
  return (
    <div className="flex items-center justify-between py-2.5">
      <span className="text-sm">{label}</span>
      <button role="switch" aria-checked={checked} onClick={onChange}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-colors ${
          checked ? "bg-black dark:bg-white" : "bg-neutral-300 dark:bg-neutral-600"
        }`}>
        <span className={`inline-block h-5 w-5 transform rounded-full shadow transition-transform duration-200 ${
          checked ? "translate-x-[22px] bg-white dark:bg-black" : "translate-x-0.5 bg-white"
        }`} />
      </button>
    </div>
  );
}
