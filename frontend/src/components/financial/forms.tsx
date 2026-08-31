"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import {
  CURRENCIES,
  DEPOSIT_TYPES,
  STOCK_TYPES,
  STOCK_TYPE_LABELS,
  CARD_TYPES,
  CARD_NETWORKS,
  FUTURE_EXCHANGE_KINDS,
  FUTURE_EXCHANGE_KIND_LABELS,
  FUTURE_EXCHANGES_BY_KIND,
  DEX_ADDRESS_PLACEHOLDERS,
  FUTURE_SIDES,
  type CashDeposit,
  type CryptoInvestment,
  type StockInvestment,
  type Card,
  type CardNetwork,
  type FutureInvestment,
  type FutureExchangeKind,
  type SalaryUsageRecord,
} from "@/types/financial";
import { Field, ComboInput, SegBtn } from "./shared-ui";
import { apiLookupStockName, inputCls, selectCls } from "./utils";

export type DepositFields = Omit<CashDeposit, "id"|"ownerEmail"|"convertedAmount"|"convertedCurrency"|"createdAt"|"updatedAt">;
export type StockFields   = Omit<StockInvestment, "id"|"ownerEmail"|"currentPrice"|"priceCurrency"|"logoUrl"|"currentValue"|"convertedInvestAmount"|"convertedCurrentValue"|"convertedCurrency"|"pnlPercent"|"createdAt"|"updatedAt">;
export type CryptoFields  = Omit<CryptoInvestment, "id"|"ownerEmail"|"currentPrice"|"logoUrl"|"currentValue"|"convertedInvestAmount"|"convertedCurrentValue"|"convertedCurrency"|"pnlPercent"|"createdAt"|"updatedAt">;
export type CardFields    = Omit<Card, "id"|"ownerEmail"|"createdAt"|"updatedAt">;
export type FutureFields  = Omit<FutureInvestment, "id"|"ownerEmail"|"currentPrice"|"currentValue"|"margin"|"liquidationPrice"|"fundingSinceOpen"|"convertedInvestAmount"|"convertedCurrentValue"|"convertedCurrency"|"pnlPercent"|"source"|"sourceConnectionId"|"hyperliquidDex"|"createdAt"|"updatedAt">;
export type SalaryFields  = Omit<SalaryUsageRecord, "id"|"ownerEmail"|"totalExpense"|"createdAt"|"updatedAt">;

export const emptyDeposit = (): DepositFields => ({ platform:"", platformType:"", countryRegion:"", depositType:"FIXED", currency:"USD", amount:0 });
export const emptyStock   = (): StockFields   => ({ broker:"", stockType:"US_STOCK", symbol:"", name:"", stockAmount:0, investAmount:0, currency:"USD", fee:0 });
export const emptyCrypto  = (): CryptoFields  => ({ name:"", symbol:"", amount:0, investAmount:0, currency:"USD" });
export const emptyCard    = (): CardFields    => ({ bank:"", countryRegion:"", types:[], cardName:"", network:"Visa", expireDate:"", creditLimit:null, creditLimitCurrency:"HKD", sharedCredit:null });
export const emptyFuture  = (): FutureFields  => ({ exchangeKind:"SECURITY", exchange:"IBKR", symbol:"", side:"LONG", quantity:0, entryPrice:0, leverage:null, currency:"USD", connectionAddress:null });

// Each CEX has its own native instrument-id format — the backend fetches the live price
// directly from that exchange's API using this exact id, not a shared/unified symbol.
const CEX_SYMBOL_EXAMPLES: Record<string, string> = {
  BINANCE: "e.g. BTCUSDT",
  OKX:     "e.g. BTC-USDT-SWAP",
  KRAKEN:  "e.g. PF_XBTUSD",
};
export const emptySalary  = (): SalaryFields  => {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1, region: "", currency: "HKD", salary: 0, bonus: 0, retirementSavingEmployee: 0, retirementSavingEmployer: 0, tax: 0, houseRent: 0, livingExpense: 0, otherExpense: 0 };
};

