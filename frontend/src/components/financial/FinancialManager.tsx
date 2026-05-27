"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Plus, Pencil, Trash2, RefreshCw, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  CURRENCIES,
  DEPOSIT_TYPES,
  STOCK_TYPES,
  STOCK_TYPE_LABELS,
  type CashDeposit,
  type CryptoInvestment,
  type Currency,
  type ExchangeRates,
  type StockInvestment,
  convertCurrency,
  formatAmount,
} from "@/types/financial";

type Tab = "deposits" | "stocks" | "crypto";

// ── Fetch helpers ─────────────────────────────────────────────────────────────

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

async function fetchRates(): Promise<ExchangeRates | null> {
  try {
    const res = await fetch("/api/financial/rates");
    if (!res.ok) return null;
    return res.json() as Promise<ExchangeRates>;
  } catch {
    return null;
  }
}

async function fetchUserCurrency(): Promise<string> {
  try {
    const res = await fetch("/api/agent/user/preferences");
    if (!res.ok) return "USD";
    const data = await res.json() as { defaultCurrency?: string };
    return data.defaultCurrency ?? "USD";
  } catch {
    return "USD";
  }
}

async function saveUserCurrency(currency: string): Promise<void> {
  await fetch("/api/agent/user/preferences", {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ defaultCurrency: currency }),
  });
}

// ── Combobox input ────────────────────────────────────────────────────────────
// Free-text input with dropdown of suggestions derived from existing records.

const inputCls =
  "w-full rounded-md border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30";

const selectCls = inputCls + " cursor-pointer appearance-none";

