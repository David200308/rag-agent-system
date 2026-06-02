"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Plus, Pencil, Trash2, RefreshCw, ChevronDown, ChevronUp, ChevronsUpDown, Download, Eye, EyeOff, Search } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  CURRENCIES,
  DEPOSIT_TYPES,
  STOCK_TYPES,
  STOCK_TYPE_LABELS,
  CARD_TYPES,
  CARD_NETWORKS,
  type CashDeposit,
  type CryptoInvestment,
  type Currency,
  type StockInvestment,
  type Card,
  type CardType,
  type CardNetwork,
  type SalaryUsageRecord,
  formatAmount,
  formatPrice,
} from "@/types/financial";

type Tab = "deposits" | "stocks" | "crypto" | "cards" | "salary";
type SortDir = "asc" | "desc";
interface SortConfig { column: string; dir: SortDir }

// ── API helpers ───────────────────────────────────────────────────────────────

async function apiFetch<T>(type: Tab): Promise<T[]> {
  const res = await fetch(`/api/financial?type=${type}`);
  if (!res.ok) return [];
  return res.json() as Promise<T[]>;
}

async function apiCreate<T>(type: Tab, body: object): Promise<T> {
  const res = await fetch(`/api/financial?type=${type}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<T>;
}

async function apiUpdate<T>(type: Tab, id: string, body: object): Promise<T> {
  const res = await fetch(`/api/financial/${id}?type=${type}`, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<T>;
}

async function apiDelete(type: Tab, id: string): Promise<void> {
  await fetch(`/api/financial/${id}?type=${type}`, { method: "DELETE" });
}

async function apiRefreshPrices(): Promise<void> {
  await fetch("/api/financial/prices", { method: "POST" });
}

async function fetchExchangeRates(): Promise<Record<string, number>> {
  try {
    const res = await fetch("/api/financial/rates");
    if (!res.ok) return {};
    const data = await res.json() as { rates?: Record<string, number> };
    return data.rates ?? {};
  } catch { return {}; }
}

async function fetchUserCurrency(): Promise<string> {
  try {
    const res = await fetch("/api/agent/user/preferences");
    if (!res.ok) return "USD";
    const data = await res.json() as { defaultCurrency?: string };
    return data.defaultCurrency ?? "USD";
  } catch { return "USD"; }
}

async function saveUserCurrency(currency: string): Promise<void> {
  await fetch("/api/agent/user/preferences", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ defaultCurrency: currency }),
  });
}

// ── Sorting helpers ───────────────────────────────────────────────────────────

function sortData<T extends object>(data: T[], sort: SortConfig | null): T[] {
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

function useSort(initial?: SortConfig) {
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

// ── Sort header cell ──────────────────────────────────────────────────────────

function Th({ label, column, sort, onSort, right }: {
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

const inputCls =
  "w-full rounded-md border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30";
const selectCls =
  "w-full rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 px-3 py-1.5 text-sm outline-none " +
  "focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30 cursor-pointer appearance-none";

function ComboInput({ value, onChange, suggestions, placeholder, required }: {
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

function Modal({ title, onClose, children }: {
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-[--color-muted]">{label}</label>
      {children}
    </div>
  );
}

// ── Forms ─────────────────────────────────────────────────────────────────────

type DepositFields = Omit<CashDeposit, "id"|"ownerEmail"|"convertedAmount"|"convertedCurrency"|"createdAt"|"updatedAt">;
type StockFields   = Omit<StockInvestment, "id"|"ownerEmail"|"currentPrice"|"priceCurrency"|"currentValue"|"convertedInvestAmount"|"convertedCurrentValue"|"convertedCurrency"|"pnlPercent"|"createdAt"|"updatedAt">;
type CryptoFields  = Omit<CryptoInvestment, "id"|"ownerEmail"|"currentPrice"|"currentValue"|"convertedInvestAmount"|"convertedCurrentValue"|"convertedCurrency"|"pnlPercent"|"createdAt"|"updatedAt">;
type CardFields    = Omit<Card, "id"|"ownerEmail"|"createdAt"|"updatedAt">;
type SalaryFields  = Omit<SalaryUsageRecord, "id"|"ownerEmail"|"totalExpense"|"createdAt"|"updatedAt">;

const emptyDeposit = (): DepositFields => ({ platform:"", platformType:"", countryRegion:"", depositType:"FIXED", currency:"USD", amount:0 });
const emptyStock   = (): StockFields   => ({ broker:"", stockType:"US_STOCK", symbol:"", name:"", stockAmount:0, investAmount:0, currency:"USD", fee:0 });
const emptyCrypto  = (): CryptoFields  => ({ name:"", symbol:"", amount:0, investAmount:0, currency:"USD" });
const emptyCard    = (): CardFields    => ({ bank:"", countryRegion:"", types:[], cardName:"", network:"Visa", expireDate:"", creditLimit:null, creditLimitCurrency:"HKD", sharedCredit:null });
const emptySalary  = (): SalaryFields  => {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1, region: "", currency: "HKD", salary: 0, bonus: 0, retirementSavingEmployee: 0, retirementSavingEmployer: 0, tax: 0, houseRent: 0, livingExpense: 0, otherExpense: 0 };
};

const NETWORK_COLORS: Record<CardNetwork, string> = {
  Visa:      "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300",
  Mastercard:"bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300",
  UnionPay:  "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300",
  JCB:       "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300",
  AMEX:      "bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300",
};

const TYPE_COLORS: Record<CardType, string> = {
  Credit: "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300",
  Debit:  "bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-300",
  ATM:    "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300",
};

function formatExpiry(dateStr: string): string {
  if (!dateStr) return "—";
  const [year, month] = dateStr.split("-");
  return `${month}/${year}`;
}

function DepositForm({ initial, suggestions, onSave, onCancel, saving }: {
  initial: DepositFields;
  suggestions: { platforms: string[]; platformTypes: string[]; countries: string[] };
  onSave: (d: DepositFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const s = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <Field label="Platform *">
        <ComboInput value={f.platform} onChange={(v) => s("platform", v)}
          suggestions={suggestions.platforms} placeholder="e.g. HSBC, DBS" required />
      </Field>
      <Field label="Platform Type *">
        <ComboInput value={f.platformType} onChange={(v) => s("platformType", v)}
          suggestions={suggestions.platformTypes} placeholder="e.g. Bank, Brokerage" required />
      </Field>
      <Field label="Country / Region">
        <ComboInput value={f.countryRegion} onChange={(v) => s("countryRegion", v)}
          suggestions={suggestions.countries} placeholder="e.g. Hong Kong SAR, Singapore" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Fixed / Flex">
          <select className={selectCls} value={f.depositType} onChange={(e) => s("depositType", e.target.value)}>
            {DEPOSIT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => s("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Amount *">
        <input className={inputCls} type="number" required min="0" step="0.01"
          value={f.amount} onChange={(e) => s("amount", parseFloat(e.target.value) || 0)} />
      </Field>
      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

function StockForm({ initial, brokers, onSave, onCancel, saving }: {
  initial: StockFields; brokers: string[];
  onSave: (d: StockFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const s = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <Field label="Broker *">
        <ComboInput value={f.broker} onChange={(v) => s("broker", v)}
          suggestions={brokers} placeholder="e.g. Interactive Brokers, Futu" required />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Type">
          <select className={selectCls} value={f.stockType} onChange={(e) => s("stockType", e.target.value)}>
            {STOCK_TYPES.map((t) => <option key={t} value={t}>{STOCK_TYPE_LABELS[t]}</option>)}
          </select>
        </Field>
        <Field label="Symbol *">
          <input className={inputCls} required value={f.symbol}
            onChange={(e) => s("symbol", e.target.value.toUpperCase())}
            placeholder="e.g. AAPL, 0700.HK" />
        </Field>
      </div>
      <Field label="Name *">
        <input className={inputCls} required value={f.name}
          onChange={(e) => s("name", e.target.value)} placeholder="e.g. Apple Inc." />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Shares *">
          <input className={inputCls} type="number" required min="0" step="0.0001"
            value={f.stockAmount} onChange={(e) => s("stockAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => s("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Invest Amount *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={f.investAmount} onChange={(e) => s("investAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Fee">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.fee} onChange={(e) => s("fee", parseFloat(e.target.value) || 0)} />
        </Field>
      </div>
      <p className="text-[11px] text-[--color-muted]">
        Use Finnhub ticker format: AAPL (US), 0700.HK (HK), 600519.SS (CN), D05.SI (SG)
      </p>
      <div className="mt-1 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

function CryptoForm({ initial, onSave, onCancel, saving }: {
  initial: CryptoFields;
  onSave: (d: CryptoFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const s = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Name *">
          <input className={inputCls} required value={f.name}
            onChange={(e) => s("name", e.target.value)} placeholder="e.g. Bitcoin" />
        </Field>
        <Field label="Symbol *">
          <input className={inputCls} required value={f.symbol}
            onChange={(e) => s("symbol", e.target.value.toUpperCase())} placeholder="e.g. BTC" />
        </Field>
      </div>
      <Field label="Amount (coins) *">
        <input className={inputCls} type="number" required min="0" step="0.00000001"
          value={f.amount} onChange={(e) => s("amount", parseFloat(e.target.value) || 0)} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Invest Amount *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={f.investAmount} onChange={(e) => s("investAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => s("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <p className="text-[11px] text-[--color-muted]">
        Symbol must match Hyperliquid perp asset name (BTC, ETH, SOL…)
      </p>
      <div className="mt-1 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

function CardForm({ initial, banks, onSave, onCancel, saving }: {
  initial: CardFields; banks: string[];
  onSave: (d: CardFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const s = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  const isCredit = f.types.includes("Credit");

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Bank *">
          <ComboInput value={f.bank} onChange={(v) => s("bank", v)}
            suggestions={banks} placeholder="e.g. HSBC, DBS, Citi" required />
        </Field>
        <Field label="Country / Region">
          <ComboInput value={f.countryRegion} onChange={(v) => s("countryRegion", v)}
            suggestions={[]} placeholder="e.g. Hong Kong SAR, United Kingdom" />
        </Field>
      </div>
      <Field label="Card Type *">
        <div className="flex gap-5 pt-1">
          {CARD_TYPES.map((t) => (
            <label key={t} className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
              <input
                type="checkbox"
                className="rounded border-[--color-border]"
                checked={f.types.includes(t)}
                onChange={(e) => {
                  const next = e.target.checked ? [...f.types, t] : f.types.filter((x) => x !== t);
                  setF((p) => ({ ...p, types: next, sharedCredit: next.includes("Credit") ? p.sharedCredit : null }));
                }}
              />
              {t}
            </label>
          ))}
        </div>
      </Field>
      <Field label="Card Name *">
        <input className={inputCls} required value={f.cardName}
          onChange={(e) => s("cardName", e.target.value)} placeholder="e.g. Premier Mastercard" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Network">
          <select className={selectCls} value={f.network}
            onChange={(e) => s("network", e.target.value as CardNetwork)}>
            {CARD_NETWORKS.map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </Field>
        <Field label="Expiry Date">
          <input className={inputCls} type="month" value={f.expireDate}
            onChange={(e) => s("expireDate", e.target.value)} />
        </Field>
      </div>
      {isCredit && (
        <>
          <Field label="Credit Limit">
            <div className="flex gap-2">
              <select
                className="w-20 shrink-0 rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 px-2 py-1.5 text-sm outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30 cursor-pointer"
                value={f.creditLimitCurrency ?? "HKD"}
                onChange={(e) => s("creditLimitCurrency", e.target.value)}>
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
              <input className={`${inputCls} flex-1`} type="number" min="0" step="0.01"
                placeholder="Leave blank if unknown"
                value={f.creditLimit ?? ""}
                onChange={(e) => s("creditLimit", e.target.value ? parseFloat(e.target.value) : null)} />
            </div>
          </Field>
          <Field label="Shared Credit">
            <div className="flex gap-5 pt-1">
              {([true, false] as const).map((v) => (
                <label key={String(v)} className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
                  <input type="radio" name="sharedCredit" checked={f.sharedCredit === v}
                    onChange={() => s("sharedCredit", v)} />
                  {v ? "Shared" : "Dedicated"}
                </label>
              ))}
              <label className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
                <input type="radio" name="sharedCredit" checked={f.sharedCredit === null}
                  onChange={() => s("sharedCredit", null)} />
                Unknown
              </label>
            </div>
          </Field>
        </>
      )}
      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving || f.types.length === 0}>
          {saving ? "Saving…" : "Save"}
        </Button>
      </div>
    </form>
  );
}

function SalaryForm({ initial, regions, onSave, onCancel, saving }: {
  initial: SalaryFields; regions: string[];
  onSave: (d: SalaryFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const s = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  const num = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    s(k, parseFloat(e.target.value) || 0);

  const computed = f.livingExpense + f.houseRent + f.otherExpense;

  const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <div className="grid grid-cols-3 gap-3">
        <Field label="Year *">
          <input className={inputCls} type="number" required min="2000" max="2100" step="1"
            value={f.year} onChange={(e) => s("year", parseInt(e.target.value) || new Date().getFullYear())} />
        </Field>
        <Field label="Month *">
          <select className={selectCls} value={f.month} onChange={(e) => s("month", parseInt(e.target.value))}>
            {MONTHS.map((m, i) => <option key={i + 1} value={i + 1}>{i + 1} – {m}</option>)}
          </select>
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => s("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Region *">
        <ComboInput value={f.region} onChange={(v) => s("region", v)}
          suggestions={regions} placeholder="e.g. Hong Kong SAR, Singapore" required />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Salary (Excl. Retirement / Pension) *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={f.salary} onChange={num("salary")} />
        </Field>
        <Field label="Bonus">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.bonus} onChange={num("bonus")} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Retirement Savings (Employee)">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.retirementSavingEmployee} onChange={num("retirementSavingEmployee")} />
        </Field>
        <Field label="Retirement Savings (Employer)">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.retirementSavingEmployer} onChange={num("retirementSavingEmployer")} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Tax">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.tax} onChange={num("tax")} />
        </Field>
        <Field label="House Rent">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.houseRent} onChange={num("houseRent")} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Living Expense">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.livingExpense} onChange={num("livingExpense")} />
        </Field>
        <Field label="Other Expense">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.otherExpense} onChange={num("otherExpense")} />
        </Field>
      </div>
      <div className="rounded-md bg-[--color-border]/30 px-3 py-2 text-xs text-[--color-muted]">
        Total Expense (auto): <span className="font-semibold tabular-nums">
          {new Intl.NumberFormat("en-US", { style: "currency", currency: f.currency, maximumFractionDigits: 2 }).format(computed)}
        </span>
        <span className="ml-1 text-[10px]">(Living + House Rent + Other)</span>
      </div>
      <p className="text-[11px] text-[--color-muted]">
        House Rent is optional — some months may cover next month&apos;s rent.
      </p>
      <div className="mt-1 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

// ── Summary card ──────────────────────────────────────────────────────────────

function PnlBadge({ pct }: { pct: number }) {
  const pos = pct >= 0;
  return (
    <span className={`text-xs font-medium ${pos ? "text-green-500" : "text-red-500"}`}>
      {pos ? "+" : ""}{pct.toFixed(2)}%
    </span>
  );
}

function SummaryCard({ label, value, currency, pnlPercent, share, usdValue, hide }: {
  label: string; value: number; currency: string; pnlPercent?: number | null; share?: number; usdValue?: number | null; hide?: boolean;
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
        <p className="mt-0.5 text-xs text-[--color-muted] tabular-nums">{hide ? "***" : formatAmount(usdValue, "USD")}</p>
      )}
      {pnlPercent != null && (
        <p className="mt-0.5"><PnlBadge pct={pnlPercent} /></p>
      )}
    </div>
  );
}

function unique(arr: string[]): string[] {
  return [...new Set(arr.filter(Boolean))].sort();
}

// ── Export helpers ────────────────────────────────────────────────────────────

type DownloadSection = "deposits" | "stocks" | "crypto" | "cards";

const SECTION_LABELS: Record<DownloadSection, string> = {
  deposits: "Cash Deposits",
  stocks:   "Stocks",
  crypto:   "Crypto",
  cards:    "Cards",
};

function pct(part: number, total: number): string {
  if (total === 0) return "0.0%";
  return (part / total * 100).toFixed(1) + "%";
}

function buildMarkdown(
  sections: DownloadSection[],
  deposits: CashDeposit[],
  stocks: StockInvestment[],
  crypto: CryptoInvestment[],
  cards: Card[],
  currency: string,
  grandTotal: number,
): string {
  const has = (s: DownloadSection) => sections.includes(s);
  const date = new Date().toISOString().slice(0, 10);
  const lines: string[] = [`# Financial Report — ${date}`, ""];

  const financialSections = sections.filter((s) => s !== "cards");
  if (financialSections.length > 1) {
    const totalDep = has("deposits") ? deposits.reduce((s, d) => s + (d.convertedAmount ?? 0), 0) : 0;
    const totalStk = has("stocks")   ? stocks.reduce((s, st) => s + (st.convertedCurrentValue ?? st.convertedInvestAmount ?? 0), 0) : 0;
    const totalCry = has("crypto")   ? crypto.reduce((s, c) => s + (c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), 0) : 0;
    lines.push("## Summary", "");
    lines.push(`| Category | Value (${currency}) | % of Total |`);
    lines.push("|---|---:|---:|");
    if (has("deposits")) lines.push(`| Cash Deposits | ${formatAmount(totalDep, currency)} | ${pct(totalDep, grandTotal)} |`);
    if (has("stocks"))   lines.push(`| Stock Investments | ${formatAmount(totalStk, currency)} | ${pct(totalStk, grandTotal)} |`);
    if (has("crypto"))   lines.push(`| Crypto Investments | ${formatAmount(totalCry, currency)} | ${pct(totalCry, grandTotal)} |`);
    lines.push(`| **Total** | **${formatAmount(grandTotal, currency)}** | **100%** |`);
    lines.push("");
  }

  if (has("deposits") && deposits.length > 0) {
    lines.push("## Cash Deposits", "");
    lines.push(`| Platform | Type | Country | F/X | Amount | ≈ ${currency} | % of Total |`);
    lines.push("|---|---|---|---|---:|---:|---:|");
    for (const d of deposits)
      lines.push(`| ${d.platform} | ${d.platformType} | ${d.countryRegion || "—"} | ${d.depositType} | ${formatAmount(d.amount, d.currency)} | ${formatAmount(d.convertedAmount, currency)} | ${pct(d.convertedAmount ?? 0, grandTotal)} |`);
    lines.push("");
  }

  if (has("stocks") && stocks.length > 0) {
    lines.push("## Stock Investments", "");
    lines.push(`| Symbol | Name | Shares | Invested | Price | Value | ≈ ${currency} | P&L% | % of Total |`);
    lines.push("|---|---|---:|---:|---:|---:|---:|---:|---:|");
    for (const s of stocks) {
      const val = s.convertedCurrentValue ?? s.convertedInvestAmount ?? 0;
      lines.push(`| ${s.symbol} | ${s.name} | ${s.stockAmount} | ${formatAmount(s.investAmount, s.currency)} | ${s.currentPrice != null ? formatPrice(s.currentPrice) : "—"} | ${s.currentValue != null ? formatAmount(s.currentValue, s.priceCurrency ?? s.currency) : "—"} | ${formatAmount(val, currency)} | ${s.pnlPercent != null ? (s.pnlPercent >= 0 ? "+" : "") + s.pnlPercent.toFixed(2) + "%" : "—"} | ${pct(val, grandTotal)} |`);
    }
    lines.push("");
  }

  if (has("crypto") && crypto.length > 0) {
    lines.push("## Crypto Investments", "");
    lines.push(`| Symbol | Name | Amount | Invested | Price (USD) | Value (USD) | ≈ ${currency} | P&L% | % of Total |`);
    lines.push("|---|---|---:|---:|---:|---:|---:|---:|---:|");
    for (const c of crypto) {
      const val = c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0;
      lines.push(`| ${c.symbol} | ${c.name} | ${c.amount} | ${formatAmount(c.investAmount, c.currency)} | ${c.currentPrice != null ? formatAmount(c.currentPrice, "USD") : "—"} | ${c.currentValue != null ? formatAmount(c.currentValue, "USD") : "—"} | ${formatAmount(val, currency)} | ${c.pnlPercent != null ? (c.pnlPercent >= 0 ? "+" : "") + c.pnlPercent.toFixed(2) + "%" : "—"} | ${pct(val, grandTotal)} |`);
    }
    lines.push("");
  }

  if (has("cards") && cards.length > 0) {
    lines.push("## Cards", "");
    lines.push(`| Bank | Country | Card Name | Types | Network | Expiry | Credit Limit | Shared Credit |`);
    lines.push("|---|---|---|---|---|---|---:|---|");
    for (const c of cards) {
      const creditStr = c.creditLimit != null
        ? `${c.creditLimitCurrency ?? ""} ${c.creditLimit.toLocaleString("en-US", { maximumFractionDigits: 0 })}`.trim()
        : "—";
      const sharedStr = c.types.includes("Credit")
        ? (c.sharedCredit === true ? "Shared" : c.sharedCredit === false ? "Dedicated" : "—") : "—";
      lines.push(`| ${c.bank} | ${c.countryRegion || "—"} | ${c.cardName} | ${c.types.join(", ")} | ${c.network} | ${formatExpiry(c.expireDate)} | ${creditStr} | ${sharedStr} |`);
    }
    lines.push("");
  }

  return lines.join("\n");
}

function downloadFile(content: string, filename: string, mime: string) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
}

