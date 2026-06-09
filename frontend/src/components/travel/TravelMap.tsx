"use client";

import "leaflet/dist/leaflet.css";
import { useEffect } from "react";
import { MapContainer, TileLayer, CircleMarker, Polyline, Tooltip, useMap } from "react-leaflet";
import L from "leaflet";
import type { TravelRecord, TravelStop } from "@/types/travel";
import { TRIP_COLORS, TRANSPORT_EMOJI } from "@/types/travel";

interface Props {
  records: TravelRecord[];
  selectedId: string | null;
  onSelectRecord: (id: string | null) => void;
}

function FitBounds({ records }: { records: TravelRecord[] }) {
  const map = useMap();
  useEffect(() => {
    const allCities = records.flatMap((r) => r.stops).filter((s) => s.lat && s.lon);
    if (allCities.length === 0) return;
    const bounds = L.latLngBounds(allCities.map((s) => [s.lat, s.lon]));
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 8 });
  // run once on mount + when records change significantly
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, records.length]);
  return null;
}

function dashArray(transport: string | null): string | undefined {
  if (transport === "PLANE") return "8 6";
  if (transport === "FERRY") return "3 5";
  return undefined;
}

export default function TravelMap({ records, selectedId, onSelectRecord }: Props) {
  const allCities: Record<string, { lat: number; lon: number; city: string; country: string; trips: string[] }> = {};

  for (const r of records) {
    for (const s of r.stops) {
      if (!s.lat || !s.lon) continue;
      const key = `${s.city}__${s.country}`;
      if (!allCities[key]) {
        allCities[key] = { lat: s.lat, lon: s.lon, city: s.city, country: s.country, trips: [] };
      }
      if (!allCities[key].trips.includes(r.id)) allCities[key].trips.push(r.id);
    }
  }

  return (
    <MapContainer
      center={[20, 10]}
      zoom={2}
      style={{ height: "100%", width: "100%" }}
      scrollWheelZoom
      zoomControl
    >
      <TileLayer
        attribution='&copy; <a href="https://carto.com">CARTO</a>'
        url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
      />
      <FitBounds records={records} />

      {/* Route lines */}
      {records.map((r, ri) => {
        const color = TRIP_COLORS[ri % TRIP_COLORS.length];
        const opacity = selectedId === null || selectedId === r.id ? 1 : 0.2;
        const segments: { from: TravelStop; to: TravelStop; transport: string | null }[] = [];
        for (let i = 1; i < r.stops.length; i++) {
          const from = r.stops[i - 1];
          const to   = r.stops[i];
          if (from && to && from.lat && from.lon && to.lat && to.lon) {
            segments.push({ from, to, transport: to.transport });
          }
        }
        return segments.map((seg, si) => (
          <Polyline
            key={`${r.id}-${si}`}
            positions={[[seg.from.lat, seg.from.lon], [seg.to.lat, seg.to.lon]]}
            pathOptions={{
              color,
              weight: selectedId === r.id ? 3 : 2,
              opacity,
              dashArray: dashArray(seg.transport),
            }}
            eventHandlers={{ click: () => onSelectRecord(selectedId === r.id ? null : r.id) }}
          >
            <Tooltip sticky>
              <span className="text-xs">
                {TRANSPORT_EMOJI[seg.transport as keyof typeof TRANSPORT_EMOJI] ?? ""}{" "}
                {seg.from.city} → {seg.to.city}
              </span>
            </Tooltip>
          </Polyline>
        ));
      })}

      {/* City markers */}
      {Object.values(allCities).map((c) => {
        const tripIdx = c.trips.length === 1
          ? records.findIndex((r) => r.id === c.trips[0])
          : -1;
        const color = tripIdx >= 0 ? TRIP_COLORS[tripIdx % TRIP_COLORS.length] : "#6b7280";
        const isSelected = selectedId !== null && c.trips.includes(selectedId);
        const faded = selectedId !== null && !c.trips.includes(selectedId);
        return (
          <CircleMarker
            key={`${c.city}__${c.country}`}
            center={[c.lat, c.lon]}
            radius={isSelected ? 7 : 5}
            pathOptions={{
              fillColor: color,
              color: "#fff",
              weight: 1.5,
              fillOpacity: faded ? 0.3 : 0.9,
              opacity: faded ? 0.3 : 1,
            }}
          >
            <Tooltip direction="top" offset={[0, -6]} permanent={false}>
              <span className="text-xs font-medium">{c.city}, {c.country}</span>
            </Tooltip>
          </CircleMarker>
        );
      })}
    </MapContainer>
  );
}
