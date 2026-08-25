import { type SalaryUsageRecord } from "@/types/financial";

export function SalaryLineChart({ records, hide }: { records: SalaryUsageRecord[]; hide: boolean }) {
  const sorted = [...records].sort((a, b) =>
    a.year !== b.year ? a.year - b.year : a.month - b.month,
  );

  const W = 800, H = 200;
  const pad = { top: 16, right: 16, bottom: 38, left: 64 };
  const iW = W - pad.left - pad.right;
  const iH = H - pad.top - pad.bottom;

  const series = [
    { getValue: (r: SalaryUsageRecord) => r.salary + r.bonus, color: "#3b82f6", label: "Salary + Bonus" },
    { getValue: (r: SalaryUsageRecord) => r.totalExpense,      color: "#f97316", label: "Total Expense" },
  ];

  const allVals = sorted.flatMap((r) => series.map((s) => s.getValue(r)));
  const maxV = Math.max(...allVals, 1) * 1.1;

  const xPos = (i: number) =>
    sorted.length === 1 ? pad.left + iW / 2 : pad.left + (i / (sorted.length - 1)) * iW;
  const yPos = (v: number) => pad.top + iH - (v / maxV) * iH;

  const compact = (v: number) => {
    if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
    if (v >= 1_000) return `${Math.round(v / 1_000)}K`;
    return String(Math.round(v));
  };

  const yTicks = Array.from({ length: 5 }, (_, i) => (maxV * i) / 4);

  const totalSalaryBonus = records.reduce((s, r) => s + r.salary + r.bonus, 0);
  const totalExpense     = records.reduce((s, r) => s + r.totalExpense, 0);
  const currency = records[0]?.currency ?? "USD";
  const fmtTotal = (v: number) => hide ? "***"
    : new Intl.NumberFormat("en-US", { style: "currency", currency, maximumFractionDigits: 2 }).format(v);

  const chartInner = (
    <>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ height: 200 }}>
        {yTicks.map((v, i) => (
          <g key={i}>
            <line x1={pad.left} x2={W - pad.right} y1={yPos(v)} y2={yPos(v)}
              stroke="currentColor" strokeOpacity={0.08} strokeWidth={1} />
            <text x={pad.left - 6} y={yPos(v) + 4} textAnchor="end"
              fontSize={10} fill="currentColor" fillOpacity={0.45}>
              {hide ? "***" : compact(v)}
            </text>
          </g>
        ))}
        {sorted.map((r, i) => (
          <text key={i} x={xPos(i)} y={H - 4} textAnchor="middle"
            fontSize={9} fill="currentColor" fillOpacity={0.45}>
            {r.year}/{String(r.month).padStart(2, "0")}
          </text>
        ))}
        {series.map((s) => (
          <g key={s.label}>
            {sorted.length > 1 && (
              <polyline
                points={sorted.map((r, i) => `${xPos(i)},${yPos(s.getValue(r))}`).join(" ")}
                fill="none" stroke={s.color} strokeWidth={2}
                strokeLinecap="round" strokeLinejoin="round" />
            )}
            {sorted.map((r, i) => (
              <circle key={i} cx={xPos(i)} cy={yPos(s.getValue(r))} r={3.5} fill={s.color} />
            ))}
          </g>
        ))}
      </svg>
      <div className="mt-2 flex justify-center gap-5">
        {series.map((s) => (
          <span key={s.label} className="flex items-center gap-1.5 text-xs text-[--color-muted]">
            <span className="inline-block h-2 w-5 rounded-full" style={{ backgroundColor: s.color }} />
            {s.label}
          </span>
        ))}
      </div>
    </>
  );

  return (
    <>
      {/* Mobile: 2-col summary cards + full-width chart */}
      <div className="flex flex-col gap-4 sm:hidden">
        <div className="grid grid-cols-2 gap-4">
          <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3">
            <p className="text-xs text-[--color-muted]">Total Salary &amp; Bonus</p>
            <p className="mt-1 text-base font-semibold tabular-nums">{fmtTotal(totalSalaryBonus)}</p>
          </div>
          <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-4 py-3">
            <p className="text-xs text-[--color-muted]">Total Expense</p>
            <p className="mt-1 text-base font-semibold tabular-nums">{fmtTotal(totalExpense)}</p>
          </div>
        </div>
        <div className="rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-4">
          {chartInner}
        </div>
      </div>

      {/* Desktop: 1/3 stacked cards + 2/3 chart */}
      <div className="hidden sm:grid sm:grid-cols-3 sm:gap-4">
        <div className="flex flex-col gap-4">
          <div className="flex flex-1 flex-col justify-center rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-5 py-4">
            <p className="text-xs text-[--color-muted]">Total Salary &amp; Bonus</p>
            <p className="mt-1.5 text-lg font-semibold tabular-nums">{fmtTotal(totalSalaryBonus)}</p>
          </div>
          <div className="flex flex-1 flex-col justify-center rounded-xl border border-[--color-border] bg-[--color-surface-raised] px-5 py-4">
            <p className="text-xs text-[--color-muted]">Total Expense</p>
            <p className="mt-1.5 text-lg font-semibold tabular-nums">{fmtTotal(totalExpense)}</p>
          </div>
        </div>
        <div className="col-span-2 rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-4">
          {chartInner}
        </div>
      </div>
    </>
  );
}