function exportPdf(
  sections: DownloadSection[],
  deposits: CashDeposit[],
  stocks: StockInvestment[],
  crypto: CryptoInvestment[],
  cards: Card[],
  currency: string,
  grandTotal: number,
  title = "Financial Report",
) {
  const has = (s: DownloadSection) => sections.includes(s);
  const date = new Date().toISOString().slice(0, 10);

  const ts  = `border-collapse:collapse;width:100%;font-size:11px;margin-bottom:20px`;
  const th  = `border:1px solid #ccc;padding:4px 8px;background:#f5f5f5;text-align:left`;
  const td  = `border:1px solid #ccc;padding:4px 8px`;
  const tdR = `border:1px solid #ccc;padding:4px 8px;text-align:right`;

  const financialSections = sections.filter((s) => s !== "cards");
  let summaryHtml = "";
  if (financialSections.length > 1) {
    const tDep = has("deposits") ? deposits.reduce((s, d) => s + (d.convertedAmount ?? 0), 0) : 0;
    const tStk = has("stocks")   ? stocks.reduce((s, st) => s + (st.convertedCurrentValue ?? st.convertedInvestAmount ?? 0), 0) : 0;
    const tCry = has("crypto")   ? crypto.reduce((s, c) => s + (c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), 0) : 0;
    summaryHtml = `<h2>Summary</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Category</th><th style="${th}">Value (${currency})</th><th style="${th}">% of Total</th>
</tr></thead><tbody>
  ${has("deposits") ? `<tr><td style="${td}">Cash Deposits</td><td style="${tdR}">${formatAmount(tDep, currency)}</td><td style="${tdR}">${pct(tDep, grandTotal)}</td></tr>` : ""}
  ${has("stocks")   ? `<tr><td style="${td}">Stock Investments</td><td style="${tdR}">${formatAmount(tStk, currency)}</td><td style="${tdR}">${pct(tStk, grandTotal)}</td></tr>` : ""}
  ${has("crypto")   ? `<tr><td style="${td}">Crypto Investments</td><td style="${tdR}">${formatAmount(tCry, currency)}</td><td style="${tdR}">${pct(tCry, grandTotal)}</td></tr>` : ""}
  <tr><td style="${td};font-weight:bold">Total</td><td style="${tdR};font-weight:bold">${formatAmount(grandTotal, currency)}</td><td style="${tdR};font-weight:bold">100%</td></tr>
</tbody></table>`;
  }

  const depRows = has("deposits") ? deposits.map(d => `<tr>
    <td style="${td}">${d.platform}</td><td style="${td}">${d.platformType}</td>
    <td style="${td}">${d.countryRegion || "—"}</td><td style="${td}">${d.depositType}</td>
    <td style="${tdR}">${formatAmount(d.amount, d.currency)}</td>
    <td style="${tdR}">${formatAmount(d.convertedAmount, currency)}</td>
    <td style="${tdR}">${pct(d.convertedAmount ?? 0, grandTotal)}</td></tr>`).join("") : "";

  const stkRows = has("stocks") ? stocks.map(s => {
    const val = s.convertedCurrentValue ?? s.convertedInvestAmount ?? 0;
    return `<tr>
    <td style="${td}">${s.symbol}</td><td style="${td}">${s.name}</td>
    <td style="${tdR}">${s.stockAmount}</td><td style="${tdR}">${formatAmount(s.investAmount, s.currency)}</td>
    <td style="${tdR}">${s.currentPrice != null ? formatPrice(s.currentPrice) : "—"}</td>
    <td style="${tdR}">${s.currentValue != null ? formatAmount(s.currentValue, s.priceCurrency ?? s.currency) : "—"}</td>
    <td style="${tdR}">${formatAmount(val, currency)}</td>
    <td style="${tdR}">${s.pnlPercent != null ? (s.pnlPercent >= 0 ? "+" : "") + s.pnlPercent.toFixed(2) + "%" : "—"}</td>
    <td style="${tdR}">${pct(val, grandTotal)}</td></tr>`;
  }).join("") : "";

  const cryRows = has("crypto") ? crypto.map(c => {
    const val = c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0;
    return `<tr>
    <td style="${td}">${c.symbol}</td><td style="${td}">${c.name}</td>
    <td style="${tdR}">${c.amount}</td><td style="${tdR}">${formatAmount(c.investAmount, c.currency)}</td>
    <td style="${tdR}">${c.currentPrice != null ? formatAmount(c.currentPrice, "USD") : "—"}</td>
    <td style="${tdR}">${c.currentValue != null ? formatAmount(c.currentValue, "USD") : "—"}</td>
    <td style="${tdR}">${formatAmount(val, currency)}</td>
    <td style="${tdR}">${c.pnlPercent != null ? (c.pnlPercent >= 0 ? "+" : "") + c.pnlPercent.toFixed(2) + "%" : "—"}</td>
    <td style="${tdR}">${pct(val, grandTotal)}</td></tr>`;
  }).join("") : "";

  const cardRows = has("cards") ? cards.map(c => {
    const creditStr = c.creditLimit != null
      ? `${c.creditLimitCurrency ?? ""} ${c.creditLimit.toLocaleString("en-US", { maximumFractionDigits: 0 })}`.trim() : "—";
    const sharedStr = c.types.includes("Credit")
      ? (c.sharedCredit === true ? "Shared" : c.sharedCredit === false ? "Dedicated" : "—") : "—";
    return `<tr>
    <td style="${td}">${c.bank}</td><td style="${td}">${c.countryRegion || "—"}</td>
    <td style="${td}">${c.cardName}</td><td style="${td}">${c.types.join(", ")}</td>
    <td style="${td}">${c.network}</td><td style="${td}">${formatExpiry(c.expireDate)}</td>
    <td style="${tdR}">${creditStr}</td><td style="${td}">${sharedStr}</td></tr>`;
  }).join("") : "";

  const html = `<!DOCTYPE html><html><head><meta charset="utf-8">
<title>${title} ${date}</title>
<style>body{font-family:Arial,sans-serif;padding:24px;color:#111}h1{font-size:18px;margin-bottom:4px}h2{font-size:14px;margin-top:20px}@media print{button{display:none}}</style>
</head><body>
<h1>${title}</h1><p style="color:#666;font-size:12px">${date}</p>
<button onclick="window.print()" style="margin-bottom:16px;padding:6px 14px;cursor:pointer">Print / Save as PDF</button>
${summaryHtml}
${has("deposits") && deposits.length > 0 ? `<h2>Cash Deposits</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Platform</th><th style="${th}">Type</th><th style="${th}">Country</th><th style="${th}">F/X</th>
  <th style="${th}">Amount</th><th style="${th}">≈ ${currency}</th><th style="${th}">% of Total</th>
</tr></thead><tbody>${depRows}</tbody></table>` : ""}
${has("stocks") && stocks.length > 0 ? `<h2>Stock Investments</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Symbol</th><th style="${th}">Name</th><th style="${th}">Shares</th>
  <th style="${th}">Invested</th><th style="${th}">Price</th><th style="${th}">Value</th>
  <th style="${th}">≈ ${currency}</th><th style="${th}">P&amp;L%</th><th style="${th}">% of Total</th>
</tr></thead><tbody>${stkRows}</tbody></table>` : ""}
${has("crypto") && crypto.length > 0 ? `<h2>Crypto Investments</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Symbol</th><th style="${th}">Name</th><th style="${th}">Amount</th>
  <th style="${th}">Invested</th><th style="${th}">Price (USD)</th><th style="${th}">Value (USD)</th>
  <th style="${th}">≈ ${currency}</th><th style="${th}">P&amp;L%</th><th style="${th}">% of Total</th>
</tr></thead><tbody>${cryRows}</tbody></table>` : ""}
${has("cards") && cards.length > 0 ? `<h2>Cards</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Bank</th><th style="${th}">Country</th><th style="${th}">Card Name</th>
  <th style="${th}">Types</th><th style="${th}">Network</th><th style="${th}">Expiry</th>
  <th style="${th}">Credit Limit</th><th style="${th}">Shared Credit</th>
</tr></thead><tbody>${cardRows}</tbody></table>` : ""}
</body></html>`;

  const w = window.open("", "_blank");
  if (w) { w.document.write(html); w.document.close(); }
}