function ComboInput({
  value,
  onChange,
  suggestions,
  placeholder,
  required,
}: {
  value: string;
  onChange: (v: string) => void;
  suggestions: string[];
  placeholder?: string;
  required?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const filtered = suggestions.filter(
    (s) => s.toLowerCase().includes(value.toLowerCase()) && s !== value,
  );
  const showDropdown = open && filtered.length > 0;

  // Close on outside click
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <input
        className={inputCls}
        value={value}
        required={required}
        placeholder={placeholder}
        onChange={(e) => { onChange(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        autoComplete="off"
      />
      {showDropdown && (
        <ul className="absolute z-50 mt-1 w-full rounded-md border border-[--color-border] bg-[--color-surface-raised] py-1 shadow-lg">
          {filtered.map((s) => (
            <li
              key={s}
              className="cursor-pointer px-3 py-1.5 text-sm hover:bg-[--color-border]/50"
              onMouseDown={(e) => { e.preventDefault(); onChange(s); setOpen(false); }}
            >
              {s}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── Empty form defaults ───────────────────────────────────────────────────────

const emptyDeposit = (): Omit<CashDeposit, "id" | "ownerEmail" | "createdAt" | "updatedAt"> => ({
  platform: "", platformType: "", countryRegion: "",
  depositType: "FIXED", currency: "USD", amount: 0,
});

const emptyStock = (): Omit<StockInvestment, "id" | "ownerEmail" | "createdAt" | "updatedAt"> => ({
  broker: "", stockType: "US_STOCK", symbol: "", name: "",
  stockAmount: 0, investAmount: 0, currency: "USD", fee: 0,
});

const emptyCrypto = (): Omit<CryptoInvestment, "id" | "ownerEmail" | "createdAt" | "updatedAt"> => ({
  name: "", symbol: "", amount: 0, investAmount: 0, currency: "USD",
});

// ── Modal wrapper ─────────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => { setMounted(true); }, []);
  if (!mounted) return null;

  return createPortal(
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/20 backdrop-blur-sm p-4">
      <div className="w-full max-w-md rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold">{title}</h2>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50"
          >
            ✕
          </button>
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

// ── Deposit form ──────────────────────────────────────────────────────────────

function DepositForm({
  initial, suggestions, onSave, onCancel, saving,
}: {
  initial: Omit<CashDeposit, "id" | "ownerEmail" | "createdAt" | "updatedAt">;
  suggestions: { platforms: string[]; platformTypes: string[]; countries: string[] };
  onSave: (data: typeof initial) => void;
  onCancel: () => void;
  saving: boolean;
}) {
  const [form, setForm] = useState(initial);
  const set = (k: string, v: unknown) => setForm((p) => ({ ...p, [k]: v }));

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(form); }}>
      <Field label="Platform *">
        <ComboInput
          value={form.platform}
          onChange={(v) => set("platform", v)}
          suggestions={suggestions.platforms}
          placeholder="e.g. HSBC, DBS"
          required
        />
      </Field>
      <Field label="Platform Type *">
        <ComboInput
          value={form.platformType}
          onChange={(v) => set("platformType", v)}
          suggestions={suggestions.platformTypes}
          placeholder="e.g. Bank, Brokerage"
          required
        />
      </Field>
      <Field label="Country / Region">
        <ComboInput
          value={form.countryRegion}
          onChange={(v) => set("countryRegion", v)}
          suggestions={suggestions.countries}
          placeholder="e.g. HK, SG, US"
        />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Fixed / Flex">
          <select className={selectCls} value={form.depositType}
            onChange={(e) => set("depositType", e.target.value)}>
            {DEPOSIT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={form.currency}
            onChange={(e) => set("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Amount *">
        <input className={inputCls} type="number" required min="0" step="0.01"
          value={form.amount} onChange={(e) => set("amount", parseFloat(e.target.value) || 0)} />
      </Field>
      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

// ── Stock form ────────────────────────────────────────────────────────────────

function StockForm({
  initial, brokers, onSave, onCancel, saving,
}: {
  initial: Omit<StockInvestment, "id" | "ownerEmail" | "createdAt" | "updatedAt">;
  brokers: string[];
  onSave: (data: typeof initial) => void;
  onCancel: () => void;
  saving: boolean;
}) {
  const [form, setForm] = useState(initial);
  const set = (k: string, v: unknown) => setForm((p) => ({ ...p, [k]: v }));

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(form); }}>
      <Field label="Broker *">
        <ComboInput
          value={form.broker}
          onChange={(v) => set("broker", v)}
          suggestions={brokers}
          placeholder="e.g. Interactive Brokers, Futu"
          required
        />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Type">
          <select className={selectCls} value={form.stockType}
            onChange={(e) => set("stockType", e.target.value)}>
            {STOCK_TYPES.map((t) => <option key={t} value={t}>{STOCK_TYPE_LABELS[t]}</option>)}
          </select>
        </Field>
        <Field label="Symbol *">
          <input className={inputCls} required value={form.symbol}
            onChange={(e) => set("symbol", e.target.value.toUpperCase())} placeholder="e.g. AAPL" />
        </Field>
      </div>
      <Field label="Name *">
        <input className={inputCls} required value={form.name}
          onChange={(e) => set("name", e.target.value)} placeholder="e.g. Apple Inc." />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Shares *">
          <input className={inputCls} type="number" required min="0" step="0.0001"
            value={form.stockAmount} onChange={(e) => set("stockAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={form.currency}
            onChange={(e) => set("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Invest Amount *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={form.investAmount} onChange={(e) => set("investAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Fee">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={form.fee} onChange={(e) => set("fee", parseFloat(e.target.value) || 0)} />
        </Field>
      </div>
      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

// ── Crypto form ───────────────────────────────────────────────────────────────

function CryptoForm({
  initial, onSave, onCancel, saving,
}: {
  initial: Omit<CryptoInvestment, "id" | "ownerEmail" | "createdAt" | "updatedAt">;
  onSave: (data: typeof initial) => void;
  onCancel: () => void;
  saving: boolean;
}) {
  const [form, setForm] = useState(initial);
  const set = (k: string, v: unknown) => setForm((p) => ({ ...p, [k]: v }));

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(form); }}>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Name *">
          <input className={inputCls} required value={form.name}
            onChange={(e) => set("name", e.target.value)} placeholder="e.g. Bitcoin" />
        </Field>
        <Field label="Symbol *">
          <input className={inputCls} required value={form.symbol}
            onChange={(e) => set("symbol", e.target.value.toUpperCase())} placeholder="e.g. BTC" />
        </Field>
      </div>
      <Field label="Amount (coins) *">
        <input className={inputCls} type="number" required min="0" step="0.00000001"
          value={form.amount} onChange={(e) => set("amount", parseFloat(e.target.value) || 0)} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Invest Amount *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={form.investAmount} onChange={(e) => set("investAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={form.currency}
            onChange={(e) => set("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

// ── Summary card ──────────────────────────────────────────────────────────────

function SummaryCard({ label, value, currency }: { label: string; value: number; currency: string }) {
  return (
    <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-4">
      <p className="text-xs text-[--color-muted]">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{formatAmount(value, currency)}</p>
    </div>
  );
}

// ── Unique helper ─────────────────────────────────────────────────────────────

function unique(arr: string[]): string[] {
  return [...new Set(arr.filter(Boolean))].sort();
}

// ── Main component ────────────────────────────────────────────────────────────

export function FinancialManager() {
  const [tab, setTab]             = useState<Tab>("deposits");
  const [defaultCurrency, setDefaultCurrency] = useState<Currency>("USD");
  const [rates, setRates]         = useState<Record<string, number> | null>(null);
  const [ratesUpdated, setRatesUpdated] = useState("");

  const [deposits, setDeposits]   = useState<CashDeposit[]>([]);
  const [stocks, setStocks]       = useState<StockInvestment[]>([]);
  const [crypto, setCrypto]       = useState<CryptoInvestment[]>([]);

  const [modal, setModal] = useState<
    | { mode: "add-deposit" }
    | { mode: "edit-deposit"; item: CashDeposit }
    | { mode: "add-stock" }
    | { mode: "edit-stock"; item: StockInvestment }
    | { mode: "add-crypto" }
    | { mode: "edit-crypto"; item: CryptoInvestment }
    | null
  >(null);

  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  // Derived suggestion lists from existing records
  const depositSuggestions = {
    platforms:     unique(deposits.map((d) => d.platform)),
    platformTypes: unique(deposits.map((d) => d.platformType)),
    countries:     unique(deposits.map((d) => d.countryRegion)),
  };
  const brokerSuggestions = unique(stocks.map((s) => s.broker));

  useEffect(() => {
    async function load() {
      setLoading(true);
      const [deps, stks, cry, ratesData, cur] = await Promise.all([
        apiFetch<CashDeposit>("deposits"),
        apiFetch<StockInvestment>("stocks"),
        apiFetch<CryptoInvestment>("crypto"),
        fetchRates(),
        fetchUserCurrency(),
      ]);
      setDeposits(deps);
      setStocks(stks);
      setCrypto(cry);
      if (ratesData) {
        setRates(ratesData.conversion_rates);
        setRatesUpdated(ratesData.time_last_update_utc);
      }
      setDefaultCurrency(cur as Currency);
      setLoading(false);
    }
    void load();
  }, []);

  const handleCurrencyChange = useCallback(async (c: Currency) => {
    setDefaultCurrency(c);
    await saveUserCurrency(c);
  }, []);

  const refreshRates = useCallback(async () => {
    const data = await fetchRates();
    if (data) {
      setRates(data.conversion_rates);
      setRatesUpdated(data.time_last_update_utc);
    }
  }, []);

  const toDefault = useCallback(
    (amount: number, currency: string) => {
      if (!rates) return amount;
      return convertCurrency(amount, currency, defaultCurrency, rates);
    },
    [rates, defaultCurrency],
  );

  const totalDeposits = deposits.reduce((s, d) => s + toDefault(d.amount, d.currency), 0);
  const totalStocks   = stocks.reduce((s, st) => s + toDefault(st.investAmount, st.currency), 0);
  const totalCrypto   = crypto.reduce((s, c) => s + toDefault(c.investAmount, c.currency), 0);

  // ── CRUD handlers ───────────────────────────────────────────────────────────

  async function saveDeposit(data: Omit<CashDeposit, "id" | "ownerEmail" | "createdAt" | "updatedAt">) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-deposit") {
        const updated = await apiUpdate<CashDeposit>("deposits", modal.item.id, data);
        setDeposits((p) => p.map((d) => d.id === updated.id ? updated : d));
      } else {
        const created = await apiCreate<CashDeposit>("deposits", data);
        setDeposits((p) => [created, ...p]);
      }
      setModal(null);
    } finally {
      setSaving(false);
    }
  }

  async function deleteDeposit(id: string) {
    await apiDelete("deposits", id);
    setDeposits((p) => p.filter((d) => d.id !== id));
  }

  async function saveStock(data: Omit<StockInvestment, "id" | "ownerEmail" | "createdAt" | "updatedAt">) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-stock") {
        const updated = await apiUpdate<StockInvestment>("stocks", modal.item.id, data);
        setStocks((p) => p.map((s) => s.id === updated.id ? updated : s));
      } else {
        const created = await apiCreate<StockInvestment>("stocks", data);
        setStocks((p) => [created, ...p]);
      }
      setModal(null);
    } finally {
      setSaving(false);
    }
  }

  async function deleteStock(id: string) {
    await apiDelete("stocks", id);
    setStocks((p) => p.filter((s) => s.id !== id));
  }

  async function saveCrypto(data: Omit<CryptoInvestment, "id" | "ownerEmail" | "createdAt" | "updatedAt">) {
    setSaving(true);
    try {
      if (modal?.mode === "edit-crypto") {
        const updated = await apiUpdate<CryptoInvestment>("crypto", modal.item.id, data);
        setCrypto((p) => p.map((c) => c.id === updated.id ? updated : c));
      } else {
        const created = await apiCreate<CryptoInvestment>("crypto", data);
        setCrypto((p) => [created, ...p]);
      }
      setModal(null);
    } finally {
      setSaving(false);
    }
  }

  async function deleteCrypto(id: string) {
    await apiDelete("crypto", id);
    setCrypto((p) => p.filter((c) => c.id !== id));
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  const tabCls = (t: Tab) =>
    `px-4 py-2 text-xs font-medium rounded-md transition-colors ${
      tab === t
        ? "bg-black text-white dark:bg-white dark:text-black"
        : "text-[--color-muted] hover:bg-[--color-border]/50"
    }`;

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="border-b border-[--color-border] px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-base font-semibold">Financial</h1>
            {ratesUpdated && (
              <p className="mt-0.5 text-[11px] text-[--color-muted]">
                Rates: {new Date(ratesUpdated).toLocaleString()}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => void refreshRates()}
              title="Refresh exchange rates"
              className="rounded-md p-1.5 text-[--color-muted] hover:bg-[--color-border]/50"
            >
              <RefreshCw className="h-3.5 w-3.5" />
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

        {/* Summary cards */}
        <div className="mt-4 grid grid-cols-3 gap-3">
          <SummaryCard label="Cash Deposits" value={totalDeposits} currency={defaultCurrency} />
          <SummaryCard label="Stock Investments" value={totalStocks} currency={defaultCurrency} />
          <SummaryCard label="Crypto Investments" value={totalCrypto} currency={defaultCurrency} />
        </div>

        {/* Tabs */}
        <div className="mt-4 flex items-center gap-1">
          <button className={tabCls("deposits")} onClick={() => setTab("deposits")}>Cash Deposits</button>
          <button className={tabCls("stocks")} onClick={() => setTab("stocks")}>Stocks</button>
          <button className={tabCls("crypto")} onClick={() => setTab("crypto")}>Crypto</button>
        </div>
      </div>

      {/* Table area */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {loading ? (
          <p className="py-12 text-center text-sm text-[--color-muted]">Loading…</p>
        ) : (
          <>
            <div className="mb-4 flex justify-end">
              <Button
                size="sm"
                onClick={() => setModal(
                  tab === "deposits" ? { mode: "add-deposit" }
                  : tab === "stocks" ? { mode: "add-stock" }
                  : { mode: "add-crypto" },
                )}
              >
                <Plus className="mr-1.5 h-3.5 w-3.5" />
                Add {tab === "deposits" ? "Deposit" : tab === "stocks" ? "Stock" : "Crypto"}
              </Button>
            </div>

            {/* Cash Deposits */}
            {tab === "deposits" && (
              deposits.length === 0 ? (
                <p className="py-12 text-center text-sm text-[--color-muted]">No cash deposits yet.</p>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-[--color-border] bg-[--color-surface-raised] text-xs text-[--color-muted]">
                        <th className="px-4 py-2.5 text-left font-medium">Platform</th>
                        <th className="px-4 py-2.5 text-left font-medium">Type</th>
                        <th className="px-4 py-2.5 text-left font-medium">Country</th>
                        <th className="px-4 py-2.5 text-left font-medium">F/X</th>
                        <th className="px-4 py-2.5 text-right font-medium">Amount</th>
                        <th className="px-4 py-2.5 text-right font-medium">≈ {defaultCurrency}</th>
                        <th className="px-4 py-2.5" />
                      </tr>
                    </thead>
                    <tbody>
                      {deposits.map((d) => (
                        <tr key={d.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                          <td className="px-4 py-3 font-medium">{d.platform}</td>
                          <td className="px-4 py-3 text-[--color-muted]">{d.platformType}</td>
                          <td className="px-4 py-3 text-[--color-muted]">{d.countryRegion || "—"}</td>
                          <td className="px-4 py-3">
                            <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                              d.depositType === "FIXED"
                                ? "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300"
                                : "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300"
                            }`}>
                              {d.depositType}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-right tabular-nums">{formatAmount(d.amount, d.currency)}</td>
                          <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                            {rates ? formatAmount(toDefault(d.amount, d.currency), defaultCurrency) : "—"}
                          </td>
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
              )
            )}

            {/* Stocks */}
            {tab === "stocks" && (
              stocks.length === 0 ? (
                <p className="py-12 text-center text-sm text-[--color-muted]">No stock investments yet.</p>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-[--color-border] bg-[--color-surface-raised] text-xs text-[--color-muted]">
                        <th className="px-4 py-2.5 text-left font-medium">Symbol</th>
                        <th className="px-4 py-2.5 text-left font-medium">Name</th>
                        <th className="px-4 py-2.5 text-left font-medium">Broker</th>
                        <th className="px-4 py-2.5 text-left font-medium">Type</th>
                        <th className="px-4 py-2.5 text-right font-medium">Shares</th>
                        <th className="px-4 py-2.5 text-right font-medium">Invested</th>
                        <th className="px-4 py-2.5 text-right font-medium">Fee</th>
                        <th className="px-4 py-2.5 text-right font-medium">≈ {defaultCurrency}</th>
                        <th className="px-4 py-2.5" />
                      </tr>
                    </thead>
                    <tbody>
                      {stocks.map((s) => (
                        <tr key={s.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                          <td className="px-4 py-3 font-semibold">{s.symbol}</td>
                          <td className="px-4 py-3">{s.name}</td>
                          <td className="px-4 py-3 text-[--color-muted]">{s.broker}</td>
                          <td className="px-4 py-3 text-xs text-[--color-muted]">{STOCK_TYPE_LABELS[s.stockType]}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{s.stockAmount}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{formatAmount(s.investAmount, s.currency)}</td>
                          <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                            {s.fee > 0 ? formatAmount(s.fee, s.currency) : "—"}
                          </td>
                          <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                            {rates ? formatAmount(toDefault(s.investAmount, s.currency), defaultCurrency) : "—"}
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
              )
            )}

            {/* Crypto */}
            {tab === "crypto" && (
              crypto.length === 0 ? (
                <p className="py-12 text-center text-sm text-[--color-muted]">No crypto investments yet.</p>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-[--color-border] bg-[--color-surface-raised] text-xs text-[--color-muted]">
                        <th className="px-4 py-2.5 text-left font-medium">Symbol</th>
                        <th className="px-4 py-2.5 text-left font-medium">Name</th>
                        <th className="px-4 py-2.5 text-right font-medium">Amount</th>
                        <th className="px-4 py-2.5 text-right font-medium">Invested</th>
                        <th className="px-4 py-2.5 text-right font-medium">≈ {defaultCurrency}</th>
                        <th className="px-4 py-2.5" />
                      </tr>
                    </thead>
                    <tbody>
                      {crypto.map((c) => (
                        <tr key={c.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                          <td className="px-4 py-3 font-semibold">{c.symbol}</td>
                          <td className="px-4 py-3">{c.name}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{c.amount}</td>
                          <td className="px-4 py-3 text-right tabular-nums">{formatAmount(c.investAmount, c.currency)}</td>
                          <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                            {rates ? formatAmount(toDefault(c.investAmount, c.currency), defaultCurrency) : "—"}
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
              )
            )}
          </>
        )}
      </div>

      {/* Modals */}
      {(modal?.mode === "add-deposit" || modal?.mode === "edit-deposit") && (
        <Modal
          title={modal.mode === "add-deposit" ? "Add Cash Deposit" : "Edit Cash Deposit"}
          onClose={() => setModal(null)}
        >
          <DepositForm
            initial={modal.mode === "edit-deposit"
              ? { platform: modal.item.platform, platformType: modal.item.platformType,
                  countryRegion: modal.item.countryRegion, depositType: modal.item.depositType,
                  currency: modal.item.currency, amount: modal.item.amount }
              : emptyDeposit()}
            suggestions={depositSuggestions}
            onSave={saveDeposit}
            onCancel={() => setModal(null)}
            saving={saving}
          />
        </Modal>
      )}

      {(modal?.mode === "add-stock" || modal?.mode === "edit-stock") && (
        <Modal
          title={modal.mode === "add-stock" ? "Add Stock" : "Edit Stock"}
          onClose={() => setModal(null)}
        >
          <StockForm
            initial={modal.mode === "edit-stock"
              ? { broker: modal.item.broker, stockType: modal.item.stockType,
                  symbol: modal.item.symbol, name: modal.item.name,
                  stockAmount: modal.item.stockAmount, investAmount: modal.item.investAmount,
                  currency: modal.item.currency, fee: modal.item.fee }
              : emptyStock()}
            brokers={brokerSuggestions}
            onSave={saveStock}
            onCancel={() => setModal(null)}
            saving={saving}
          />
        </Modal>
      )}

      {(modal?.mode === "add-crypto" || modal?.mode === "edit-crypto") && (
        <Modal
          title={modal.mode === "add-crypto" ? "Add Crypto" : "Edit Crypto"}
          onClose={() => setModal(null)}
        >
          <CryptoForm
            initial={modal.mode === "edit-crypto"
              ? { name: modal.item.name, symbol: modal.item.symbol,
                  amount: modal.item.amount, investAmount: modal.item.investAmount,
                  currency: modal.item.currency }
              : emptyCrypto()}
            onSave={saveCrypto}
            onCancel={() => setModal(null)}
            saving={saving}
          />
        </Modal>
      )}
    </div>
  );
}
