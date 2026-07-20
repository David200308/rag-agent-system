"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Pencil, Trash2, RefreshCw, ChevronDown, Eye, EyeOff, Search, Bell } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  CURRENCIES,
  type CashDeposit,
  type CryptoInvestment,
  type Currency,
  type StockInvestment,
  type Card,
  type SalaryUsageRecord,
  formatAmount,
  formatPrice,
} from "@/types/financial";
import {
  type Tab,
  apiFetch, apiCreate, apiUpdate, apiDelete, apiRefreshPrices,
  fetchExchangeRates, fetchUserCurrency, saveUserCurrency,
  sortData, groupStocksBySymbol, useSort, unique,
  formatPercentOfTotal, NETWORK_COLORS, TYPE_COLORS, formatExpiry, toTradingViewSymbol,
} from "./utils";
import { Th, Modal, PnlBadge, SummaryCard, SymbolIcon } from "./shared-ui";
import { SymbolHoverChart } from "./symbol-hover-chart";
import {
  type DepositFields, type StockFields, type CryptoFields, type CardFields, type SalaryFields,
  emptyDeposit, emptyStock, emptyCrypto, emptyCard, emptySalary,
  DepositForm, StockForm, CryptoForm, CardForm, SalaryForm,
} from "./forms";
import { StockGroupRows } from "./stock-table";
import { SalaryLineChart } from "./salary-chart";
import { DownloadModal } from "./download-modal";
import { AlertModal } from "./AlertModal";

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

  const depositSort = useSort({ column: "platform", dir: "asc" });
  const stockSort   = useSort({ column: "symbol",   dir: "asc" });
  const cryptoSort  = useSort({ column: "symbol",   dir: "asc" });
  const cardSort    = useSort({ column: "bank",     dir: "asc" });
  const salarySort  = useSort({ column: "year",     dir: "desc" });
  const [salaryFrom, setSalaryFrom] = useState("");
  const [salaryTo,   setSalaryTo]   = useState("");

  const [searchTerm, setSearchTerm] = useState("");
  useEffect(() => { setSearchTerm(""); }, [tab]);

  const [expandedStocks, setExpandedStocks] = useState<Set<string>>(new Set());
  const toggleStockGroup = (symbol: string) => {
    setExpandedStocks((prev) => {
      const next = new Set(prev);
      if (next.has(symbol)) next.delete(symbol);
      else next.add(symbol);
      return next;
    });
  };

  const [modal, setModal] = useState<
    | { mode: "add-deposit" }   | { mode: "edit-deposit";  item: CashDeposit }
    | { mode: "add-stock" }     | { mode: "edit-stock";    item: StockInvestment }
    | { mode: "add-crypto" }    | { mode: "edit-crypto";   item: CryptoInvestment }
    | { mode: "add-card" }      | { mode: "edit-card";     item: Card }
    | { mode: "add-salary" }    | { mode: "edit-salary";   item: SalaryUsageRecord }
    | { mode: "alert"; symbol: string; assetType: "CRYPTO" | "STOCK" }
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

  const totalDeposits = deposits.reduce((s, d) => s + (d.convertedAmount ?? 0), 0);
  const totalStocks   = stocks.reduce((s, st) => s + (st.convertedCurrentValue ?? st.convertedInvestAmount ?? 0), 0);
  const totalCrypto   = crypto.reduce((s, c) => s + (c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), 0);

  const stocksInvested  = stocks.reduce((s, st) => s + st.convertedInvestAmount, 0);
  const stocksPnlPct    = stocks.some((st) => st.pnlPercent != null) && stocksInvested > 0
    ? (totalStocks - stocksInvested) / stocksInvested * 100
    : null;

  const cryptoInvested  = crypto.reduce((s, c) => s + c.convertedInvestAmount, 0);
  const cryptoPnlPct    = crypto.some((c) => c.pnlPercent != null) && cryptoInvested > 0
    ? (totalCrypto - cryptoInvested) / cryptoInvested * 100
    : null;

  const grandTotal = totalDeposits + totalStocks + totalCrypto;

  const toUSD = (amount: number): number | null => {
    if (defaultCurrency === "USD") return null;
    const rate = fxRates[defaultCurrency];
    if (!rate) return null;
    return amount / rate;
  };

  const summaryItems = [
    { label: "Cash Deposits",      value: totalDeposits, pnlPercent: null,         pnlAmount: null as number | null,                             share: grandTotal > 0 ? totalDeposits / grandTotal * 100 : 0, usdValue: null as number | null },
    { label: "Stock Investments",  value: totalStocks,   pnlPercent: stocksPnlPct, pnlAmount: stocksPnlPct != null ? totalStocks - stocksInvested : null, share: grandTotal > 0 ? totalStocks  / grandTotal * 100 : 0, usdValue: toUSD(totalStocks) },
    { label: "Crypto Investments", value: totalCrypto,   pnlPercent: cryptoPnlPct, pnlAmount: cryptoPnlPct != null ? totalCrypto - cryptoInvested : null, share: grandTotal > 0 ? totalCrypto  / grandTotal * 100 : 0, usdValue: toUSD(totalCrypto) },
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

  const sortedDeposits = sortData(deposits, depositSort.sort);
  const sortedStocks   = sortData(stocks,   stockSort.sort);
  const sortedCrypto   = sortData(crypto,   cryptoSort.sort);
  const sortedCards    = sortData(cards,    cardSort.sort);
  const sortedSalary   = sortData(salaryRecords, salarySort.sort);

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
  const stockGroups = sortData(groupStocksBySymbol(filteredStocks), stockSort.sort);
  const filteredCards = q
    ? sortedCards.filter((c) =>
        [c.bank, c.countryRegion, c.cardName, c.network, c.types.join(" ")]
          .some((v) => v?.toLowerCase().includes(q)))
    : sortedCards;
  const salaryInRange = sortedSalary.filter((r) => {
    const ym = `${r.year}-${String(r.month).padStart(2, "0")}`;
    if (salaryFrom && ym < salaryFrom) return false;
    if (salaryTo   && ym > salaryTo)   return false;
    return true;
  });
  const filteredSalary = q
    ? salaryInRange.filter((r) =>
        [r.region, r.currency, String(r.year), String(r.month).padStart(2, "0")]
          .some((v) => v?.toLowerCase().includes(q)))
    : salaryInRange;

  const maskAmount = (formatted: string) => hideAmounts ? "***" : formatted;

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
          <h1 className="text-base font-semibold">
            Financial
            <span className="ml-2 text-sm font-normal text-[--color-muted]">
              (Total: {maskAmount(formatAmount(grandTotal, defaultCurrency))})
            </span>
          </h1>
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
              currency={defaultCurrency} pnlPercent={item.pnlPercent} pnlAmount={item.pnlAmount} share={item.share} usdValue={item.usdValue} hide={hideAmounts} />
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
                currency={defaultCurrency} pnlPercent={item.pnlPercent} pnlAmount={item.pnlAmount} share={item.share} usdValue={item.usdValue} hide={hideAmounts} />
            </div>
          ))}
        </div>

        <div className="mt-4 flex items-center gap-1">
          <button className={tabCls("deposits")} onClick={() => setTab("deposits")}>Cash Deposits</button>
          <button className={tabCls("stocks")}   onClick={() => setTab("stocks")}>Stocks</button>
          <button className={tabCls("crypto")}   onClick={() => setTab("crypto")}>Crypto</button>
          <button className={tabCls("cards")}    onClick={() => setTab("cards")}>Cards</button>
          <button className={tabCls("salary")}   onClick={() => setTab("salary")}>Salary &amp; Expense</button>
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
                      <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">% of Total</th>
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
                        <td className="px-4 py-3 text-right tabular-nums">{maskAmount(formatAmount(d.amount, d.currency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">{maskAmount(formatAmount(d.convertedAmount, defaultCurrency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums text-xs text-[--color-muted]">{formatPercentOfTotal(d.convertedAmount ?? 0, totalDeposits)}</td>
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
                      <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">Avg Price</th>
                      <Th label="Price"    column="currentPrice" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label="Value"    column="currentValue" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label={`≈ ${defaultCurrency}`} column="convertedCurrentValue" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <Th label="P&L%" column="pnlPercent" sort={stockSort.sort} onSort={stockSort.toggle} right />
                      <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">% of Total</th>
                      <th className="px-4 py-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {stockGroups.map((group) => (
                      <StockGroupRows
                        key={group.symbol}
                        group={group}
                        expanded={expandedStocks.has(group.symbol)}
                        onToggle={() => toggleStockGroup(group.symbol)}
                        defaultCurrency={defaultCurrency}
                        total={totalStocks}
                        hideAmounts={hideAmounts}
                        onEdit={(s) => setModal({ mode: "edit-stock", item: s })}
                        onDelete={(id) => void deleteStock(id)}
                        onAlert={(symbol) => setModal({ mode: "alert", symbol, assetType: "STOCK" })}
                      />
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
                      <th className="px-4 py-2.5 text-right text-xs font-medium text-[--color-muted]">Avg Price</th>
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
                        <td className="px-4 py-3 font-semibold">
                          <span className="inline-flex items-center gap-1.5">
                            <SymbolIcon logoUrl={c.logoUrl} symbol={c.symbol} />
                            <SymbolHoverChart tvSymbol={toTradingViewSymbol(c.symbol, "crypto")}>
                              {c.symbol}
                            </SymbolHoverChart>
                          </span>
                        </td>
                        <td className="px-4 py-3">{c.name}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{maskAmount(String(c.amount))}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{maskAmount(formatAmount(c.investAmount, c.currency))}</td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.amount > 0
                            ? <span>{maskAmount(formatPrice(c.investAmount / c.amount))} <span className="text-[10px] text-[--color-muted]">{c.currency}</span></span>
                            : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.currentPrice != null ? maskAmount(formatAmount(c.currentPrice, "USD")) : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.currentValue != null ? maskAmount(formatAmount(c.currentValue, "USD")) : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">
                          {c.convertedCurrentValue != null
                            ? maskAmount(formatAmount(c.convertedCurrentValue, defaultCurrency))
                            : maskAmount(formatAmount(c.convertedInvestAmount, defaultCurrency))}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {c.pnlPercent != null
                            ? <PnlBadge percent={c.pnlPercent} amount={(c.convertedCurrentValue ?? c.convertedInvestAmount) - c.convertedInvestAmount} currency={defaultCurrency} hide={hideAmounts} />
                            : <span className="text-[--color-muted]">—</span>}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums text-xs text-[--color-muted]">
                          {formatPercentOfTotal((c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), totalCrypto)}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-1">
                            <Button size="icon" variant="ghost" className="h-7 w-7"
                              onClick={() => setModal({ mode: "alert", symbol: c.symbol, assetType: "CRYPTO" })}>
                              <Bell className="h-3.5 w-3.5 text-[--color-muted]" />
                            </Button>
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

            {/* ── Salary & Expense ── */}
            {tab === "salary" && (
              <div className="flex flex-col gap-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs text-[--color-muted]">From</span>
                  <input type="month" value={salaryFrom}
                    onChange={(e) => setSalaryFrom(e.target.value)}
                    className="rounded-md border border-[--color-border] bg-[--color-surface] px-2.5 py-1.5 text-xs outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30" />
                  <span className="text-xs text-[--color-muted]">To</span>
                  <input type="month" value={salaryTo}
                    onChange={(e) => setSalaryTo(e.target.value)}
                    className="rounded-md border border-[--color-border] bg-[--color-surface] px-2.5 py-1.5 text-xs outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30" />
                  {(salaryFrom || salaryTo) && (
                    <button onClick={() => { setSalaryFrom(""); setSalaryTo(""); }}
                      className="text-xs text-[--color-muted] hover:text-red-500">
                      Clear
                    </button>
                  )}
                </div>
                {filteredSalary.length === 0 ? (
                  <p className="py-12 text-center text-sm text-[--color-muted]">
                    {q ? `No records matching "${searchTerm}".` : "No salary records yet."}
                  </p>
                ) : (
                  <>
                    <SalaryLineChart records={filteredSalary} hide={hideAmounts} />
                    <div className="overflow-x-auto rounded-xl border border-[--color-border]">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className={thCls}>
                            <Th label="Year / Month"      column="year"          sort={salarySort.sort} onSort={salarySort.toggle} />
                            <Th label="Region / Currency" column="region"        sort={salarySort.sort} onSort={salarySort.toggle} />
                            <Th label="Salary (Excl. Retirement)" column="salary"  sort={salarySort.sort} onSort={salarySort.toggle} right />
                            <Th label="Bonus"              column="bonus"         sort={salarySort.sort} onSort={salarySort.toggle} right />
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
                              : new Intl.NumberFormat("en-US", { style: "currency", currency: r.currency, maximumFractionDigits: 2 }).format(v);
                            return (
                              <tr key={r.id} className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
                                <td className="px-4 py-3 font-medium tabular-nums">
                                  {r.year}/{String(r.month).padStart(2, "0")}
                                </td>
                                <td className="px-4 py-3 text-[--color-muted]">{r.region} / {r.currency}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.salary))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.bonus))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.retirementSavingEmployee))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.retirementSavingEmployer))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.tax))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.houseRent))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.livingExpense))}</td>
                                <td className="px-4 py-3 text-right tabular-nums">{maskAmount(fmtS(r.otherExpense))}</td>
                                <td className="px-4 py-3 text-right tabular-nums font-semibold">{maskAmount(fmtS(r.totalExpense))}</td>
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
                    <p className="text-[11px] text-[--color-muted]">
                      * House Rent: some months are paid in the following month.
                    </p>
                  </>
                )}
              </div>
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
                            ? <span>{c.creditLimitCurrency && <span className="mr-1 text-xs text-[--color-muted]">{c.creditLimitCurrency}</span>}{maskAmount(c.creditLimit.toLocaleString("en-US", { minimumFractionDigits: 0, maximumFractionDigits: 0 }))}</span>
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

      {modal?.mode === "alert" && (
        <AlertModal symbol={modal.symbol} assetType={modal.assetType} onClose={() => setModal(null)} />
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
          salary={salaryRecords}
          currency={defaultCurrency}
          grandTotal={grandTotal}
          onClose={() => setShowDownload(false)}
        />
      )}
    </div>
  );
}
