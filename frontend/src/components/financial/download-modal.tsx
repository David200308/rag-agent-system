"use client";

import { useState } from "react";
import { Download } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  type CashDeposit,
  type CryptoInvestment,
  type StockInvestment,
  type Card,
  type SalaryUsageRecord,
  formatAmount,
  formatPrice,
} from "@/types/financial";
import { Modal, SegBtn, SwitchRow } from "./shared-ui";
import { formatPercentOfTotal, formatExpiry } from "./utils";

type DownloadSection = "deposits" | "stocks" | "crypto" | "cards" | "salary";

const SECTION_LABELS: Record<DownloadSection, string> = {
  deposits: "Cash Deposits",
  stocks:   "Stocks",
  crypto:   "Crypto",
  cards:    "Cards",
  salary:   "Salary & Expense",
};

function buildMarkdown(
  sections: DownloadSection[],
  deposits: CashDeposit[],
  stocks: StockInvestment[],
  crypto: CryptoInvestment[],
  cards: Card[],
  salary: SalaryUsageRecord[],
  currency: string,
  grandTotal: number,
): string {
  const has = (s: DownloadSection) => sections.includes(s);
  const date = new Date().toISOString().slice(0, 10);
  const lines: string[] = [`# Financial Report — ${date}`, ""];

  const totalDep = has("deposits") ? deposits.reduce((s, d) => s + (d.convertedAmount ?? 0), 0) : 0;
  const totalStk = has("stocks")   ? stocks.reduce((s, st) => s + (st.convertedCurrentValue ?? st.convertedInvestAmount ?? 0), 0) : 0;
  const totalCry = has("crypto")   ? crypto.reduce((s, c) => s + (c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), 0) : 0;

  const financialSections = sections.filter((s) => s !== "cards" && s !== "salary");
  if (financialSections.length > 1) {
    lines.push("## Summary", "");
    lines.push(`| Category | Value (${currency}) | % of Total |`);
    lines.push("|---|---:|---:|");
    if (has("deposits")) lines.push(`| Cash Deposits | ${formatAmount(totalDep, currency)} | ${formatPercentOfTotal(totalDep, grandTotal)} |`);
    if (has("stocks"))   lines.push(`| Stock Investments | ${formatAmount(totalStk, currency)} | ${formatPercentOfTotal(totalStk, grandTotal)} |`);
    if (has("crypto"))   lines.push(`| Crypto Investments | ${formatAmount(totalCry, currency)} | ${formatPercentOfTotal(totalCry, grandTotal)} |`);
    lines.push(`| **Total** | **${formatAmount(grandTotal, currency)}** | **100%** |`);
    lines.push("");
  }

  if (has("deposits") && deposits.length > 0) {
    lines.push("## Cash Deposits", "");
    lines.push(`| Platform | Type | Country | F/X | Amount | ≈ ${currency} | % of Total |`);
    lines.push("|---|---|---|---|---:|---:|---:|");
    for (const d of deposits)
      lines.push(`| ${d.platform} | ${d.platformType} | ${d.countryRegion || "—"} | ${d.depositType} | ${formatAmount(d.amount, d.currency)} | ${formatAmount(d.convertedAmount, currency)} | ${formatPercentOfTotal(d.convertedAmount ?? 0, totalDep)} |`);
    lines.push("");
  }

  if (has("stocks") && stocks.length > 0) {
    lines.push("## Stock Investments", "");
    lines.push(`| Symbol | Name | Shares | Invested | Price | Value | ≈ ${currency} | P&L% | % of Total |`);
    lines.push("|---|---|---:|---:|---:|---:|---:|---:|---:|");
    for (const s of stocks) {
      const val = s.convertedCurrentValue ?? s.convertedInvestAmount ?? 0;
      lines.push(`| ${s.symbol} | ${s.name} | ${s.stockAmount} | ${formatAmount(s.investAmount, s.currency)} | ${s.currentPrice != null ? formatPrice(s.currentPrice) : "—"} | ${s.currentValue != null ? formatAmount(s.currentValue, s.priceCurrency ?? s.currency) : "—"} | ${formatAmount(val, currency)} | ${s.pnlPercent != null ? (s.pnlPercent >= 0 ? "+" : "") + s.pnlPercent.toFixed(2) + "%" : "—"} | ${formatPercentOfTotal(val, totalStk)} |`);
    }
    lines.push("");
  }

  if (has("crypto") && crypto.length > 0) {
    lines.push("## Crypto Investments", "");
    lines.push(`| Symbol | Name | Amount | Invested | Price (USD) | Value (USD) | ≈ ${currency} | P&L% | % of Total |`);
    lines.push("|---|---|---:|---:|---:|---:|---:|---:|---:|");
    for (const c of crypto) {
      const val = c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0;
      lines.push(`| ${c.symbol} | ${c.name} | ${c.amount} | ${formatAmount(c.investAmount, c.currency)} | ${c.currentPrice != null ? formatAmount(c.currentPrice, "USD") : "—"} | ${c.currentValue != null ? formatAmount(c.currentValue, "USD") : "—"} | ${formatAmount(val, currency)} | ${c.pnlPercent != null ? (c.pnlPercent >= 0 ? "+" : "") + c.pnlPercent.toFixed(2) + "%" : "—"} | ${formatPercentOfTotal(val, totalCry)} |`);
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

  if (has("salary") && salary.length > 0) {
    const sorted = [...salary].sort((a, b) => a.year !== b.year ? a.year - b.year : a.month - b.month);
    lines.push("## Salary & Expense", "");
    lines.push("| Year/Month | Region | Currency | Salary | Bonus | Retirement (Emp.) | Retirement (Emplr.) | Tax | House Rent | Living Expense | Other Expense | Total Expense |");
    lines.push("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
    for (const r of sorted) {
      const f = (v: number) => v === 0 ? "—" : formatAmount(v, r.currency);
      lines.push(`| ${r.year}/${String(r.month).padStart(2,"0")} | ${r.region} | ${r.currency} | ${f(r.salary)} | ${f(r.bonus)} | ${f(r.retirementSavingEmployee)} | ${f(r.retirementSavingEmployer)} | ${f(r.tax)} | ${f(r.houseRent)} | ${f(r.livingExpense)} | ${f(r.otherExpense)} | ${f(r.totalExpense)} |`);
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
  salary: SalaryUsageRecord[],
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

  const financialSections = sections.filter((s) => s !== "cards" && s !== "salary");
  const tDep = has("deposits") ? deposits.reduce((s, d) => s + (d.convertedAmount ?? 0), 0) : 0;
  const tStk = has("stocks")   ? stocks.reduce((s, st) => s + (st.convertedCurrentValue ?? st.convertedInvestAmount ?? 0), 0) : 0;
  const tCry = has("crypto")   ? crypto.reduce((s, c) => s + (c.convertedCurrentValue ?? c.convertedInvestAmount ?? 0), 0) : 0;
  let summaryHtml = "";
  if (financialSections.length > 1) {
    summaryHtml = `<h2>Summary</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Category</th><th style="${th}">Value (${currency})</th><th style="${th}">% of Total</th>
</tr></thead><tbody>
  ${has("deposits") ? `<tr><td style="${td}">Cash Deposits</td><td style="${tdR}">${formatAmount(tDep, currency)}</td><td style="${tdR}">${formatPercentOfTotal(tDep, grandTotal)}</td></tr>` : ""}
  ${has("stocks")   ? `<tr><td style="${td}">Stock Investments</td><td style="${tdR}">${formatAmount(tStk, currency)}</td><td style="${tdR}">${formatPercentOfTotal(tStk, grandTotal)}</td></tr>` : ""}
  ${has("crypto")   ? `<tr><td style="${td}">Crypto Investments</td><td style="${tdR}">${formatAmount(tCry, currency)}</td><td style="${tdR}">${formatPercentOfTotal(tCry, grandTotal)}</td></tr>` : ""}
  <tr><td style="${td};font-weight:bold">Total</td><td style="${tdR};font-weight:bold">${formatAmount(grandTotal, currency)}</td><td style="${tdR};font-weight:bold">100%</td></tr>
</tbody></table>`;
  }

  const depRows = has("deposits") ? deposits.map(d => `<tr>
    <td style="${td}">${d.platform}</td><td style="${td}">${d.platformType}</td>
    <td style="${td}">${d.countryRegion || "—"}</td><td style="${td}">${d.depositType}</td>
    <td style="${tdR}">${formatAmount(d.amount, d.currency)}</td>
    <td style="${tdR}">${formatAmount(d.convertedAmount, currency)}</td>
    <td style="${tdR}">${formatPercentOfTotal(d.convertedAmount ?? 0, tDep)}</td></tr>`).join("") : "";

  const stkRows = has("stocks") ? stocks.map(s => {
    const val = s.convertedCurrentValue ?? s.convertedInvestAmount ?? 0;
    return `<tr>
    <td style="${td}">${s.symbol}</td><td style="${td}">${s.name}</td>
    <td style="${tdR}">${s.stockAmount}</td><td style="${tdR}">${formatAmount(s.investAmount, s.currency)}</td>
    <td style="${tdR}">${s.currentPrice != null ? formatPrice(s.currentPrice) : "—"}</td>
    <td style="${tdR}">${s.currentValue != null ? formatAmount(s.currentValue, s.priceCurrency ?? s.currency) : "—"}</td>
    <td style="${tdR}">${formatAmount(val, currency)}</td>
    <td style="${tdR}">${s.pnlPercent != null ? (s.pnlPercent >= 0 ? "+" : "") + s.pnlPercent.toFixed(2) + "%" : "—"}</td>
    <td style="${tdR}">${formatPercentOfTotal(val, tStk)}</td></tr>`;
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
    <td style="${tdR}">${formatPercentOfTotal(val, tCry)}</td></tr>`;
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
${has("salary") && salary.length > 0 ? (() => {
  const sorted = [...salary].sort((a, b) => a.year !== b.year ? a.year - b.year : a.month - b.month);
  const f = (v: number, cur: string) => v === 0 ? "—" : new Intl.NumberFormat("en-US", { style: "currency", currency: cur, maximumFractionDigits: 2 }).format(v);
  const rows = sorted.map(r => `<tr>
    <td style="${td}">${r.year}/${String(r.month).padStart(2,"0")}</td>
    <td style="${td}">${r.region}</td><td style="${td}">${r.currency}</td>
    <td style="${tdR}">${f(r.salary,r.currency)}</td><td style="${tdR}">${f(r.bonus,r.currency)}</td>
    <td style="${tdR}">${f(r.retirementSavingEmployee,r.currency)}</td>
    <td style="${tdR}">${f(r.retirementSavingEmployer,r.currency)}</td>
    <td style="${tdR}">${f(r.tax,r.currency)}</td><td style="${tdR}">${f(r.houseRent,r.currency)}</td>
    <td style="${tdR}">${f(r.livingExpense,r.currency)}</td><td style="${tdR}">${f(r.otherExpense,r.currency)}</td>
    <td style="${tdR};font-weight:bold">${f(r.totalExpense,r.currency)}</td></tr>`).join("");
  return `<h2>Salary &amp; Expense</h2><table style="${ts}"><thead><tr>
  <th style="${th}">Year/Month</th><th style="${th}">Region</th><th style="${th}">Currency</th>
  <th style="${th}">Salary</th><th style="${th}">Bonus</th>
  <th style="${th}">Retirement (Emp.)</th><th style="${th}">Retirement (Emplr.)</th>
  <th style="${th}">Tax</th><th style="${th}">House Rent</th>
  <th style="${th}">Living Expense</th><th style="${th}">Other Expense</th>
  <th style="${th}">Total Expense</th>
</tr></thead><tbody>${rows}</tbody></table>`;
})() : ""}
</body></html>`;

  const w = window.open("", "_blank");
  if (w) { w.document.write(html); w.document.close(); }
}

export function DownloadModal({ deposits, stocks, crypto, cards, salary, currency, grandTotal, onClose }: {
  deposits: CashDeposit[]; stocks: StockInvestment[]; crypto: CryptoInvestment[];
  cards: Card[]; salary: SalaryUsageRecord[];
  currency: string; grandTotal: number; onClose: () => void;
}) {
  const [sections, setSections] = useState<DownloadSection[]>(["deposits", "stocks", "crypto", "cards", "salary"]);
  const [format,   setFormat]   = useState<"markdown" | "pdf">("markdown");
  const [mode,     setMode]     = useState<"combined" | "separate">("combined");

  const toggle = (s: DownloadSection) =>
    setSections((p) => p.includes(s) ? p.filter((x) => x !== s) : [...p, s]);

  const handleDownload = () => {
    if (sections.length === 0) return;
    const date = new Date().toISOString().slice(0, 10);
    if (mode === "combined") {
      if (format === "markdown") {
        downloadFile(buildMarkdown(sections, deposits, stocks, crypto, cards, salary, currency, grandTotal), `financial-${date}.md`, "text/markdown");
      } else {
        exportPdf(sections, deposits, stocks, crypto, cards, salary, currency, grandTotal, "Financial Report");
      }
    } else {
      for (const section of sections) {
        if (format === "markdown") {
          downloadFile(buildMarkdown([section], deposits, stocks, crypto, cards, salary, currency, grandTotal), `financial-${section}-${date}.md`, "text/markdown");
        } else {
          exportPdf([section], deposits, stocks, crypto, cards, salary, currency, grandTotal, SECTION_LABELS[section]);
        }
      }
    }
    onClose();
  };

  return (
    <Modal title="Download Report" onClose={onClose}>
      <div className="flex flex-col gap-4">

        {/* Sections */}
        <div>
          <p className="mb-1 text-xs text-[--color-muted]">Sections</p>
          <div className="divide-y divide-[--color-border] rounded-xl border border-[--color-border] px-3">
            {(["deposits", "stocks", "crypto", "cards", "salary"] as DownloadSection[]).map((s) => (
              <SwitchRow key={s} label={SECTION_LABELS[s]}
                checked={sections.includes(s)} onChange={() => toggle(s)} />
            ))}
          </div>
        </div>

        {/* Format */}
        <div>
          <p className="mb-1 text-xs text-[--color-muted]">Format</p>
          <div className="flex gap-1 rounded-xl border border-[--color-border] p-1">
            <SegBtn label="Markdown (.md)" active={format === "markdown"} onClick={() => setFormat("markdown")} />
            <SegBtn label="PDF (print)"    active={format === "pdf"}      onClick={() => setFormat("pdf")} />
          </div>
        </div>

        {/* Output */}
        <div>
          <p className="mb-1 text-xs text-[--color-muted]">Output</p>
          <div className="flex gap-1 rounded-xl border border-[--color-border] p-1">
            <SegBtn label="Combined"       active={mode === "combined"}  onClick={() => setMode("combined")} />
            <SegBtn label="Separate files" active={mode === "separate"} onClick={() => setMode("separate")} />
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