export function DepositForm({ initial, suggestions, onSave, onCancel, saving }: {
  initial: DepositFields;
  suggestions: { platforms: string[]; platformTypes: string[]; countries: string[] };
  onSave: (d: DepositFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const setField = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <Field label="Platform *">
        <ComboInput value={f.platform} onChange={(v) => setField("platform", v)}
          suggestions={suggestions.platforms} placeholder="e.g. HSBC, DBS" required />
      </Field>
      <Field label="Platform Type *">
        <ComboInput value={f.platformType} onChange={(v) => setField("platformType", v)}
          suggestions={suggestions.platformTypes} placeholder="e.g. Bank, Brokerage" required />
      </Field>
      <Field label="Country / Region">
        <ComboInput value={f.countryRegion} onChange={(v) => setField("countryRegion", v)}
          suggestions={suggestions.countries} placeholder="e.g. Hong Kong SAR, Singapore" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Fixed / Flex">
          <select className={selectCls} value={f.depositType} onChange={(e) => setField("depositType", e.target.value)}>
            {DEPOSIT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => setField("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Amount *">
        <input className={inputCls} type="number" required min="0" step="0.01"
          value={f.amount} onChange={(e) => setField("amount", parseFloat(e.target.value) || 0)} />
      </Field>
      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

export function StockForm({ initial, brokers, onSave, onCancel, saving }: {
  initial: StockFields; brokers: string[];
  onSave: (d: StockFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const [nameLookupPending, setNameLookupPending] = useState(false);
  const setField = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));

  // Auto-fills Name from Symbol on blur, via Finnhub/Pyth company-profile lookup — never
  // overwrites a name the user (or an existing edit) already has.
  const handleSymbolBlur = async () => {
    if (!f.symbol.trim() || f.name.trim()) return;
    setNameLookupPending(true);
    try {
      const name = await apiLookupStockName(f.symbol);
      if (name) setField("name", name);
    } finally {
      setNameLookupPending(false);
    }
  };

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <Field label="Broker *">
        <ComboInput value={f.broker} onChange={(v) => setField("broker", v)}
          suggestions={brokers} placeholder="e.g. Interactive Brokers, Futu" required />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Type">
          <select className={selectCls} value={f.stockType} onChange={(e) => setField("stockType", e.target.value)}>
            {STOCK_TYPES.map((t) => <option key={t} value={t}>{STOCK_TYPE_LABELS[t]}</option>)}
          </select>
        </Field>
        <Field label="Symbol *">
          <input className={inputCls} required value={f.symbol}
            onChange={(e) => setField("symbol", e.target.value.toUpperCase())}
            onBlur={handleSymbolBlur}
            placeholder="e.g. AAPL, 0700.HK" />
        </Field>
      </div>
      <Field label="Name *">
        <input className={inputCls} required value={f.name}
          onChange={(e) => setField("name", e.target.value)}
          placeholder={nameLookupPending ? "Looking up…" : "e.g. Apple Inc."} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Shares *">
          <input className={inputCls} type="number" required min="0" step="0.0001"
            value={f.stockAmount} onChange={(e) => setField("stockAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => setField("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Invest Amount *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={f.investAmount} onChange={(e) => setField("investAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Fee">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.fee} onChange={(e) => setField("fee", parseFloat(e.target.value) || 0)} />
        </Field>
      </div>
      <p className="text-[11px] text-[--color-muted]">
        Use Finnhub ticker format: AAPL (US), 0700.HK (HK), 600519.SS (CN), 7203.T (JP), MC.PA (FR)
      </p>
      <div className="mt-1 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

export function CryptoForm({ initial, onSave, onCancel, saving }: {
  initial: CryptoFields;
  onSave: (d: CryptoFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const setField = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Name *">
          <input className={inputCls} required value={f.name}
            onChange={(e) => setField("name", e.target.value)} placeholder="e.g. Bitcoin" />
        </Field>
        <Field label="Symbol *">
          <input className={inputCls} required value={f.symbol}
            onChange={(e) => setField("symbol", e.target.value.toUpperCase())} placeholder="e.g. BTC" />
        </Field>
      </div>
      <Field label="Amount (coins) *">
        <input className={inputCls} type="number" required min="0" step="0.00000001"
          value={f.amount} onChange={(e) => setField("amount", parseFloat(e.target.value) || 0)} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Invest Amount *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={f.investAmount} onChange={(e) => setField("investAmount", parseFloat(e.target.value) || 0)} />
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => setField("currency", e.target.value)}>
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

export function FutureForm({ initial, onSave, onCancel, saving }: {
  initial: FutureFields;
  onSave: (d: FutureFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const setField = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));

  const setKind = (kind: FutureExchangeKind) => {
    setF((p) => ({
      ...p,
      exchangeKind: kind,
      exchange: FUTURE_EXCHANGES_BY_KIND[kind][0]!,
      symbol: kind === "CRYPTO_DEX" ? null : (p.symbol ?? ""),
      side: kind === "CRYPTO_DEX" ? null : (p.side ?? "LONG"),
      quantity: kind === "CRYPTO_DEX" ? null : (p.quantity ?? 0),
      entryPrice: kind === "CRYPTO_DEX" ? null : (p.entryPrice ?? 0),
      connectionAddress: kind === "CRYPTO_DEX" ? (p.connectionAddress ?? "") : null,
    }));
  };

  const isDex = f.exchangeKind === "CRYPTO_DEX";

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <Field label="Exchange Kind">
        <div className="flex gap-1.5 rounded-md bg-[--color-border]/30 p-1">
          {FUTURE_EXCHANGE_KINDS.map((k) => (
            <SegBtn key={k} label={FUTURE_EXCHANGE_KIND_LABELS[k]} active={f.exchangeKind === k} onClick={() => setKind(k)} />
          ))}
        </div>
      </Field>

      {isDex ? (
        <>
          <Field label="Exchange">
            <select className={selectCls} value={f.exchange}
              onChange={(e) => setField("exchange", e.target.value)}>
              {FUTURE_EXCHANGES_BY_KIND[f.exchangeKind].map((ex) => <option key={ex} value={ex}>{ex}</option>)}
            </select>
          </Field>
          <Field label="Wallet Address *">
            <input className={inputCls} required value={f.connectionAddress ?? ""}
              onChange={(e) => setField("connectionAddress", e.target.value)}
              placeholder={DEX_ADDRESS_PLACEHOLDERS[f.exchange] ?? "0x…"} />
          </Field>
          <p className="text-[11px] text-[--color-muted]">
            We&apos;ll auto-track all open positions for this address — no need to enter symbol or size.
          </p>
        </>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Exchange">
              <select className={selectCls} value={f.exchange}
                onChange={(e) => setField("exchange", e.target.value)}>
                {FUTURE_EXCHANGES_BY_KIND[f.exchangeKind].map((ex) => <option key={ex} value={ex}>{ex}</option>)}
              </select>
            </Field>
            <Field label="Symbol *">
              <input className={inputCls} required value={f.symbol ?? ""}
                onChange={(e) => setField("symbol", e.target.value.toUpperCase())}
                placeholder={f.exchangeKind === "SECURITY" ? "e.g. ES, NQ" : CEX_SYMBOL_EXAMPLES[f.exchange] ?? "e.g. BTCUSDT"} />
            </Field>
          </div>
          <Field label="Side *">
            <div className="flex gap-1 rounded-xl border border-[--color-border] p-1">
              {FUTURE_SIDES.map((s) => (
                <SegBtn key={s} label={s === "LONG" ? "Long" : "Short"}
                  active={f.side === s} onClick={() => setField("side", s)} />
              ))}
            </div>
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Quantity *">
              <input className={inputCls} type="number" required min="0" step="0.00000001"
                value={f.quantity ?? 0} onChange={(e) => setField("quantity", parseFloat(e.target.value) || 0)} />
            </Field>
            <Field label="Entry Price *">
              <input className={inputCls} type="number" required min="0" step="0.0001"
                value={f.entryPrice ?? 0} onChange={(e) => setField("entryPrice", parseFloat(e.target.value) || 0)} />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Leverage">
              <input className={inputCls} type="number" min="0" step="0.1"
                placeholder="Optional"
                value={f.leverage ?? ""}
                onChange={(e) => setField("leverage", e.target.value ? parseFloat(e.target.value) : null)} />
            </Field>
            <Field label="Currency">
              <select className={selectCls} value={f.currency} onChange={(e) => setField("currency", e.target.value)}>
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
          </div>
          <p className="text-[11px] text-[--color-muted]">
            {f.exchangeKind === "CRYPTO_CEX"
              ? `Enter the symbol exactly as ${f.exchange} lists it — each exchange has its own live price fetched directly from ${f.exchange}, not a shared/unified feed.`
              : "Live price lookup is best-effort for futures/continuous contracts and may be unavailable."}
          </p>
        </>
      )}

      <div className="mt-1 flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

export function CardForm({ initial, banks, onSave, onCancel, saving }: {
  initial: CardFields; banks: string[];
  onSave: (d: CardFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const setField = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  const isCredit = f.types.includes("Credit");

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Bank *">
          <ComboInput value={f.bank} onChange={(v) => setField("bank", v)}
            suggestions={banks} placeholder="e.g. HSBC, DBS, Citi" required />
        </Field>
        <Field label="Country / Region">
          <ComboInput value={f.countryRegion} onChange={(v) => setField("countryRegion", v)}
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
          onChange={(e) => setField("cardName", e.target.value)} placeholder="e.g. Premier Mastercard" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Network">
          <select className={selectCls} value={f.network}
            onChange={(e) => setField("network", e.target.value as CardNetwork)}>
            {CARD_NETWORKS.map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </Field>
        <Field label="Expiry Date">
          <input className={inputCls} type="month" value={f.expireDate}
            onChange={(e) => setField("expireDate", e.target.value)} />
        </Field>
      </div>
      {isCredit && (
        <>
          <Field label="Credit Limit">
            <div className="flex gap-2">
              <select
                className="w-20 shrink-0 rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 px-2 py-1.5 text-sm outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30 cursor-pointer"
                value={f.creditLimitCurrency ?? "HKD"}
                onChange={(e) => setField("creditLimitCurrency", e.target.value)}>
                {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
              <input className={`${inputCls} flex-1`} type="number" min="0" step="0.01"
                placeholder="Leave blank if unknown"
                value={f.creditLimit ?? ""}
                onChange={(e) => setField("creditLimit", e.target.value ? parseFloat(e.target.value) : null)} />
            </div>
          </Field>
          <Field label="Shared Credit">
            <div className="flex gap-5 pt-1">
              {([true, false] as const).map((v) => (
                <label key={String(v)} className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
                  <input type="radio" name="sharedCredit" checked={f.sharedCredit === v}
                    onChange={() => setField("sharedCredit", v)} />
                  {v ? "Shared" : "Dedicated"}
                </label>
              ))}
              <label className="flex cursor-pointer select-none items-center gap-1.5 text-sm">
                <input type="radio" name="sharedCredit" checked={f.sharedCredit === null}
                  onChange={() => setField("sharedCredit", null)} />
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

export function SalaryForm({ initial, regions, onSave, onCancel, saving }: {
  initial: SalaryFields; regions: string[];
  onSave: (d: SalaryFields) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);
  const setField = (k: string, v: unknown) => setF((p) => ({ ...p, [k]: v }));
  const numericField = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setField(k, parseFloat(e.target.value) || 0);

  const computed = f.livingExpense + f.houseRent + f.otherExpense;

  const MONTHS = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];

  return (
    <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <div className="grid grid-cols-3 gap-3">
        <Field label="Year *">
          <input className={inputCls} type="number" required min="2000" max="2100" step="1"
            value={f.year} onChange={(e) => setField("year", parseInt(e.target.value) || new Date().getFullYear())} />
        </Field>
        <Field label="Month *">
          <select className={selectCls} value={f.month} onChange={(e) => setField("month", parseInt(e.target.value))}>
            {MONTHS.map((m, i) => <option key={i + 1} value={i + 1}>{i + 1} – {m}</option>)}
          </select>
        </Field>
        <Field label="Currency">
          <select className={selectCls} value={f.currency} onChange={(e) => setField("currency", e.target.value)}>
            {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Region *">
        <ComboInput value={f.region} onChange={(v) => setField("region", v)}
          suggestions={regions} placeholder="e.g. Hong Kong SAR, Singapore" required />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Salary (Excl. Retirement / Pension) *">
          <input className={inputCls} type="number" required min="0" step="0.01"
            value={f.salary} onChange={numericField("salary")} />
        </Field>
        <Field label="Bonus">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.bonus} onChange={numericField("bonus")} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Retirement Savings (Employee)">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.retirementSavingEmployee} onChange={numericField("retirementSavingEmployee")} />
        </Field>
        <Field label="Retirement Savings (Employer)">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.retirementSavingEmployer} onChange={numericField("retirementSavingEmployer")} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Tax">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.tax} onChange={numericField("tax")} />
        </Field>
        <Field label="House Rent">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.houseRent} onChange={numericField("houseRent")} />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Living Expense">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.livingExpense} onChange={numericField("livingExpense")} />
        </Field>
        <Field label="Other Expense">
          <input className={inputCls} type="number" min="0" step="0.01"
            value={f.otherExpense} onChange={numericField("otherExpense")} />
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
