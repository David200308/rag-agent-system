import { useCallback, useState } from "react";
import {
  type CardNetwork,
  type CardType,
  type StockInvestment,
  type StockType,
} from "@/types/financial";

export type Tab = "deposits" | "stocks" | "crypto" | "cards" | "salary";
export type SortDir = "asc" | "desc";
export interface SortConfig { column: string; dir: SortDir }

// ── API helpers ───────────────────────────────────────────────────────────────

export async function apiFetch<T>(type: Tab): Promise<T[]> {
  const res = await fetch(`/api/financial?type=${type}`);
  if (!res.ok) return [];
  return res.json() as Promise<T[]>;
}

export async function apiCreate<T>(type: Tab, body: object): Promise<T> {
  const res = await fetch(`/api/financial?type=${type}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<T>;
}

export async function apiUpdate<T>(type: Tab, id: string, body: object): Promise<T> {
  const res = await fetch(`/api/financial/${id}?type=${type}`, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<T>;
}

export async function apiDelete(type: Tab, id: string): Promise<void> {
  await fetch(`/api/financial/${id}?type=${type}`, { method: "DELETE" });
}

export async function apiRefreshPrices(): Promise<void> {
  await fetch("/api/financial/prices", { method: "POST" });
}

export async function fetchExchangeRates(): Promise<Record<string, number>> {
  try {
    const res = await fetch("/api/financial/rates");
    if (!res.ok) return {};
    const data = await res.json() as { rates?: Record<string, number> };
    return data.rates ?? {};
  } catch { return {}; }
}

export async function fetchUserCurrency(): Promise<string> {
  try {
    const res = await fetch("/api/agent/user/preferences");
    if (!res.ok) return "USD";
    const data = await res.json() as { defaultCurrency?: string };
    return data.defaultCurrency ?? "USD";
  } catch { return "USD"; }
}

export async function saveUserCurrency(currency: string): Promise<void> {
  await fetch("/api/agent/user/preferences", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ defaultCurrency: currency }),
  });
}

// ── Sorting helpers ───────────────────────────────────────────────────────────

export function sortData<T extends object>(data: T[], sort: SortConfig | null): T[] {
  if (!sort) return data;
  return [...data].sort((a, b) => {
    const av = (a as Record<string, unknown>)[sort.column];
    const bv = (b as Record<string, unknown>)[sort.column];
    let cmp = 0;
    if (av == null && bv == null) cmp = 0;
    else if (av == null) cmp = 1;
    else if (bv == null) cmp = -1;
    else if (typeof av === "number" && typeof bv === "number") cmp = av - bv;
    else cmp = String(av).localeCompare(String(bv));
    return sort.dir === "asc" ? cmp : -cmp;
  });
}

export function useSort(initial?: SortConfig) {
  const [sort, setSort] = useState<SortConfig | null>(initial ?? null);
  const toggle = useCallback((col: string) => {
    setSort((prev) =>
      prev?.column === col
        ? { column: col, dir: prev.dir === "asc" ? "desc" : "asc" }
        : { column: col, dir: "asc" },
    );
  }, []);
  return { sort, toggle };
}

// ── Stock grouping (same symbol across multiple brokers) ──────────────────────

export interface StockGroup {
  symbol: string; name: string; stockType: StockType;
  rows: StockInvestment[];
  stockAmount: number; investAmount: number; fee: number; currency: string;
  avgPrice: number | null;
  currentPrice: number | null; priceCurrency: string | null;
  currentValue: number | null;
  convertedInvestAmount: number; convertedCurrentValue: number | null; convertedCurrency: string;
  pnlPercent: number | null;
}

export function groupStocksBySymbol(rows: StockInvestment[]): StockGroup[] {
  const map = new Map<string, StockInvestment[]>();
  for (const r of rows) map.set(r.symbol, [...(map.get(r.symbol) ?? []), r]);
  return Array.from(map.values()).map((group) => {
    const first = group[0]!;
    const stockAmount = group.reduce((s, g) => s + g.stockAmount, 0);
    const investAmount = group.reduce((s, g) => s + g.investAmount, 0);
    const fee = group.reduce((s, g) => s + g.fee, 0);
    const convertedInvestAmount = group.reduce((s, g) => s + g.convertedInvestAmount, 0);
    const convertedCurrentValue = group.reduce(
      (s, g) => s + (g.convertedCurrentValue ?? g.convertedInvestAmount), 0);
    const currentValue = first.currentPrice != null ? first.currentPrice * stockAmount : null;
    const pnlPercent = convertedInvestAmount > 0
      ? Math.round((convertedCurrentValue - convertedInvestAmount) / convertedInvestAmount * 10000) / 100
      : null;
    return {
      symbol: first.symbol, name: first.name, stockType: first.stockType, rows: group,
      stockAmount, investAmount, fee, currency: first.currency,
      avgPrice: stockAmount > 0 ? (investAmount + fee) / stockAmount : null,
      currentPrice: first.currentPrice, priceCurrency: first.priceCurrency, currentValue,
      convertedInvestAmount, convertedCurrentValue, convertedCurrency: first.convertedCurrency,
      pnlPercent,
    };
  });
}

// ── Misc formatting / lookup helpers ───────────────────────────────────────────

export function unique(arr: string[]): string[] {
  return [...new Set(arr.filter(Boolean))].sort();
}

export function formatPercentOfTotal(part: number, total: number): string {
  if (total === 0) return "0.0%";
  return (part / total * 100).toFixed(1) + "%";
}

export function formatExpiry(dateStr: string): string {
  if (!dateStr) return "—";
  const [year, month] = dateStr.split("-");
  return `${month}/${year}`;
}

// ── TradingView symbol mapping (best-effort; we don't store exchange data) ────

export function toTradingViewSymbol(symbol: string, kind: "crypto" | "stock", stockType?: StockType): string {
  const sym = symbol.trim().toUpperCase();
  if (kind === "crypto") {
    // Matches our backend's price source (Hyperliquid perps), quoted in USDC (e.g. "HYPERLIQUID:BTCUSDC").
    const base = sym.replace(/(USDT|USDC|USD)$/, "");
    return `HYPERLIQUID:${base}USDC`;
  }
  switch (stockType) {
    case "HK_STOCK": return `HKEX:${sym.replace(/^0+/, "").padStart(4, "0")}`;
    case "SG_STOCK": return `SGX:${sym}`;
    case "CN_STOCK": return `SSE:${sym}`;
    default:         return sym; // US_STOCK / OTHER: let TradingView resolve the primary listing
  }
}

export const NETWORK_COLORS: Record<CardNetwork, string> = {
  Visa:      "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300",
  Mastercard:"bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300",
  UnionPay:  "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300",
  JCB:       "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300",
  AMEX:      "bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300",
};

export const TYPE_COLORS: Record<CardType, string> = {
  Credit: "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300",
  Debit:  "bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-300",
  ATM:    "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300",
};

export const inputCls =
  "w-full rounded-md border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30";
export const selectCls =
  "w-full rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 px-3 py-1.5 text-sm outline-none " +
  "focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30 cursor-pointer appearance-none";
