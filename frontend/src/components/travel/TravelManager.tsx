"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import dynamic from "next/dynamic";
import { Plus, Pencil, Trash2, MapPin, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  type TravelRecord, type TravelStop, type TransportType,
  TRANSPORT_TYPES, TRANSPORT_LABELS, TRANSPORT_EMOJI,
  CITY_LOOKUP, TRIP_COLORS, tripDays,
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
  city:      string;
  country:   string;
  lat:       string;
  lon:       string;
  transport: TransportType | "";
  notes:     string;
}

function emptyStop(): StopDraft {
  return { city: "", country: "", lat: "", lon: "", transport: "", notes: "" };
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
      city:      s.city,
      country:   s.country,
      lat:       String(s.lat),
      lon:       String(s.lon),
      transport: (s.transport ?? "") as TransportType | "",
      notes:     s.notes ?? "",
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
      city:      s.city,
      country:   s.country,
      lat:       parseFloat(s.lat) || 0,
      lon:       parseFloat(s.lon) || 0,
      transport: s.transport || null,
      notes:     s.notes || null,
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

// ── Main component ─────────────────────────────────────────────────────────────

export function TravelManager() {
  const [records,    setRecords]    = useState<TravelRecord[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [saving,     setSaving]     = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [modal, setModal] = useState<
    | { mode: "add" }
    | { mode: "edit"; record: TravelRecord }
    | null
  >(null);

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
    await load();
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-sm text-[--color-muted]">Loading…</p>
      </div>
    );
  }

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
        <Button size="sm" onClick={() => setModal({ mode: "add" })}>
          <Plus className="h-3.5 w-3.5 mr-1" /> Add Trip
        </Button>
      </div>

      {/* Map */}
      <div className="relative shrink-0 h-[380px] border-b border-[--color-border]">
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
                onSelect={() => setSelectedId((prev) => prev === r.id ? null : r.id)}
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
    </div>
  );
}
