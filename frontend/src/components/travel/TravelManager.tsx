"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import dynamic from "next/dynamic";
import { Plus, Pencil, Trash2, MapPin, X, BarChart2, Maximize2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  type TravelRecord, type TransportType, type RichExpenseEntry, type TripExpenseData,
  TRANSPORT_TYPES, TRANSPORT_LABELS, TRANSPORT_EMOJI,
  CITY_LOOKUP, TRIP_COLORS, tripDays,
  parseTripExpenseData, buildDateGroups, newExpenseEntry,
} from "@/types/travel";

const TravelMap = dynamic(() => import("./TravelMap"), { ssr: false });

// ── API helpers ────────────────────────────────────────────────────────────────

async function apiList(): Promise<TravelRecord[]> {
  const res = await fetch("/api/travel");
  if (!res.ok) return [];
  return res.json() as Promise<TravelRecord[]>;
}

async function apiCreate(body: object): Promise<TravelRecord> {
  const res = await fetch("/api/travel", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<TravelRecord>;
}

async function apiUpdate(id: string, body: object): Promise<TravelRecord> {
  const res = await fetch(`/api/travel/${id}`, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json() as Promise<TravelRecord>;
}

async function apiDelete(id: string): Promise<void> {
  await fetch(`/api/travel/${id}`, { method: "DELETE" });
}

// ── Shared styles ──────────────────────────────────────────────────────────────

const inputCls =
  "w-full rounded-md border border-[--color-border] bg-[--color-surface] px-3 py-1.5 text-sm " +
  "outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30";

const selectCls =
  "w-full rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 px-3 py-1.5 text-sm " +
  "outline-none focus:border-[--color-primary] focus:ring-1 focus:ring-[--color-primary]/30 " +
  "cursor-pointer appearance-none";

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-[--color-muted]">{label}</label>
      {children}
    </div>
  );
}

// ── City autocomplete ──────────────────────────────────────────────────────────

const CITY_NAMES = Object.keys(CITY_LOOKUP).sort();

function CityInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const filtered = value.length >= 1
    ? CITY_NAMES.filter((c) => c.toLowerCase().includes(value.toLowerCase()) && c !== value).slice(0, 8)
    : [];

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  return (
    <div ref={ref} className="relative">
      <input
        className={inputCls}
        value={value}
        placeholder="e.g. Tokyo"
        autoComplete="off"
        onChange={(e) => { onChange(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
      />
      {open && filtered.length > 0 && (
        <ul className="absolute z-50 mt-1 w-full rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 py-1 shadow-lg max-h-48 overflow-y-auto">
          {filtered.map((c) => (
            <li
              key={c}
              className="cursor-pointer px-3 py-1.5 text-sm hover:bg-[--color-border]/50"
              onMouseDown={(e) => { e.preventDefault(); onChange(c); setOpen(false); }}
            >
              {c} <span className="text-[--color-muted] text-xs">— {CITY_LOOKUP[c]?.country}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── Stop row in form ──────────────────────────────────────────────────────────

interface StopDraft {
  city:         string;
  country:      string;
  lat:          string;
  lon:          string;
  transport:    TransportType | "";
  flightNumber: string;
  seatNumber:   string;
  notes:        string;
}

function emptyStop(): StopDraft {
  return { city: "", country: "", lat: "", lon: "", transport: "", flightNumber: "", seatNumber: "", notes: "" };
}

function StopRow({
  stop, index, isFirst, onChange, onRemove,
}: {
  stop: StopDraft; index: number; isFirst: boolean;
  onChange: (s: StopDraft) => void; onRemove: () => void;
}) {
  const handleCityChange = (city: string) => {
    const lookup = CITY_LOOKUP[city];
    if (lookup) {
      onChange({ ...stop, city, country: lookup.country, lat: String(lookup.lat), lon: String(lookup.lon) });
    } else {
      onChange({ ...stop, city });
    }
  };

  return (
    <div className="rounded-lg border border-[--color-border] bg-[--color-surface] p-3 space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-[--color-muted]">
          Stop {index + 1}{isFirst ? " (origin)" : ""}
        </span>
        {!isFirst && (
          <button onClick={onRemove} className="text-[--color-muted] hover:text-red-400 p-0.5 rounded">
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {!isFirst && (
        <Field label="Transport to here">
          <select
            className={selectCls}
            value={stop.transport}
            onChange={(e) => onChange({ ...stop, transport: e.target.value as TransportType | "" })}
          >
            <option value="">— select —</option>
            {TRANSPORT_TYPES.map((t) => (
              <option key={t} value={t}>{TRANSPORT_EMOJI[t]} {TRANSPORT_LABELS[t]}</option>
            ))}
          </select>
        </Field>
      )}

      {!isFirst && stop.transport && (
        <div className="grid grid-cols-2 gap-2">
          {stop.transport === "PLANE" && (
            <Field label="Flight Number">
              <input
                className={inputCls}
                placeholder="e.g. CX725"
                value={stop.flightNumber}
                onChange={(e) => onChange({ ...stop, flightNumber: e.target.value })}
              />
            </Field>
          )}
          <Field label="Seat Number">
            <input
              className={inputCls}
              placeholder="e.g. 12A"
              value={stop.seatNumber}
              onChange={(e) => onChange({ ...stop, seatNumber: e.target.value })}
            />
          </Field>
        </div>
      )}

      <div className="grid grid-cols-2 gap-2">
        <Field label="City *">
          <CityInput value={stop.city} onChange={handleCityChange} />
        </Field>
        <Field label="Country *">
          <input
            className={inputCls}
            value={stop.country}
            placeholder="e.g. Japan"
            onChange={(e) => onChange({ ...stop, country: e.target.value })}
          />
        </Field>
      </div>
    </div>
  );
}

// ── Modal ──────────────────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: {
  title: string; onClose: () => void; children: React.ReactNode;
}) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => { setMounted(true); }, []);
  if (!mounted) return null;
  return createPortal(
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/20 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg rounded-xl border border-[--color-border] bg-[--color-surface-raised] p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold">{title}</h2>
          <button onClick={onClose} className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50">
            <X className="h-4 w-4" />
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

// ── Trip form ──────────────────────────────────────────────────────────────────

interface TripDraft {
  title:     string;
  startDate: string;
  endDate:   string;
  notes:     string;
  stops:     StopDraft[];
}

function emptyTrip(): TripDraft {
  return { title: "", startDate: "", endDate: "", notes: "", stops: [emptyStop(), emptyStop()] };
}

function draftFromRecord(r: TravelRecord): TripDraft {
  return {
    title:     r.title,
    startDate: r.startDate,
    endDate:   r.endDate,
    notes:     r.notes ?? "",
    stops: r.stops.map((s) => ({
      city:         s.city,
      country:      s.country,
      lat:          String(s.lat),
      lon:          String(s.lon),
      transport:    (s.transport ?? "") as TransportType | "",
      flightNumber: s.flightNumber ?? "",
      seatNumber:   s.seatNumber ?? "",
      notes:        s.notes ?? "",
    })),
  };
}

function draftToPayload(d: TripDraft) {
  return {
    title:     d.title,
    startDate: d.startDate,
    endDate:   d.endDate,
    notes:     d.notes || null,
    stops: d.stops.map((s) => ({
      city:         s.city,
      country:      s.country,
      lat:          parseFloat(s.lat) || 0,
      lon:          parseFloat(s.lon) || 0,
      transport:    s.transport || null,
      flightNumber: s.flightNumber || null,
      seatNumber:   s.seatNumber || null,
      notes:        s.notes || null,
    })),
  };
}

function TripForm({
  initial, onSave, onCancel, saving,
}: {
  initial: TripDraft; onSave: (d: TripDraft) => void; onCancel: () => void; saving: boolean;
}) {
  const [f, setF] = useState(initial);

  const updateStop = (i: number, s: StopDraft) => {
    setF((p) => { const stops = [...p.stops]; stops[i] = s; return { ...p, stops }; });
  };

  const addStop = () => setF((p) => ({ ...p, stops: [...p.stops, emptyStop()] }));

  const removeStop = (i: number) =>
    setF((p) => ({ ...p, stops: p.stops.filter((_, idx) => idx !== i) }));

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => { e.preventDefault(); onSave(f); }}
    >
      <Field label="Trip Title *">
        <input
          className={inputCls}
          required
          placeholder="e.g. Europe 2026"
          value={f.title}
          onChange={(e) => setF((p) => ({ ...p, title: e.target.value }))}
        />
      </Field>

      <div className="grid grid-cols-2 gap-3">
        <Field label="Start Date *">
          <input
            className={inputCls}
            type="date"
            required
            value={f.startDate}
            onChange={(e) => setF((p) => ({ ...p, startDate: e.target.value }))}
          />
        </Field>
        <Field label="End Date *">
          <input
            className={inputCls}
            type="date"
            required
            value={f.endDate}
            onChange={(e) => setF((p) => ({ ...p, endDate: e.target.value }))}
          />
        </Field>
      </div>

      <div>
        <p className="text-xs text-[--color-muted] mb-2">
          Stops — add each city in order, with how you got there
        </p>
        <div className="flex flex-col gap-2">
          {f.stops.map((s, i) => (
            <StopRow
              key={i}
              index={i}
              stop={s}
              isFirst={i === 0}
              onChange={(ns) => updateStop(i, ns)}
              onRemove={() => removeStop(i)}
            />
          ))}
        </div>
        <button
          type="button"
          onClick={addStop}
          className="mt-2 flex items-center gap-1.5 text-xs text-[--color-primary] hover:underline"
        >
          <Plus className="h-3 w-3" /> Add stop
        </button>
      </div>

      <Field label="Notes">
        <textarea
          className={`${inputCls} resize-none`}
          rows={2}
          placeholder="Optional notes about this trip"
          value={f.notes}
          onChange={(e) => setF((p) => ({ ...p, notes: e.target.value }))}
        />
      </Field>

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>
        <Button type="submit" size="sm" disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
      </div>
    </form>
  );
}

// ── Trip card ──────────────────────────────────────────────────────────────────

function TripCard({
  record, color, selected, onSelect, onEdit, onDelete,
}: {
  record: TravelRecord; color: string; selected: boolean;
  onSelect: () => void; onEdit: () => void; onDelete: () => void;
}) {
  const days  = tripDays(record);
  const year  = record.startDate.slice(0, 4);
  const stops = record.stops;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => { if (e.key === "Enter") onSelect(); }}
      className={`group relative rounded-xl border cursor-pointer transition-all outline-none ${
        selected
          ? "border-[--color-primary] ring-1 ring-[--color-primary]/30"
          : "border-[--color-border] hover:border-[--color-border]"
      } bg-[--color-surface-raised] p-4`}
    >
      {/* Color accent */}
      <div className="absolute left-0 top-4 w-1 rounded-r-full h-8" style={{ backgroundColor: color }} />

      <div className="flex items-start justify-between gap-2 pl-3">
        <div className="min-w-0 flex-1">
          <p className="font-medium text-sm truncate">{record.title}</p>
          <p className="text-xs text-[--color-muted] mt-0.5">
            {year} · {days} day{days !== 1 ? "s" : ""}
          </p>
        </div>
        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
          <Button
            size="icon" variant="ghost" className="h-6 w-6"
            onClick={(e) => { e.stopPropagation(); onEdit(); }}
            title="Edit"
          >
            <Pencil className="h-3 w-3" />
          </Button>
          <Button
            size="icon" variant="ghost" className="h-6 w-6"
            onClick={(e) => { e.stopPropagation(); onDelete(); }}
            title="Delete"
          >
            <Trash2 className="h-3 w-3 text-red-400" />
          </Button>
        </div>
      </div>

      {/* Route pills */}
      {stops.length > 0 && (
        <div className="mt-3 pl-3 flex flex-wrap items-center gap-1 text-xs">
          {stops.map((s, i) => (
            <span key={i} className="flex items-center gap-1">
              {i > 0 && s.transport && (
                <span className="text-[--color-muted] text-[11px]" title={TRANSPORT_LABELS[s.transport]}>
                  {TRANSPORT_EMOJI[s.transport]}
                </span>
              )}
              <span
                className="inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 font-medium"
                style={{ backgroundColor: `${color}20`, color }}
              >
                <MapPin className="h-2.5 w-2.5" />
                {s.city}
              </span>
            </span>
          ))}
        </div>
      )}

      {record.notes && (
        <p className="mt-2 pl-3 text-xs text-[--color-muted] line-clamp-2">{record.notes}</p>
      )}
    </div>
  );
}

// ── Legend ─────────────────────────────────────────────────────────────────────

function Legend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-[--color-muted] px-4 py-2 border-b border-[--color-border]">
      <span className="font-medium text-xs">Transport:</span>
      <span className="flex items-center gap-1">
        <span style={{ display: "inline-block", width: 20, borderBottom: "2px dashed #6b7280" }} /> ✈ Plane
      </span>
      <span className="flex items-center gap-1">
        <span style={{ display: "inline-block", width: 20, borderBottom: "2px solid #6b7280" }} /> 🚆 Train
      </span>
      <span className="flex items-center gap-1">
        <span style={{ display: "inline-block", width: 20, borderBottom: "2px dotted #6b7280" }} /> ⛴ Ferry
      </span>
      <span className="flex items-center gap-1">
        <span style={{ display: "inline-block", width: 20, borderBottom: "2px solid #6b7280" }} /> 🚌/🚗/🚶 Land
      </span>
    </div>
  );
}

// ── Expense components ─────────────────────────────────────────────────────────

const tinyInput =
  "w-full rounded border border-[--color-border] bg-[--color-surface] px-1.5 py-1 text-xs " +
  "outline-none focus:border-[--color-primary]";

const COMMON_CURRENCIES = [
  "HKD", "USD", "EUR", "GBP", "JPY", "CNY", "TWD",
  "SGD", "KRW", "THB", "MYR", "AUD", "CAD", "CHF",
];

interface FinancialCard { id: string; name: string }

function CardMultiSelect({
  selected, options, onChange,
}: {
  selected: string[]; options: FinancialCard[]; onChange: (v: string[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const [dropPos, setDropPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const btnRef  = useRef<HTMLButtonElement>(null);
  const dropRef = useRef<HTMLDivElement>(null);
  const [mounted, setMounted] = useState(false);
  useEffect(() => { setMounted(true); }, []);

  useEffect(() => {
    if (!open) return;
    const h = (e: MouseEvent) => {
      const t = e.target as Node;
      if (!btnRef.current?.contains(t) && !dropRef.current?.contains(t)) {
        setOpen(false);
      }
    };
    // use "click" (not "mousedown") so checkbox onChange fires before the close handler
    document.addEventListener("click", h);
    return () => document.removeEventListener("click", h);
  }, [open]);

  const handleToggle = () => {
    if (!open && btnRef.current) {
      const rect = btnRef.current.getBoundingClientRect();
      setDropPos({ top: rect.bottom + 4, left: rect.left, width: Math.max(rect.width, 180) });
    }
    setOpen((o) => !o);
  };

  const toggle = (id: string) =>
    onChange(selected.includes(id) ? selected.filter((c) => c !== id) : [...selected, id]);

  const selectedNames = selected
    .map((id) => options.find((o) => o.id === id)?.name ?? id)
    .join(", ");

  const label = selected.length === 0 ? "Select cards" : selectedNames;

  const dropdown = open && mounted && dropPos ? createPortal(
    <div
      ref={dropRef}
      style={{ position: "fixed", top: dropPos.top, left: dropPos.left, minWidth: dropPos.width, zIndex: 99999 }}
      className="rounded-md border border-[--color-border] bg-white dark:bg-neutral-900 shadow-xl py-1 max-h-48 overflow-y-auto"
    >
      {options.length === 0 ? (
        <p className="px-3 py-2 text-xs text-[--color-muted]">No cards in financial section</p>
      ) : (
        options.map((card) => (
          <label
            key={card.id}
            className="flex items-center gap-2 px-3 py-1.5 text-xs cursor-pointer hover:bg-[--color-border]/30 whitespace-nowrap"
          >
            <input
              type="checkbox"
              checked={selected.includes(card.id)}
              onChange={() => toggle(card.id)}
              className="cursor-pointer"
            />
            {card.name}
          </label>
        ))
      )}
    </div>,
    document.body,
  ) : null;

  return (
    <div className="w-full">
      <button
        ref={btnRef}
        type="button"
        onClick={handleToggle}
        className={`${tinyInput} text-left flex items-center justify-between gap-1 cursor-pointer`}
      >
        <span className={`truncate ${selected.length === 0 ? "text-[--color-muted]" : ""}`}>{label}</span>
        <span className="text-[--color-muted] text-[10px] shrink-0">▾</span>
      </button>
      {dropdown}
    </div>
  );
}

function ExpenseEntryRow({
  entry, currencies, cards, primaryCur, onChange, onRemove,
}: {
  entry: RichExpenseEntry; currencies: string[]; cards: FinancialCard[]; primaryCur: string | undefined;
  onChange: (e: RichExpenseEntry) => void; onRemove: () => void;
}) {
  return (
    <tr className="border-t border-[--color-border]/50">
      <td className="px-2 py-1">
        <input
          className={tinyInput}
          placeholder="Item name"
          value={entry.name}
          onChange={(e) => onChange({ ...entry, name: e.target.value })}
        />
      </td>
      {currencies.map((c) => (
        <td key={c} className="px-2 py-1">
          <input
            className={`${tinyInput} text-right`}
            type="number"
            min="0"
            step="0.01"
            placeholder="0"
            value={entry.amounts[c] ?? ""}
            onChange={(ev) => {
              const v = parseFloat(ev.target.value);
              const amounts = { ...entry.amounts };
              if (isNaN(v) || ev.target.value === "") { delete amounts[c]; } else { amounts[c] = v; }
              onChange({ ...entry, amounts });
            }}
          />
        </td>
      ))}
      <td className="px-2 py-1">
        <input
          className={`${tinyInput} text-right`}
          type="number"
          min="0"
          step="0.01"
          placeholder="0"
          value={entry.cashback || ""}
          onChange={(e) => onChange({ ...entry, cashback: parseFloat(e.target.value) || 0 })}
          title={primaryCur ? `Cashback in ${primaryCur}` : "Cashback"}
        />
      </td>
      <td className="px-2 py-1 min-w-[120px]">
        <CardMultiSelect
          selected={entry.creditCards}
          options={cards}
          onChange={(v) => onChange({ ...entry, creditCards: v })}
        />
      </td>
      <td className="px-2 py-1">
        <button onClick={onRemove} className="text-[--color-muted] hover:text-red-400 p-0.5">
          <X className="h-3 w-3" />
        </button>
      </td>
    </tr>
  );
}

function ExpenseTable({
  currencies, cards, entries, primaryCur, onUpdate, onRemove,
}: {
  currencies: string[]; cards: FinancialCard[]; entries: RichExpenseEntry[]; primaryCur: string | undefined;
  onUpdate: (id: string, e: RichExpenseEntry) => void; onRemove: (id: string) => void;
}) {
  if (entries.length === 0) return null;
  return (
    <div className="overflow-x-auto rounded-lg border border-[--color-border]">
      <table className="min-w-full text-xs">
        <thead className="bg-[--color-surface]/80">
          <tr>
            <th className="text-left px-2 py-1.5 text-[--color-muted] font-medium min-w-[110px]">Name</th>
            {currencies.map((c) => (
              <th key={c} className="text-right px-2 py-1.5 text-[--color-muted] font-medium min-w-[70px]">{c}</th>
            ))}
            <th className="text-right px-2 py-1.5 text-[--color-muted] font-medium min-w-[80px]">
              Cashback{primaryCur ? ` (${primaryCur})` : ""}
            </th>
            <th className="text-left px-2 py-1.5 text-[--color-muted] font-medium min-w-[120px]">Card</th>
            <th className="w-6" />
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <ExpenseEntryRow
              key={entry.id}
              entry={entry}
              currencies={currencies}
              cards={cards}
              primaryCur={primaryCur}
              onChange={(e) => onUpdate(entry.id, e)}
              onRemove={() => onRemove(entry.id)}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ExpenseTab({
  record, onSaved,
}: {
  record: TravelRecord; onSaved: () => void;
}) {
  const init = (): TripExpenseData => {
    const existing = parseTripExpenseData(record.expenses);
    const currencies = existing?.currencies ?? [];
    return {
      __v: 2,
      currencies,
      defaultCurrency: existing?.defaultCurrency ?? currencies[0] ?? "",
      itemExpenses:    existing?.itemExpenses ?? [],
      dateExpenses:    buildDateGroups(record.startDate, record.endDate, existing?.dateExpenses ?? []),
    };
  };

  const [data, setData]               = useState<TripExpenseData>(init);
  const [saving, setSaving]           = useState(false);
  const [availableCards, setAvailableCards] = useState<FinancialCard[]>([]);

  useEffect(() => {
    fetch("/api/financial?type=cards")
      .then((r) => r.ok ? r.json() as Promise<{ id: string; cardName: string }[]> : [])
      .then((list) => setAvailableCards(list.map((c) => ({ id: c.id, name: c.cardName }))))
      .catch(() => {});
  }, []);

  useEffect(() => { setData(init()); }, [record.id]); // eslint-disable-line react-hooks/exhaustive-deps

  async function save() {
    setSaving(true);
    try {
      await apiUpdate(record.id, { expenses: [data] });
      onSaved();
    } finally {
      setSaving(false);
    }
  }

  function addCurrency(c: string) {
    if (!c || data.currencies.includes(c)) return;
    setData((d) => ({
      ...d,
      currencies: [...d.currencies, c],
      defaultCurrency: d.defaultCurrency || c,
    }));
  }

  function removeCurrency(c: string) {
    setData((d) => {
      const next = d.currencies.filter((x) => x !== c);
      return {
        ...d,
        currencies: next,
        defaultCurrency: d.defaultCurrency === c ? (next[0] ?? "") : d.defaultCurrency,
      };
    });
  }

  function setDefaultCurrency(c: string) {
    setData((d) => ({ ...d, defaultCurrency: c }));
  }

  function addItem() {
    setData((d) => ({ ...d, itemExpenses: [...d.itemExpenses, newExpenseEntry()] }));
  }

  function updateItem(id: string, e: RichExpenseEntry) {
    setData((d) => ({ ...d, itemExpenses: d.itemExpenses.map((x) => x.id === id ? e : x) }));
  }

  function removeItem(id: string) {
    setData((d) => ({ ...d, itemExpenses: d.itemExpenses.filter((x) => x.id !== id) }));
  }

  function addDateEntry(date: string) {
    setData((d) => ({
      ...d,
      dateExpenses: d.dateExpenses.map((g) =>
        g.date === date ? { ...g, entries: [...g.entries, newExpenseEntry()] } : g,
      ),
    }));
  }

  function updateDateEntry(date: string, id: string, e: RichExpenseEntry) {
    setData((d) => ({
      ...d,
      dateExpenses: d.dateExpenses.map((g) =>
        g.date === date ? { ...g, entries: g.entries.map((x) => x.id === id ? e : x) } : g,
      ),
    }));
  }

  function removeDateEntry(date: string, id: string) {
    setData((d) => ({
      ...d,
      dateExpenses: d.dateExpenses.map((g) =>
        g.date === date ? { ...g, entries: g.entries.filter((x) => x.id !== id) } : g,
      ),
    }));
  }

  // Compute totals
  const allEntries = [...data.itemExpenses, ...data.dateExpenses.flatMap((g) => g.entries)];
  const totalsByCur: Record<string, number> = {};
  let totalCashback = 0;
  for (const entry of allEntries) {
    for (const [c, amt] of Object.entries(entry.amounts)) {
      totalsByCur[c] = (totalsByCur[c] ?? 0) + amt;
    }
    totalCashback += entry.cashback;
  }
  const primaryCur = data.defaultCurrency || data.currencies[0];
  const netTotal   = primaryCur ? (totalsByCur[primaryCur] ?? 0) - totalCashback : null;

  const availableCurrencies = COMMON_CURRENCIES.filter((c) => !data.currencies.includes(c));

  return (
    <div className="flex flex-col gap-4">
      {/* Currencies — inline with click-to-set-default */}
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-xs text-[--color-muted] shrink-0">Currencies tracked</span>
        {data.currencies.length === 0 && (
          <span className="text-xs text-[--color-muted]">none</span>
        )}
        {data.currencies.map((c) => {
          const isDefault = c === primaryCur;
          return (
            <span
              key={c}
              className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium border ${
                isDefault
                  ? "bg-[--color-primary]/10 border-[--color-primary]/40 text-[--color-primary]"
                  : "bg-[--color-border]/60 border-transparent text-[--color-fg]"
              }`}
            >
              <button
                onClick={() => setDefaultCurrency(c)}
                title={isDefault ? "Default currency" : "Set as default"}
                className={`leading-none ${isDefault ? "opacity-80" : "opacity-30 hover:opacity-70"}`}
              >
                {isDefault ? "●" : "○"}
              </button>
              {c}
              <button onClick={() => removeCurrency(c)} className="text-[--color-muted] hover:text-red-400">
                <X className="h-2.5 w-2.5" />
              </button>
            </span>
          );
        })}
        <select
          className="ml-auto text-xs border border-[--color-border] rounded px-1.5 py-0.5 bg-[--color-surface] cursor-pointer outline-none focus:border-[--color-primary]"
          value=""
          onChange={(e) => { if (e.target.value) addCurrency(e.target.value); }}
        >
          <option value="">+ Currency</option>
          {availableCurrencies.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>

      {data.currencies.length > 0 && (
        <>
          {/* Item expenses */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-[--color-muted] uppercase tracking-wide">Pre-trip / Fixed</p>
              <button onClick={addItem} className="flex items-center gap-1 text-xs text-[--color-primary] hover:underline">
                <Plus className="h-3 w-3" /> Add item
              </button>
            </div>
            {data.itemExpenses.length === 0 ? (
              <p className="text-xs text-[--color-muted] pl-1">No items yet</p>
            ) : (
              <ExpenseTable
                currencies={data.currencies}
                cards={availableCards}
                primaryCur={primaryCur}
                entries={data.itemExpenses}
                onUpdate={updateItem}
                onRemove={removeItem}
              />
            )}
          </div>

          {/* Daily expenses */}
          <div>
            <p className="text-xs font-semibold text-[--color-muted] uppercase tracking-wide mb-2">Daily</p>
            <div className="space-y-3">
              {data.dateExpenses.map((group) => (
                <div key={group.date}>
                  <div className="flex items-center justify-between mb-1">
                    <p className="text-xs font-medium">{group.date.replace(/-/g, "/")}</p>
                    <button
                      onClick={() => addDateEntry(group.date)}
                      className="flex items-center gap-1 text-xs text-[--color-primary] hover:underline"
                    >
                      <Plus className="h-3 w-3" /> Add
                    </button>
                  </div>
                  {group.entries.length > 0 ? (
                    <ExpenseTable
                      currencies={data.currencies}
                      cards={availableCards}
                      primaryCur={primaryCur}
                      entries={group.entries}
                      onUpdate={(id, e) => updateDateEntry(group.date, id, e)}
                      onRemove={(id) => removeDateEntry(group.date, id)}
                    />
                  ) : (
                    <p className="text-xs text-[--color-muted] pl-1">—</p>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Totals */}
          {allEntries.length > 0 && (
            <div className="rounded-lg border border-[--color-border] bg-[--color-surface] px-3.5 py-2.5 space-y-1">
              <p className="text-[10px] font-semibold text-[--color-muted] uppercase tracking-wide mb-1">Summary</p>
              {data.currencies.map((c) => (
                <div key={c} className="flex justify-between text-sm">
                  <span className="text-[--color-muted]">Total {c}</span>
                  <span className="font-medium">{(totalsByCur[c] ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                </div>
              ))}
              {totalCashback > 0 && (
                <div className="flex justify-between text-sm">
                  <span className="text-[--color-muted]">Cashback</span>
                  <span className="font-medium text-emerald-500">−{totalCashback.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                </div>
              )}
              {netTotal !== null && primaryCur && (
                <div className="flex justify-between text-sm border-t border-[--color-border] pt-1 mt-1">
                  <span className="font-semibold">Net ({primaryCur})</span>
                  <span className="font-bold">{netTotal.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* Save */}
      <div className="flex justify-end pt-1">
        <Button size="sm" onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save expenses"}
        </Button>
      </div>
    </div>
  );
}

// ── Travel analysis ────────────────────────────────────────────────────────────

function computeAnalysis(records: TravelRecord[]) {
  if (records.length === 0) return null;

  const totalDays = records.reduce((sum, r) => sum + tripDays(r), 0);

  const countryCounts: Record<string, number> = {};
  const cityCounts:    Record<string, number> = {};
  const transportCounts: Partial<Record<TransportType, number>> = {};

  for (const r of records) {
    for (const s of r.stops) {
      countryCounts[s.country] = (countryCounts[s.country] ?? 0) + 1;
      cityCounts[s.city]       = (cityCounts[s.city]       ?? 0) + 1;
      if (s.transport) transportCounts[s.transport] = (transportCounts[s.transport] ?? 0) + 1;
    }
  }

  const countries = Object.keys(countryCounts).sort();
  const topCountry = Object.entries(countryCounts).sort((a, b) => b[1] - a[1])[0];
  const topCity    = Object.entries(cityCounts).sort((a, b) => b[1] - a[1])[0];

  const sorted = [...records].sort((a, b) => tripDays(b) - tripDays(a));
  const longestTrip  = sorted[0]!;
  const shortestTrip = sorted[sorted.length - 1]!;

  const years = [...new Set(records.map((r) => r.startDate.slice(0, 4)))].sort().reverse();

  return {
    totalTrips:    records.length,
    totalDays,
    totalCountries: countries.length,
    totalCities:    Object.keys(cityCounts).length,
    countries,
    topCountry,
    topCity,
    transportCounts,
    longestTrip,
    shortestTrip,
    years,
  };
}

function AnalysisModal({ records, onClose }: { records: TravelRecord[]; onClose: () => void }) {
  const stats = computeAnalysis(records);

  return (
    <Modal title="Travel Analysis" onClose={onClose}>
      {!stats ? (
        <p className="text-sm text-[--color-muted] text-center py-8">No trips to analyse yet.</p>
      ) : (
        <div className="flex flex-col gap-5">

          {/* Summary numbers */}
          <div className="grid grid-cols-2 gap-3">
            {[
              { label: "Trips",     value: stats.totalTrips },
              { label: "Days",      value: stats.totalDays },
              { label: "Countries", value: stats.totalCountries },
              { label: "Cities",    value: stats.totalCities },
            ].map(({ label, value }) => (
              <div
                key={label}
                className="rounded-lg border border-[--color-border] bg-[--color-surface] p-4 text-center"
              >
                <p className="text-2xl font-bold">{value}</p>
                <p className="text-xs text-[--color-muted] mt-0.5">{label}</p>
              </div>
            ))}
          </div>

          {/* Highlights */}
          <div className="rounded-lg border border-[--color-border] bg-[--color-surface] p-4 space-y-2.5">
            <p className="text-xs font-semibold text-[--color-muted] uppercase tracking-wide">Highlights</p>
            {stats.topCountry && (
              <div className="flex justify-between text-sm">
                <span className="text-[--color-muted]">Most visited country</span>
                <span className="font-medium">{stats.topCountry[0]} <span className="text-[--color-muted] text-xs">({stats.topCountry[1]}x)</span></span>
              </div>
            )}
            {stats.topCity && (
              <div className="flex justify-between text-sm">
                <span className="text-[--color-muted]">Most visited city</span>
                <span className="font-medium">{stats.topCity[0]} <span className="text-[--color-muted] text-xs">({stats.topCity[1]}x)</span></span>
              </div>
            )}
            <div className="flex justify-between text-sm">
              <span className="text-[--color-muted]">Longest trip</span>
              <span className="font-medium">{stats.longestTrip.title} <span className="text-[--color-muted] text-xs">({tripDays(stats.longestTrip)}d)</span></span>
            </div>
            {stats.longestTrip.id !== stats.shortestTrip.id && (
              <div className="flex justify-between text-sm">
                <span className="text-[--color-muted]">Shortest trip</span>
                <span className="font-medium">{stats.shortestTrip.title} <span className="text-[--color-muted] text-xs">({tripDays(stats.shortestTrip)}d)</span></span>
              </div>
            )}
            <div className="flex justify-between text-sm">
              <span className="text-[--color-muted]">Active years</span>
              <span className="font-medium">{stats.years.join(", ")}</span>
            </div>
          </div>

          {/* Transport breakdown */}
          {Object.keys(stats.transportCounts).length > 0 && (
            <div className="rounded-lg border border-[--color-border] bg-[--color-surface] p-4 space-y-2.5">
              <p className="text-xs font-semibold text-[--color-muted] uppercase tracking-wide">Transport Breakdown</p>
              {(Object.entries(stats.transportCounts) as [TransportType, number][])
                .sort((a, b) => b[1] - a[1])
                .map(([type, count]) => {
                  const total = Object.values(stats.transportCounts).reduce((s, n) => s + (n ?? 0), 0);
                  const pct   = Math.round((count / total) * 100);
                  return (
                    <div key={type}>
                      <div className="flex justify-between text-sm mb-1">
                        <span>{TRANSPORT_EMOJI[type]} {TRANSPORT_LABELS[type]}</span>
                        <span className="text-[--color-muted] text-xs">{count} leg{count !== 1 ? "s" : ""} · {pct}%</span>
                      </div>
                      <div className="h-1.5 w-full rounded-full bg-[--color-border]">
                        <div
                          className="h-1.5 rounded-full bg-[--color-primary]"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
            </div>
          )}

          {/* Countries list */}
          <div className="rounded-lg border border-[--color-border] bg-[--color-surface] p-4">
            <p className="text-xs font-semibold text-[--color-muted] uppercase tracking-wide mb-2">Countries Visited</p>
            <div className="flex flex-wrap gap-1.5">
              {stats.countries.map((c) => (
                <span
                  key={c}
                  className="inline-flex items-center rounded-full border border-[--color-border] px-2.5 py-0.5 text-xs"
                >
                  {c}
                </span>
              ))}
            </div>
          </div>

        </div>
      )}
    </Modal>
  );
}

// ── Detail panel ───────────────────────────────────────────────────────────────

type DetailTab = "detail" | "expense" | "transport" | "memory";

const PANEL_MIN = 360;
const PANEL_MAX = 900;
const PANEL_DEFAULT = PANEL_MAX;

const DETAIL_TABS: { id: DetailTab; icon: string; label: string }[] = [
  { id: "detail",    icon: "📋", label: "Detail" },
  { id: "expense",   icon: "💰", label: "Expense" },
  { id: "transport", icon: "🚆", label: "Transport" },
  { id: "memory",    icon: "📸", label: "Memory" },
];

function EmptyTab({ icon, text }: { icon: string; text: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center">
      <span className="text-3xl opacity-40">{icon}</span>
      <p className="text-sm text-[--color-muted]">{text}</p>
    </div>
  );
}

function TravelDetailPanel({
  record, color, onClose, onUpdate,
}: {
  record: TravelRecord; color: string; onClose: () => void; onUpdate: () => void;
}) {
  const [mounted, setMounted] = useState(false);
  const [tab, setTab] = useState<DetailTab>("detail");
  const [width, setWidth] = useState(PANEL_DEFAULT);
  const dragRef = useRef<{ startX: number; startW: number } | null>(null);

  useEffect(() => { setMounted(true); }, []);
  useEffect(() => { setTab("detail"); }, [record.id]);

  const onResizeStart = useCallback((e: React.MouseEvent | React.TouchEvent) => {
    const clientX = "touches" in e ? e.touches[0]!.clientX : e.clientX;
    dragRef.current = { startX: clientX, startW: width };

    const onMove = (ev: MouseEvent | TouchEvent) => {
      if (!dragRef.current) return;
      const x = "touches" in ev ? (ev as TouchEvent).touches[0]!.clientX : (ev as MouseEvent).clientX;
      const next = Math.min(PANEL_MAX, Math.max(PANEL_MIN, dragRef.current.startW - (x - dragRef.current.startX)));
      setWidth(next);
    };
    const onUp = () => {
      dragRef.current = null;
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("touchmove", onMove);
      window.removeEventListener("mouseup",   onUp);
      window.removeEventListener("touchend",  onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("touchmove", onMove);
    window.addEventListener("mouseup",   onUp);
    window.addEventListener("touchend",  onUp);
  }, [width]);

  if (!mounted) return null;

  const days       = tripDays(record);
  const year       = record.startDate.slice(0, 4);
  const route      = record.stops.map((s) => s.city).join(" → ");
  const countries  = new Set(record.stops.map((s) => s.country)).size;
  const cities     = new Set(record.stops.map((s) => s.city)).size;

  const legs = record.stops.slice(1).map((s, i) => ({
    from:         record.stops[i]!.city,
    to:           s.city,
    transport:    s.transport,
    flightNumber: s.flightNumber,
    seatNumber:   s.seatNumber,
    notes:        s.notes,
  }));

  return createPortal(
    <div className="fixed inset-0 z-[9999]">
      <div className="absolute inset-0 bg-black/20 backdrop-blur-sm" onClick={onClose} />
      <aside
        style={{ "--panel-w": `${width}px` } as React.CSSProperties}
        className="absolute right-0 top-0 flex h-full w-full flex-col border-l border-[--color-border] bg-[--color-surface-raised] shadow-xl sm:w-[var(--panel-w)]"
      >
        {/* Resize handle (desktop only) */}
        <div
          onMouseDown={onResizeStart}
          onTouchStart={onResizeStart}
          className="absolute left-0 top-0 z-10 hidden h-full w-1.5 -translate-x-1/2 cursor-col-resize select-none sm:block hover:bg-[--color-primary]/30"
          title="Drag to resize"
        />

        {/* Header */}
        <div className="flex items-start justify-between gap-2 border-b border-[--color-border] px-4 py-3">
          <div className="min-w-0">
            <p className="text-sm font-semibold truncate">{record.title}</p>
            <p className="text-xs text-[--color-muted] mt-0.5">
              {year} · {days} day{days !== 1 ? "s" : ""}{route ? ` · ${route}` : ""}
            </p>
          </div>
          <div className="flex items-center gap-0.5 shrink-0">
            <button
              onClick={() => setWidth(width === PANEL_MAX ? PANEL_DEFAULT : PANEL_MAX)}
              className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50"
              title={width === PANEL_MAX ? "Restore size" : "Expand to full"}
            >
              <Maximize2 className="h-3.5 w-3.5" />
            </button>
            <button onClick={onClose} className="rounded-md p-1 text-[--color-muted] hover:bg-[--color-border]/50">
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-[--color-border] px-2 overflow-x-auto">
          {DETAIL_TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex items-center gap-1.5 whitespace-nowrap border-b-2 -mb-px px-3 py-2.5 text-xs font-medium transition-colors ${
                tab === t.id
                  ? "border-[--color-primary] text-[--color-primary]"
                  : "border-transparent text-[--color-muted] hover:text-[--color-fg]"
              }`}
            >
              <span>{t.icon}</span> {t.label}
            </button>
          ))}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-4 py-4">
          {tab === "detail" && (
            <div className="space-y-4">
              <p className="text-sm leading-relaxed">
                {record.notes || "No notes for this trip yet."}
              </p>
              <div className="grid grid-cols-2 gap-2.5">
                {[
                  { label: "Year",      value: year },
                  { label: "Duration",  value: `${days} day${days !== 1 ? "s" : ""}` },
                  { label: "Countries", value: countries },
                  { label: "Cities",    value: cities },
                ].map(({ label, value }) => (
                  <div key={label} className="rounded-lg border border-[--color-border] bg-[--color-surface] p-3">
                    <p className="text-[10px] uppercase tracking-wide text-[--color-muted]">{label}</p>
                    <p className="text-sm font-medium mt-0.5">{value}</p>
                  </div>
                ))}
              </div>
              {record.stops.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {record.stops.map((s, i) => (
                    <span
                      key={i}
                      className="inline-flex items-center gap-0.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
                      style={{ backgroundColor: `${color}20`, color }}
                    >
                      <MapPin className="h-2.5 w-2.5" /> {s.city}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          {tab === "expense" && (
            <ExpenseTab record={record} onSaved={onUpdate} />
          )}

          {tab === "transport" && (
            legs.length === 0 ? (
              <EmptyTab icon="🚆" text="Add stops to see transport legs." />
            ) : (
              <div className="space-y-2.5">
                {legs.map((leg, i) => (
                  <div key={i} className="flex items-center gap-3 rounded-lg border border-[--color-border] bg-[--color-surface] px-3.5 py-2.5">
                    <span
                      className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-base"
                      style={{ backgroundColor: `${color}20` }}
                    >
                      {leg.transport ? TRANSPORT_EMOJI[leg.transport] : "📍"}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium truncate">{leg.from} → {leg.to}</p>
                      <p className="text-xs text-[--color-muted] mt-0.5">
                        {leg.transport ? TRANSPORT_LABELS[leg.transport] : "Mode unknown"}
                        {leg.flightNumber ? ` · ${leg.flightNumber}` : ""}
                        {leg.seatNumber ? ` · Seat ${leg.seatNumber}` : ""}
                        {leg.notes ? ` · ${leg.notes}` : ""}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )
          )}

          {tab === "memory" && (
            <EmptyTab icon="📸" text="Memories coming soon." />
          )}
        </div>
      </aside>
    </div>,
    document.body,
  );
}

// ── Main component ─────────────────────────────────────────────────────────────

const MAP_MIN = 160;
const MAP_MAX = 700;
const MAP_DEFAULT = 380;

export function TravelManager() {
  const [records,    setRecords]    = useState<TravelRecord[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [saving,     setSaving]     = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailId,   setDetailId]   = useState<string | null>(null);
  const [mapHeight,  setMapHeight]  = useState(MAP_DEFAULT);
  const dragRef = useRef<{ startY: number; startH: number } | null>(null);

  const onDragStart = useCallback((e: React.MouseEvent | React.TouchEvent) => {
    const clientY = "touches" in e ? e.touches[0]!.clientY : e.clientY;
    dragRef.current = { startY: clientY, startH: mapHeight };

    const onMove = (ev: MouseEvent | TouchEvent) => {
      if (!dragRef.current) return;
      const y = "touches" in ev ? (ev as TouchEvent).touches[0]!.clientY : (ev as MouseEvent).clientY;
      const next = Math.min(MAP_MAX, Math.max(MAP_MIN, dragRef.current.startH + y - dragRef.current.startY));
      setMapHeight(next);
    };
    const onUp = () => {
      dragRef.current = null;
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("touchmove", onMove);
      window.removeEventListener("mouseup",   onUp);
      window.removeEventListener("touchend",  onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("touchmove", onMove);
    window.addEventListener("mouseup",   onUp);
    window.addEventListener("touchend",  onUp);
  }, [mapHeight]);

  const [modal, setModal] = useState<
    | { mode: "add" }
    | { mode: "edit"; record: TravelRecord }
    | null
  >(null);

  const [analysisOpen, setAnalysisOpen] = useState(false);

  const load = useCallback(async () => {
    const data = await apiList();
    setRecords(data);
  }, []);

  useEffect(() => {
    setLoading(true);
    load().finally(() => setLoading(false));
  }, [load]);

  async function handleSave(draft: TripDraft) {
    setSaving(true);
    try {
      const payload = draftToPayload(draft);
      if (modal?.mode === "edit") {
        await apiUpdate(modal.record.id, payload);
      } else {
        await apiCreate(payload);
      }
      setModal(null);
      await load();
    } finally {
      setSaving(false); }
  }

  async function handleDelete(id: string) {
    if (!confirm("Delete this trip?")) return;
    await apiDelete(id);
    if (selectedId === id) setSelectedId(null);
    if (detailId === id) setDetailId(null);
    await load();
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-[--color-muted]">Loading…</p>
      </div>
    );
  }

  const detailIndex  = records.findIndex((r) => r.id === detailId);
  const detailRecord = detailIndex >= 0 ? records[detailIndex]! : null;

  return (
    <div className="flex h-full flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[--color-border] px-4 py-3">
        <div>
          <h1 className="text-sm font-semibold">Travel</h1>
          {records.length > 0 && (
            <p className="text-xs text-[--color-muted]">
              {records.length} trip{records.length !== 1 ? "s" : ""} · {new Set(records.flatMap((r) => r.stops.map((s) => s.city))).size} cities
            </p>
          )}
        </div>
        <div className="flex items-center gap-2">
          {records.length > 0 && (
            <Button size="sm" variant="ghost" onClick={() => setAnalysisOpen(true)}>
              <BarChart2 className="h-3.5 w-3.5 mr-1" /> Analysis
            </Button>
          )}
          <Button size="sm" onClick={() => setModal({ mode: "add" })}>
            <Plus className="h-3.5 w-3.5 mr-1" /> Add Trip
          </Button>
        </div>
      </div>

      {/* Map — isolate creates a stacking context so Leaflet's z-indexes (200-600) don't bleed over the mobile sidebar */}
      <div className="relative isolate shrink-0 border-b border-[--color-border]" style={{ height: mapHeight }}>
        {records.length === 0 ? (
          <div className="flex h-full items-center justify-center bg-[--color-surface]">
            <div className="text-center">
              <MapPin className="mx-auto h-10 w-10 text-[--color-muted] opacity-30" />
              <p className="mt-2 text-sm text-[--color-muted]">Add your first trip to see it on the map</p>
            </div>
          </div>
        ) : (
          <TravelMap
            records={records}
            selectedId={selectedId}
            onSelectRecord={setSelectedId}
          />
        )}
        {selectedId && (
          <button
            className="absolute top-3 right-3 z-[400] rounded-full bg-white dark:bg-neutral-800 border border-[--color-border] px-2 py-0.5 text-xs shadow hover:bg-[--color-border]/50"
            onClick={() => setSelectedId(null)}
          >
            Clear
          </button>
        )}
      </div>

      {/* Drag handle */}
      <div
        onMouseDown={onDragStart}
        onTouchStart={onDragStart}
        className="group flex h-2 shrink-0 cursor-row-resize items-center justify-center bg-[--color-surface] hover:bg-[--color-border]/50 select-none"
        title="Drag to resize"
      >
        <span className="h-0.5 w-8 rounded-full bg-[--color-border] group-hover:bg-[--color-muted] transition-colors" />
      </div>

      {/* Legend */}
      {records.length > 0 && <Legend />}

      {/* Cards grid */}
      <div className="flex-1 overflow-y-auto px-4 py-4">
        {records.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <MapPin className="h-12 w-12 text-[--color-muted] opacity-20" />
            <p className="text-sm text-[--color-muted]">No trips yet. Add your first trip!</p>
            <Button size="sm" onClick={() => setModal({ mode: "add" })}>
              <Plus className="h-3.5 w-3.5 mr-1" /> Add Trip
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {records.map((r, i) => (
              <TripCard
                key={r.id}
                record={r}
                color={TRIP_COLORS[i % TRIP_COLORS.length] ?? "#6b7280"}
                selected={selectedId === r.id}
                onSelect={() => { setSelectedId(r.id); setDetailId(r.id); }}
                onEdit={() => setModal({ mode: "edit", record: r })}
                onDelete={() => handleDelete(r.id)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Add / Edit modal */}
      {modal && (
        <Modal
          title={modal.mode === "add" ? "Add Trip" : "Edit Trip"}
          onClose={() => setModal(null)}
        >
          <TripForm
            initial={modal.mode === "edit" ? draftFromRecord(modal.record) : emptyTrip()}
            onSave={handleSave}
            onCancel={() => setModal(null)}
            saving={saving}
          />
        </Modal>
      )}

      {/* Analysis modal */}
      {analysisOpen && (
        <AnalysisModal records={records} onClose={() => setAnalysisOpen(false)} />
      )}

      {/* Detail panel */}
      {detailRecord && (
        <TravelDetailPanel
          record={detailRecord}
          color={TRIP_COLORS[detailIndex % TRIP_COLORS.length] ?? "#6b7280"}
          onClose={() => setDetailId(null)}
          onUpdate={load}
        />
      )}
    </div>
  );
}
