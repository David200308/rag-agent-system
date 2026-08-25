import { ChevronDown, ChevronRight, Pencil, Trash2, Bell } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { STOCK_TYPE_LABELS, formatAmount, formatPrice, type StockInvestment } from "@/types/financial";
import { PnlBadge, SymbolIcon } from "./shared-ui";
import { SymbolHoverChart } from "./symbol-hover-chart";
import { type StockGroup, formatPercentOfTotal, toTradingViewSymbol } from "./utils";

function StockDataCells({ row, defaultCurrency, total, hideAmounts }: {
  row: {
    stockAmount: number; investAmount: number; currency: string; avgPrice: number | null;
    currentPrice: number | null; priceCurrency: string | null; currentValue: number | null;
    convertedCurrentValue: number | null; convertedInvestAmount: number; pnlPercent: number | null;
  };
  defaultCurrency: string; total: number; hideAmounts: boolean;
}) {
  const maskAmount = (formatted: string) => hideAmounts ? "***" : formatted;
  const convertedValue = row.convertedCurrentValue ?? row.convertedInvestAmount;
  return (
    <>
      <td className="px-4 py-3 text-right tabular-nums">{maskAmount(String(row.stockAmount))}</td>
      <td className="px-4 py-3 text-right tabular-nums">{maskAmount(formatAmount(row.investAmount, row.currency))}</td>
      <td className="px-4 py-3 text-right tabular-nums">
        {row.avgPrice != null
          ? <span>{maskAmount(formatPrice(row.avgPrice))} <span className="text-[10px] text-[--color-muted]">{row.currency}</span></span>
          : <span className="text-[--color-muted]">—</span>}
      </td>
      <td className="px-4 py-3 text-right tabular-nums">
        {row.currentPrice != null
          ? <span>{maskAmount(formatPrice(row.currentPrice))} <span className="text-[10px] text-[--color-muted]">{row.priceCurrency}</span></span>
          : <span className="text-[--color-muted]">—</span>}
      </td>
      <td className="px-4 py-3 text-right tabular-nums">
        {row.currentValue != null ? maskAmount(formatAmount(row.currentValue, row.priceCurrency ?? row.currency)) : <span className="text-[--color-muted]">—</span>}
      </td>
      <td className="px-4 py-3 text-right tabular-nums text-[--color-muted]">{maskAmount(formatAmount(convertedValue, defaultCurrency))}</td>
      <td className="px-4 py-3 text-right tabular-nums">
        {row.pnlPercent != null
          ? <PnlBadge percent={row.pnlPercent} amount={convertedValue - row.convertedInvestAmount} currency={defaultCurrency} hide={hideAmounts} />
          : <span className="text-[--color-muted]">—</span>}
      </td>
      <td className="px-4 py-3 text-right tabular-nums text-xs text-[--color-muted]">{formatPercentOfTotal(convertedValue, total)}</td>
    </>
  );
}

export function StockGroupRows({ group, expanded, onToggle, defaultCurrency, total, hideAmounts, onEdit, onDelete, onAlert }: {
  group: StockGroup; expanded: boolean; onToggle: () => void;
  defaultCurrency: string; total: number; hideAmounts: boolean;
  onEdit: (s: StockInvestment) => void; onDelete: (id: string) => void; onAlert: (symbol: string) => void;
}) {
  if (group.rows.length === 1) {
    const s = group.rows[0]!;
    return (
      <tr className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20">
        <td className="px-4 py-3">
          <span className="inline-flex items-center gap-1.5">
            <SymbolIcon logoUrl={s.logoUrl} symbol={s.symbol} />
            <SymbolHoverChart tvSymbol={toTradingViewSymbol(s.symbol, "stock", s.stockType)}>
              <span className="font-semibold">{s.symbol}</span>
            </SymbolHoverChart>
          </span>
          <span className="ml-1.5 text-[11px] text-[--color-muted]">{s.broker}</span>
        </td>
        <td className="px-4 py-3">{s.name}</td>
        <td className="px-4 py-3 text-xs text-[--color-muted]">{STOCK_TYPE_LABELS[s.stockType]}</td>
        <StockDataCells row={{ ...s, avgPrice: group.avgPrice }} defaultCurrency={defaultCurrency} total={total} hideAmounts={hideAmounts} />
        <td className="px-4 py-3">
          <div className="flex justify-end gap-1">
            <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => onAlert(s.symbol)}>
              <Bell className="h-3.5 w-3.5 text-[--color-muted]" />
            </Button>
            <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => onEdit(s)}>
              <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
            </Button>
            <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => onDelete(s.id)}>
              <Trash2 className="h-3.5 w-3.5 text-red-400" />
            </Button>
          </div>
        </td>
      </tr>
    );
  }

  return (
    <>
      <tr className="border-b border-[--color-border]/50 hover:bg-[--color-border]/20 cursor-pointer" onClick={onToggle}>
        <td className="px-4 py-3 font-semibold">
          <span className="inline-flex items-center gap-1">
            {expanded ? <ChevronDown className="h-3.5 w-3.5 text-[--color-muted]" /> : <ChevronRight className="h-3.5 w-3.5 text-[--color-muted]" />}
            <SymbolIcon logoUrl={group.logoUrl} symbol={group.symbol} />
            <SymbolHoverChart tvSymbol={toTradingViewSymbol(group.symbol, "stock", group.stockType)}>
              {group.symbol}
            </SymbolHoverChart>
            <span className="text-[10px] font-normal text-[--color-muted]">· {group.rows.length} brokers</span>
          </span>
        </td>
        <td className="px-4 py-3">{group.name}</td>
        <td className="px-4 py-3 text-xs text-[--color-muted]">{STOCK_TYPE_LABELS[group.stockType]}</td>
        <StockDataCells row={group} defaultCurrency={defaultCurrency} total={total} hideAmounts={hideAmounts} />
        <td className="px-4 py-3">
          <div className="flex justify-end">
            <Button size="icon" variant="ghost" className="h-7 w-7"
              onClick={(e) => { e.stopPropagation(); onAlert(group.symbol); }}>
              <Bell className="h-3.5 w-3.5 text-[--color-muted]" />
            </Button>
          </div>
        </td>
      </tr>
      {expanded && group.rows.map((s) => (
        <tr key={s.id} className="border-b border-[--color-border]/50 bg-[--color-border]/10 hover:bg-[--color-border]/20">
          <td className="px-4 py-3 pl-9 text-[--color-muted]">↳ {s.broker}</td>
          <td className="px-4 py-3" />
          <td className="px-4 py-3" />
          <StockDataCells
            row={{ ...s, avgPrice: s.stockAmount > 0 ? (s.investAmount + s.fee) / s.stockAmount : null }}
            defaultCurrency={defaultCurrency} total={total} hideAmounts={hideAmounts}
          />
          <td className="px-4 py-3">
            <div className="flex justify-end gap-1">
              <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => onEdit(s)}>
                <Pencil className="h-3.5 w-3.5 text-[--color-muted]" />
              </Button>
              <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => onDelete(s.id)}>
                <Trash2 className="h-3.5 w-3.5 text-red-400" />
              </Button>
            </div>
          </td>
        </tr>
      ))}
    </>
  );
}