// ── Download modal ────────────────────────────────────────────────────────────

function DownloadModal({ deposits, stocks, crypto, cards, currency, grandTotal, onClose }: {
  deposits: CashDeposit[]; stocks: StockInvestment[]; crypto: CryptoInvestment[]; cards: Card[];
  currency: string; grandTotal: number; onClose: () => void;
}) {
  const [sections, setSections] = useState<DownloadSection[]>(["deposits", "stocks", "crypto", "cards"]);
  const [format,   setFormat]   = useState<"markdown" | "pdf">("markdown");
  const [mode,     setMode]     = useState<"combined" | "separate">("combined");

  const toggle = (s: DownloadSection) =>
    setSections((p) => p.includes(s) ? p.filter((x) => x !== s) : [...p, s]);

  const handleDownload = () => {
    if (sections.length === 0) return;
    const date = new Date().toISOString().slice(0, 10);
    if (mode === "combined") {
      if (format === "markdown") {
        downloadFile(buildMarkdown(sections, deposits, stocks, crypto, cards, currency, grandTotal), `financial-${date}.md`, "text/markdown");
      } else {
        exportPdf(sections, deposits, stocks, crypto, cards, currency, grandTotal, "Financial Report");
      }
    } else {
      for (const section of sections) {
        if (format === "markdown") {
          downloadFile(buildMarkdown([section], deposits, stocks, crypto, cards, currency, grandTotal), `financial-${section}-${date}.md`, "text/markdown");
        } else {
          exportPdf([section], deposits, stocks, crypto, cards, currency, grandTotal, SECTION_LABELS[section]);
        }
      }
    }
    onClose();
  };

  return (
    <Modal title="Download Report" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div>
          <p className="mb-2 text-xs text-[--color-muted]">Sections</p>
          <div className="grid grid-cols-2 gap-2">
            {(["deposits", "stocks", "crypto", "cards"] as DownloadSection[]).map((s) => (
              <label key={s} className={`flex cursor-pointer select-none items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors ${
                sections.includes(s)
                  ? "border-[--color-primary] bg-[--color-primary]/5"
                  : "border-[--color-border] hover:bg-[--color-border]/30"
              }`}>
                <input type="checkbox" className="accent-[--color-primary]"
                  checked={sections.includes(s)} onChange={() => toggle(s)} />
                {SECTION_LABELS[s]}
              </label>
            ))}
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <p className="mb-2 text-xs text-[--color-muted]">Format</p>
            <div className="flex flex-col gap-2">
              {(["markdown", "pdf"] as const).map((f) => (
                <label key={f} className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
                  <input type="radio" name="dl-format" checked={format === f} onChange={() => setFormat(f)} />
                  {f === "markdown" ? "Markdown (.md)" : "PDF (print)"}
                </label>
              ))}
            </div>
          </div>
          <div>
            <p className="mb-2 text-xs text-[--color-muted]">Output</p>
            <div className="flex flex-col gap-2">
              {(["combined", "separate"] as const).map((m) => (
                <label key={m} className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
                  <input type="radio" name="dl-mode" checked={mode === m} onChange={() => setMode(m)} />
                  {m === "combined" ? "Combined" : "Separate files"}
                </label>
              ))}
            </div>
          </div>
        </div>
        {mode === "separate" && format === "pdf" && (
          <p className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:bg-amber-900/20 dark:text-amber-400">
            Separate PDF opens one window per section — allow popups if prompted.
          </p>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>Cancel</Button>
          <Button size="sm" disabled={sections.length === 0} onClick={handleDownload}>
            <Download className="mr-1.5 h-3.5 w-3.5" />
            Download
          </Button>
        </div>
      </div>
    </Modal>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function FinancialManager() {
  const [tab, setTab] = useState<Tab>("deposits");
  const [defaultCurrency, setDefaultCurrency] = useState<Currency>("USD");
  const [hideAmounts, setHideAmounts] = useState(false);

  const [deposits,      setDeposits]      = useState<CashDeposit[]>([]);
  const [stocks,        setStocks]        = useState<StockInvestment[]>([]);
  const [crypto,        setCrypto]        = useState<CryptoInvestment[]>([]);
  const [cards,         setCards]         = useState<Card[]>([]);
  const [salaryRecords, setSalaryRecords] = useState<SalaryUsageRecord[]>([]);

  const [loading,    setLoading]    = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving,     setSaving]     = useState(false);
  const [fxRates,    setFxRates]    = useState<Record<string, number>>({});

  const [salaryEnabled, setSalaryEnabled] = useState(true);
  useEffect(() => {
    const stored = localStorage.getItem("salary_tracking_enabled");
    if (stored !== null) setSalaryEnabled(stored === "true");
  }, []);
  const toggleSalary = (v: boolean) => {
    setSalaryEnabled(v);
    localStorage.setItem("salary_tracking_enabled", String(v));
  };

  const depositSort = useSort({ column: "platform", dir: "asc" });
  const stockSort   = useSort({ column: "symbol",   dir: "asc" });
  const cryptoSort  = useSort({ column: "symbol",   dir: "asc" });
  const cardSort    = useSort({ column: "bank",     dir: "asc" });
  const salarySort  = useSort({ column: "year",     dir: "desc" });

  const [searchTerm, setSearchTerm] = useState("");
  useEffect(() => { setSearchTerm(""); }, [tab]);

  const [modal, setModal] = useState<
    | { mode: "add-deposit" }   | { mode: "edit-deposit";  item: CashDeposit }
    | { mode: "add-stock" }     | { mode: "edit-stock";    item: StockInvestment }
    | { mode: "add-crypto" }    | { mode: "edit-crypto";   item: CryptoInvestment }
    | { mode: "add-card" }      | { mode: "edit-card";     item: Card }
    | { mode: "add-salary" }    | { mode: "edit-salary";   item: SalaryUsageRecord }
    | null
  >(null);

  const [showDownload, setShowDownload] = useState(false);

  const depositSuggestions = {
    platforms:     unique(deposits.map((d) => d.platform)),
    platformTypes: unique(deposits.map((d) => d.platformType)),
    countries:     unique(deposits.map((d) => d.countryRegion)),
  };
  const regionSuggestions = unique(salaryRecords.map((r) => r.region));
  const brokerSuggestions = unique(stocks.map((s) => s.broker));

  const loadAll = useCallback(async () => {
    const [deps, stks, cry, cds, sal, cur, rates] = await Promise.all([
      apiFetch<CashDeposit>("deposits"),
      apiFetch<StockInvestment>("stocks"),
      apiFetch<CryptoInvestment>("crypto"),
      apiFetch<Card>("cards"),
      apiFetch<SalaryUsageRecord>("salary"),
      fetchUserCurrency(),
      fetchExchangeRates(),
    ]);
    setDeposits(deps);
    setStocks(stks);
    setCrypto(cry);
    setCards(cds);
    setSalaryRecords(sal);
    setDefaultCurrency(cur as Currency);
    setFxRates(rates);
  }, []);

  useEffect(() => {
    setLoading(true);
    loadAll().finally(() => setLoading(false));
  }, [loadAll]);

  const handleCurrencyChange = useCallback(async (c: Currency) => {
    setDefaultCurrency(c);
    await saveUserCurrency(c);
    // Re-fetch so backend recalculates with new currency
    const [deps, stks, cry] = await Promise.all([
      apiFetch<CashDeposit>("deposits"),
      apiFetch<StockInvestment>("stocks"),
      apiFetch<CryptoInvestment>("crypto"),
    ]);
    setDeposits(deps);
    setStocks(stks);
    setCrypto(cry);
  }, []);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await apiRefreshPrices();
      await loadAll();
    } finally {
      setRefreshing(false);
    }
  }, [loadAll]);

  // Summaries use server-converted values
  const totalDeposits = deposits.reduce((s, d) => s + (d.convertedAmount ?? 0), 0);
  const totalStocks   = stocks.reduce((s, st) => s + (st.convertedCurrentValue ?? st.convertedInvestAmount ?? 0), 0);
  const totalCrypto   = crypto.reduce((s, c) => s + (c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), 0);

  // Aggregate P&L% for summary cards (only when at least one item has live price)
  const stocksInvested  = stocks.reduce((s, st) => s + st.convertedInvestAmount, 0);
  const stocksPnlPct    = stocks.some((st) => st.pnlPercent != null) && stocksInvested > 0
    ? (totalStocks - stocksInvested) / stocksInvested * 100
    : null;

  const cryptoInvested  = crypto.reduce((s, c) => s + c.convertedInvestAmount, 0);
  const cryptoPnlPct    = crypto.some((c) => c.pnlPercent != null) && cryptoInvested > 0
    ? (totalCrypto - cryptoInvested) / cryptoInvested * 100
    : null;

  const grandTotal = totalDeposits + totalStocks + totalCrypto;

  // Convert a default-currency total back to USD for the secondary label
  const toUSD = (amount: number): number | null => {
    if (defaultCurrency === "USD") return null;
    const rate = fxRates[defaultCurrency];
    if (!rate) return null;
    return amount / rate;
  };

  const summaryItems = [
    { label: "Cash Deposits",      value: totalDeposits, pnlPercent: null,         share: grandTotal > 0 ? totalDeposits / grandTotal * 100 : 0, usdValue: null as number | null },
    { label: "Stock Investments",  value: totalStocks,   pnlPercent: stocksPnlPct, share: grandTotal > 0 ? totalStocks  / grandTotal * 100 : 0, usdValue: toUSD(totalStocks) },
    { label: "Crypto Investments", value: totalCrypto,   pnlPercent: cryptoPnlPct, share: grandTotal > 0 ? totalCrypto  / grandTotal * 100 : 0, usdValue: toUSD(totalCrypto) },
  ] as const;

  // ── CRUD ──────────────────────────────────────────────────────────────────

  async function saveDeposit(data: DepositFields) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-deposit") {
        await apiUpdate<CashDeposit>("deposits", modal.item.id, data);
      } else {
        await apiCreate<CashDeposit>("deposits", data);
      }
      setModal(null);
      setDeposits(await apiFetch<CashDeposit>("deposits"));
    } finally { setSaving(false); }
  }

  async function saveStock(data: StockFields) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-stock") {
        await apiUpdate<StockInvestment>("stocks", modal.item.id, data);
      } else {
        await apiCreate<StockInvestment>("stocks", data);
      }
      setModal(null);
      setStocks(await apiFetch<StockInvestment>("stocks"));
    } finally { setSaving(false); }
  }

  async function saveCrypto(data: CryptoFields) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-crypto") {
        await apiUpdate<CryptoInvestment>("crypto", modal.item.id, data);
      } else {
        await apiCreate<CryptoInvestment>("crypto", data);
      }
      setModal(null);
      setCrypto(await apiFetch<CryptoInvestment>("crypto"));
    } finally { setSaving(false); }
  }

  const deleteDeposit = async (id: string) => { await apiDelete("deposits", id); setDeposits((p) => p.filter((d) => d.id !== id)); };
  const deleteStock   = async (id: string) => { await apiDelete("stocks",   id); setStocks((p)   => p.filter((s) => s.id !== id)); };
  const deleteCrypto  = async (id: string) => { await apiDelete("crypto",   id); setCrypto((p)   => p.filter((c) => c.id !== id)); };
  const deleteCard    = async (id: string) => { await apiDelete("cards",    id); setCards((p)    => p.filter((c) => c.id !== id)); };

  async function saveCard(data: CardFields) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-card") {
        await apiUpdate<Card>("cards", modal.item.id, data);
      } else {
        await apiCreate<Card>("cards", data);
      }
      setModal(null);
      setCards(await apiFetch<Card>("cards"));
    } finally { setSaving(false); }
  }

  async function saveSalary(data: SalaryFields) {
    setSaving(true);
    const totalExpense = data.livingExpense + data.houseRent + data.otherExpense;
    try {
      if (modal?.mode === "edit-salary") {
        await apiUpdate<SalaryUsageRecord>("salary", modal.item.id, { ...data, totalExpense });
      } else {
        await apiCreate<SalaryUsageRecord>("salary", { ...data, totalExpense });
      }
      setModal(null);
      setSalaryRecords(await apiFetch<SalaryUsageRecord>("salary"));
    } finally { setSaving(false); }
  }

  const deleteSalary = async (id: string) => {
    await apiDelete("salary", id);
    setSalaryRecords((p) => p.filter((r) => r.id !== id));
  };

  // Sorted views
  const sortedDeposits = sortData(deposits, depositSort.sort);
  const sortedStocks   = sortData(stocks,   stockSort.sort);
  const sortedCrypto   = sortData(crypto,   cryptoSort.sort);
  const sortedCards    = sortData(cards,    cardSort.sort);
  const sortedSalary   = sortData(salaryRecords, salarySort.sort);

  // Filtered views
  const q = searchTerm.toLowerCase();
  const filteredDeposits = q
    ? sortedDeposits.filter((d) =>
        [d.platform, d.platformType, d.countryRegion, d.depositType, d.currency]
          .some((v) => v?.toLowerCase().includes(q)))
    : sortedDeposits;
  const filteredStocks = q
    ? sortedStocks.filter((s) =>
        [s.symbol, s.name, s.broker, s.stockType]
          .some((v) => v?.toLowerCase().includes(q)))
    : sortedStocks;
  const filteredCrypto = q
    ? sortedCrypto.filter((c) =>
        [c.symbol, c.name]
          .some((v) => v?.toLowerCase().includes(q)))
    : sortedCrypto;
  const filteredCards = q
    ? sortedCards.filter((c) =>
        [c.bank, c.countryRegion, c.cardName, c.network, c.types.join(" ")]
          .some((v) => v?.toLowerCase().includes(q)))
    : sortedCards;
  const filteredSalary = q
    ? sortedSalary.filter((r) =>
        [r.region, r.currency, String(r.year), String(r.month).padStart(2, "0")]
          .some((v) => v?.toLowerCase().includes(q)))
    : sortedSalary;

  // Amount masking helper
  const amt = (formatted: string) => hideAmounts ? "***" : formatted;

  const tabCls = (t: Tab) =>
    `px-4 py-2 text-xs font-medium rounded-md transition-colors ${
      tab === t
        ? "bg-black text-white dark:bg-white dark:text-black"
        : "text-[--color-muted] hover:bg-[--color-border]/50"
    }`;

  const thCls = "border-b border-[--color-border] bg-[--color-surface-raised] text-xs text-[--color-muted]";

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b border-[--color-border] px-6 py-4">
        <div className="flex items-center justify-between">
          <h1 className="text-base font-semibold">Financial</h1>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setHideAmounts((v) => !v)}
              title={hideAmounts ? "Show amounts" : "Hide amounts"}
              className="rounded-md p-1.5 text-[--color-muted] hover:bg-[--color-border]/50"
            >
              {hideAmounts
                ? <EyeOff className="h-3.5 w-3.5" />
                : <Eye className="h-3.5 w-3.5" />}
            </button>
            <button
              onClick={() => void handleRefresh()}
              disabled={refreshing}
              title="Refresh prices & rates"
              className="rounded-md p-1.5 text-[--color-muted] hover:bg-[--color-border]/50 disabled:opacity-50"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? "animate-spin" : ""}`} />
            </button>
            <button
              onClick={() => setShowDownload(true)}
              title="Download report"
              className="flex items-center gap-1.5 rounded-md border border-[--color-border] bg-[--color-surface] px-2.5 py-1.5 text-xs text-[--color-muted] hover:text-inherit"
            >
              <Download className="h-3.5 w-3.5" />
              Download
            </button>
            <div className="relative flex items-center gap-1.5 rounded-md border border-[--color-border] bg-[--color-surface] px-2.5 py-1.5">
              <span className="text-xs text-[--color-muted]">Default:</span>
              <select
                value={defaultCurrency}
                onChange={(e) => void handleCurrencyChange(e.target.value as Currency)}
                className="appearance-none bg-transparent pr-4 text-xs font-medium outline-none cursor-pointer"
              >
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
              <ChevronDown className="pointer-events-none absolute right-1.5 h-3 w-3 text-[--color-muted]" />
            </div>
          </div>
        </div>

        {/* Desktop: 3-column grid */}
        <div className="mt-4 hidden sm:grid sm:grid-cols-3 sm:gap-3">
          {summaryItems.map((item) => (
            <SummaryCard key={item.label} label={item.label} value={item.value}
              currency={defaultCurrency} pnlPercent={item.pnlPercent} share={item.share} usdValue={item.usdValue} hide={hideAmounts} />
          ))}
        </div>
        {/* Mobile: swipeable snap carousel */}
        <div className="mt-4 flex gap-3 overflow-x-auto sm:hidden"
          style={{ scrollSnapType: "x mandatory", WebkitOverflowScrolling: "touch" }}>
          {summaryItems.map((item, i) => (
            <div key={item.label} className="shrink-0"
              style={{ scrollSnapAlign: "start", width: "78vw",
                marginRight: i === summaryItems.length - 1 ? "8vw" : undefined }}>
              <SummaryCard label={item.label} value={item.value}
                currency={defaultCurrency} pnlPercent={item.pnlPercent} share={item.share} usdValue={item.usdValue} hide={hideAmounts} />
            </div>
          ))}
        </div>

        <div className="mt-4 flex items-center gap-1">
          <button className={tabCls("deposits")} onClick={() => setTab("deposits")}>Cash Deposits</button>
          <button className={tabCls("stocks")}   onClick={() => setTab("stocks")}>Stocks</button>
          <button className={tabCls("crypto")}   onClick={() => setTab("crypto")}>Crypto</button>
          <button className={tabCls("cards")}    onClick={() => setTab("cards")}>Cards</button>
          <button className={tabCls("salary")}   onClick={() => setTab("salary")}>Salary</button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {loading ? (
          <p className="py-12 text-center text-sm text-[--color-muted]">Loading…</p>
        ) : (
          <>
            <div className="mb-4 flex items-center gap-2">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-[--color-muted]" />
                <input
                  className="w-full rounded-md border border-[--color-border] bg-[--color-surface] py-1.5 pl-8 pr-3 text-sm outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30"
                  placeholder={`Search ${tab === "deposits" ? "deposits" : tab === "stocks" ? "stocks" : tab === "crypto" ? "crypto" : tab === "cards" ? "cards" : "salary records"}…`}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
              {(tab !== "salary" || salaryEnabled) && (
                <Button size="sm" onClick={() => setModal(
                  tab === "deposits" ? { mode: "add-deposit" }
                  : tab === "stocks" ? { mode: "add-stock" }
                  : tab === "crypto" ? { mode: "add-crypto" }
                  : tab === "cards"  ? { mode: "add-card" }
                  : { mode: "add-salary" },
                )}>
                  <Plus className="mr-1.5 h-3.5 w-3.5" />
                  Add {tab === "deposits" ? "Deposit" : tab === "stocks" ? "Stock" : tab === "crypto" ? "Crypto" : tab === "cards" ? "Card" : "Record"}
                </Button>
              )}
            </div>

            {/* ── Cash Deposits ── */}
            {tab === "deposits" && (filteredDeposits.length === 0 ? (
              <p className="py-12 text-center text-sm text-[--color-muted]">
                {q ? `No deposits matching "${searchTerm}".` : "No cash deposits yet."}
              </p>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                <table className="w-full text-sm">
                  <thead>
                    <tr className={thCls}>
                      <Th label="Platform" column="platform" sort={depositSort.sort} onSort={depositSort.toggle} />
                      <Th label="Type"     column="platformType" sort={depositSort.sort} onSort={depositSort.toggle} />
                      <Th label="Country"  column="countryRegion" sort={depositSort.sort} onSort={depositSort.toggle} />
                      <Th label="F/X"      column="depositType" sort={depositSort.sort} onSort={depositSort.toggle} />
                      <Th label="Amount"   column="amount" sort={depositSort.sort} onSort={depositSort.toggle} right />
                      <Th label={`≈ ${defaultCurrency}`} column="convertedAmount" sort={depositSort.sort} onSort={depositSort.toggle} right />
                      <th className={`px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]`}>% of Total</th>
                      <th className="px-4 py-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {filteredDeposits.map((d) => (
                      <tr key={d.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                        <td className="px-4 py-3 font-medium">{d.platform}</td>
                        <td className="px-4 py-3 text-[--color-muted]">{d.platformType}</td>
                        <td className="px-4 py-3 text-[--color-muted]">{d.countryRegion || "—"}</td>
                        <td className="px-4 py-3">
                          <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                            d.depositType === "FIXED"
                              ? "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300"
                              : "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300"
                          }`}>{d.depositType}</span>
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">{amt(formatAmount(d.amount, d.currency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">{amt(formatAmount(d.convertedAmount, defaultCurrency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-xs text-[--color-muted]">{pct(d.convertedAmount ?? 0, grandTotal)}</td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-1">
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => setModal({ mode: "edit-deposit", item: d })}>
                              <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
                            </Button>
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => void deleteDeposit(d.id)}>
                              <Trash2 className="h-3.5 w-3.5 text-red-400" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}

            {/* ── Stocks ── */}
            {tab === "stocks" && (filteredStocks.length === 0 ? (
              <p className="py-12 text-center text-sm text-[--color-muted]">
                {q ? `No stocks matching "${searchTerm}".` : "No stock investments yet."}
              </p>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                <table className="w-full text-sm">
                  <thead>
                    <tr className={thCls}>
                      <Th label="Symbol"   column="symbol"      sort={stockSort.sort} onSort={stockSort.toggle} />
                      <Th label="Name"     column="name"        sort={stockSort.sort} onSort={stockSort.toggle} />
                      <Th label="Type"     column="stockType"   sort={stockSort.sort} onSort={stockSort.toggle} />
                      <Th label="Shares"   column="stockAmount" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label="Invested" column="investAmount" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label="Price"    column="currentPrice" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label="Value"    column="currentValue" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label={`≈ ${defaultCurrency}`} column="convertedCurrentValue" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label="P&L%" column="pnlPercent" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">% of Total</th>
                      <th className="px-4 py-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {filteredStocks.map((s) => (
                      <tr key={s.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                        <td className="px-4 py-3 font-semibold">{s.symbol}</td>
                        <td className="px-4 py-3">{s.name}</td>
                        <td className="px-4 py-3 text-xs text-[--color-muted]">{STOCK_TYPE_LABELS[s.stockType]}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{amt(String(s.stockAmount))}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{amt(formatAmount(s.investAmount, s.currency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {s.currentPrice != null
                            ? <span>{amt(formatPrice(s.currentPrice))} <span className="text-[10px] text-[--color-muted]">{s.priceCurrency}</span></span>
                            : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {s.currentValue != null ? amt(formatAmount(s.currentValue, s.priceCurrency ?? s.currency)) : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                          {s.convertedCurrentValue != null
                            ? amt(formatAmount(s.convertedCurrentValue, defaultCurrency))
                            : amt(formatAmount(s.convertedInvestAmount, defaultCurrency))}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {s.pnlPercent != null ? <PnlBadge pct={s.pnlPercent} /> : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-xs text-[--color-muted]">
                          {pct((s.convertedCurrentValue ?? s.convertedInvestAmount ?? 0), grandTotal)}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-1">
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => setModal({ mode: "edit-stock", item: s })}>
                              <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
                            </Button>
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => void deleteStock(s.id)}>
                              <Trash2 className="h-3.5 w-3.5 text-red-400" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}

            {/* ── Crypto ── */}
            {tab === "crypto" && (filteredCrypto.length === 0 ? (
              <p className="py-12 text-center text-sm text-[--color-muted]">
                {q ? `No crypto matching "${searchTerm}".` : "No crypto investments yet."}
              </p>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                <table className="w-full text-sm">
                  <thead>
                    <tr className={thCls}>
                      <Th label="Symbol"   column="symbol"       sort={cryptoSort.sort} onSort={cryptoSort.toggle} />
                      <Th label="Name"     column="name"         sort={cryptoSort.sort} onSort={cryptoSort.toggle} />
                      <Th label="Amount"   column="amount"       sort={cryptoSort.sort} onSort={cryptoSort.toggle} right />
                      <Th label="Invested" column="investAmount" sort={cryptoSort.sort} onSort={cryptoSort.toggle} right />
                      <Th label="Price (USD)" column="currentPrice" sort={cryptoSort.sort} onSort={cryptoSort.toggle} right />
                      <Th label="Value (USD)" column="currentValue" sort={cryptoSort.sort} onSort={cryptoSort.toggle} right />
                      <Th label={`≈ ${defaultCurrency}`} column="convertedCurrentValue" sort={cryptoSort.sort} onSort={cryptoSort.toggle} right />
                      <Th label="P&L%" column="pnlPercent" sort={cryptoSort.sort} onSort={cryptoSort.toggle} right />
                      <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">% of Total</th>
                      <th className="px-4 py-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {filteredCrypto.map((c) => (
                      <tr key={c.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                        <td className="px-4 py-3 font-semibold">{c.symbol}</td>
                        <td className="px-4 py-3">{c.name}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{amt(String(c.amount))}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{amt(formatAmount(c.investAmount, c.currency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.currentPrice != null ? amt(formatAmount(c.currentPrice, "USD")) : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.currentValue != null ? amt(formatAmount(c.currentValue, "USD")) : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                          {c.convertedCurrentValue != null
                            ? amt(formatAmount(c.convertedCurrentValue, defaultCurrency))
                            : amt(formatAmount(c.convertedInvestAmount, defaultCurrency))}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.pnlPercent != null ? <PnlBadge pct={c.pnlPercent} /> : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-xs text-[--color-muted]">
                          {pct((c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), grandTotal)}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-1">
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => setModal({ mode: "edit-crypto", item: c })}>
                              <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
                            </Button>
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => void deleteCrypto(c.id)}>
                              <Trash2 className="h-3.5 w-3.5 text-red-400" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}

            {/* ── Salary ── */}
            {tab === "salary" && (
              <>
                <div className="mb-4 flex items-center justify-between rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3">
                  <div>
                    <p className="text-sm font-medium">Salary Tracking</p>
                    <p className="text-xs text-[--color-muted]">Monthly salary &amp; expense records — no total banner</p>
                  </div>
                  <label className="relative inline-flex cursor-pointer items-center">
                    <input type="checkbox" className="peer sr-only" checked={salaryEnabled}
                      onChange={(e) => toggleSalary(e.target.checked)} />
                    <div className="h-5 w-9 rounded-full bg-[--color-border] transition-colors peer-checked:bg-[--color-primary] peer-focus:ring-2 peer-focus:ring-[--color-primary]/30 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:bg-white after:transition-all after:content-[''] peer-checked:after:translate-x-4" />
                  </label>
                </div>
                {salaryEnabled && (filteredSalary.length === 0 ? (
                  <p className="py-12 text-center text-sm text-[--color-muted]">
                    {q ? `No records matching "${searchTerm}".` : "No salary records yet."}
                  </p>
                ) : (
                  <>
                    <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className={thCls}>
                            <Th label="Year / Month"      column="year"          sort={salarySort.sort} onSort={salarySort.toggle} />
                            <Th label="Region / Currency" column="region"        sort={salarySort.sort} onSort={salarySort.toggle} />
                            <Th label="Salary (Excl. Retirement)" column="salary"  sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Bonus"              column="bonus"   sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Retirement (Emp.)" column="retirementSavingEmployee" sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Retirement (Emplr.)" column="retirementSavingEmployer" sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Tax"               column="tax"           sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">
                              House Rent <span className="opacity-60">*</span>
                            </th>
                            <Th label="Living Expense"    column="livingExpense"  sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Other Expense"     column="otherExpense"   sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Total Expense"     column="totalExpense"   sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <th className="px-4 py-2.5" />
                          </tr>
                        </thead>
                        <tbody>
                          {filteredSalary.map((r) => {
                            const fmtS = (v: number) => v === 0 ? "—"
                              : new Intl.NumberFormat("en-US", { style: "currency", currency: r.currency, maximumFractionDigits: 0 }).format(v);
                            return (
                              <tr key={r.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                                <td className="px-4 py-3 font-medium tabular-nums">
                                  {r.year}/{String(r.month).padStart(2, "0")}
                                </td>
                                <td className="px-4 py-3 text-[--color-muted]">{r.region} / {r.currency}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.salary))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.bonus))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.retirementSavingEmployee))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.retirementSavingEmployer))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.tax))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.houseRent))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.livingExpense))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{amt(fmtS(r.otherExpense))}</td>
                                <td className="px-4 py-3 text-right tabular-nums font-semibold">{amt(fmtS(r.totalExpense))}</td>
                                <td className="px-4 py-3">
                                  <div className="flex justify-end gap-1">
                                    <Button size="icon" variant="ghost" className="h-7 w-7"
                                      onClick={() => setModal({ mode: "edit-salary", item: r })}>
                                      <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
                                    </Button>
                                    <Button size="icon" variant="ghost" className="h-7 w-7"
                                      onClick={() => void deleteSalary(r.id)}>
                                      <Trash2 className="h-3.5 w-3.5 text-red-400" />
                                    </Button>
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                    <p className="mt-2 text-[11px] text-[--color-muted]">
                      * House Rent: some months are paid in the following month.
                    </p>
                  </>
                ))}
              </>
            )}

            {/* ── Cards ── */}
            {tab === "cards" && (filteredCards.length === 0 ? (
              <p className="py-12 text-center text-sm text-[--color-muted]">
                {q ? `No cards matching "${searchTerm}".` : "No cards yet."}
              </p>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                <table className="w-full text-sm">
                  <thead>
                    <tr className={thCls}>
                      <Th label="Bank"         column="bank"          sort={cardSort.sort} onSort={cardSort.toggle} />
                      <Th label="Country"      column="countryRegion" sort={cardSort.sort} onSort={cardSort.toggle} />
                      <Th label="Card Name"    column="cardName"      sort={cardSort.sort} onSort={cardSort.toggle} />
                      <th className="px-4 py-2.5 text-left text-xs font-medium text-[--color-muted]">Types</th>
                      <Th label="Network"      column="network"     sort={cardSort.sort} onSort={cardSort.toggle} />
                      <Th label="Expiry"       column="expireDate"  sort={cardSort.sort} onSort={cardSort.toggle} />
                      <Th label="Credit Limit" column="creditLimit" sort={cardSort.sort} onSort={cardSort.toggle} right />
                      <th className="px-4 py-2.5 text-left text-xs font-medium text-[--color-muted]">Shared Credit</th>
                      <th className="px-4 py-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {filteredCards.map((c) => (
                      <tr key={c.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                        <td className="px-4 py-3 font-medium">{c.bank}</td>
                        <td className="px-4 py-3 text-[--color-muted]">{c.countryRegion || "—"}</td>
                        <td className="px-4 py-3">{c.cardName}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {c.types.map((t) => (
                              <span key={t} className={`rounded px-1.5 py-0.5 text-xs font-medium ${TYPE_COLORS[t]}`}>{t}</span>
                            ))}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${NETWORK_COLORS[c.network]}`}>{c.network}</span>
                        </td>
                        <td className="px-4 py-3 tabular-nums">{formatExpiry(c.expireDate)}</td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.creditLimit != null
                            ? <span>{c.creditLimitCurrency && <span className="mr-1 text-xs text-[--color-muted]">{c.creditLimitCurrency}</span>}{amt(c.creditLimit.toLocaleString("en-US", { minimumFractionDigits: 0, maximumFractionDigits: 0 }))}</span>
                            : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3">
                          {c.types.includes("Credit")
                            ? c.sharedCredit === true
                              ? <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">Shared</span>
                              : c.sharedCredit === false
                                ? <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">Dedicated</span>
                                : <span className="text-[--color-muted]">—</span>
                            : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-1">
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => setModal({ mode: "edit-card", item: c })}>
                              <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
                            </Button>
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => void deleteCard(c.id)}>
                              <Trash2 className="h-3.5 w-3.5 text-red-400" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </>
        )}
      </div>

      {/* Modals */}
      {(modal?.mode === "add-deposit" || modal?.mode === "edit-deposit") && (
        <Modal title={modal.mode === "add-deposit" ? "Add Cash Deposit" : "Edit Cash Deposit"} onClose={() => setModal(null)}>
          <DepositForm
            initial={modal.mode === "edit-deposit"
              ? { platform: modal.item.platform, platformType: modal.item.platformType,
                  countryRegion: modal.item.countryRegion, depositType: modal.item.depositType,
                  currency: modal.item.currency as Currency, amount: modal.item.amount }
              : emptyDeposit()}
            suggestions={depositSuggestions}
            onSave={saveDeposit} onCancel={() => setModal(null)} saving={saving} />
        </Modal>
      )}

      {(modal?.mode === "add-stock" || modal?.mode === "edit-stock") && (
        <Modal title={modal.mode === "add-stock" ? "Add Stock" : "Edit Stock"} onClose={() => setModal(null)}>
          <StockForm
            initial={modal.mode === "edit-stock"
              ? { broker: modal.item.broker, stockType: modal.item.stockType,
                  symbol: modal.item.symbol, name: modal.item.name,
                  stockAmount: modal.item.stockAmount, investAmount: modal.item.investAmount,
                  currency: modal.item.currency as Currency, fee: modal.item.fee }
              : emptyStock()}
            brokers={brokerSuggestions}
            onSave={saveStock} onCancel={() => setModal(null)} saving={saving} />
        </Modal>
      )}

      {(modal?.mode === "add-crypto" || modal?.mode === "edit-crypto") && (
        <Modal title={modal.mode === "add-crypto" ? "Add Crypto" : "Edit Crypto"} onClose={() => setModal(null)}>
          <CryptoForm
            initial={modal.mode === "edit-crypto"
              ? { name: modal.item.name, symbol: modal.item.symbol,
                  amount: modal.item.amount, investAmount: modal.item.investAmount,
                  currency: modal.item.currency as Currency }
              : emptyCrypto()}
            onSave={saveCrypto} onCancel={() => setModal(null)} saving={saving} />
        </Modal>
      )}

      {(modal?.mode === "add-card" || modal?.mode === "edit-card") && (
        <Modal title={modal.mode === "add-card" ? "Add Card" : "Edit Card"} onClose={() => setModal(null)}>
          <CardForm
            initial={modal.mode === "edit-card"
              ? { bank: modal.item.bank, countryRegion: modal.item.countryRegion,
                  types: modal.item.types, cardName: modal.item.cardName,
                  network: modal.item.network, expireDate: modal.item.expireDate,
                  creditLimit: modal.item.creditLimit, creditLimitCurrency: modal.item.creditLimitCurrency ?? "HKD",
                  sharedCredit: modal.item.sharedCredit }
              : emptyCard()}
            banks={unique(cards.map((c) => c.bank))}
            onSave={saveCard} onCancel={() => setModal(null)} saving={saving} />
        </Modal>
      )}

      {(modal?.mode === "add-salary" || modal?.mode === "edit-salary") && (
        <Modal title={modal.mode === "add-salary" ? "Add Salary Record" : "Edit Salary Record"} onClose={() => setModal(null)}>
          <SalaryForm
            initial={modal.mode === "edit-salary"
              ? { year: modal.item.year, month: modal.item.month, region: modal.item.region,
                  currency: modal.item.currency, salary: modal.item.salary, bonus: modal.item.bonus,
                  retirementSavingEmployee: modal.item.retirementSavingEmployee,
                  retirementSavingEmployer: modal.item.retirementSavingEmployer,
                  tax: modal.item.tax, houseRent: modal.item.houseRent,
                  livingExpense: modal.item.livingExpense, otherExpense: modal.item.otherExpense }
              : emptySalary()}
            regions={regionSuggestions}
            onSave={saveSalary} onCancel={() => setModal(null)} saving={saving} />
        </Modal>
      )}

      {showDownload && (
        <DownloadModal
          deposits={deposits}
          stocks={stocks}
          crypto={crypto}
          cards={cards}
          currency={defaultCurrency}
          grandTotal={grandTotal}
          onClose={() => setShowDownload(false)}
        />
      )}
    </div>
  );
}
