/// <reference types="google.maps" />
"use client";

import { useEffect, useMemo } from "react";
import { APIProvider, Map, useMap, useMapsLibrary } from "@vis.gl/react-google-maps";
import type { TravelRecord, TravelStop } from "@/types/travel";
import { TRIP_COLORS, TRANSPORT_EMOJI } from "@/types/travel";

interface Props {
  apiKey: string;
  mapId: string | null;
  records: TravelRecord[];
  selectedId: string | null;
  onSelectRecord: (id: string | null) => void;
}

interface CityPoint { lat: number; lon: number; city: string; country: string; trips: string[] }

function collectCities(records: TravelRecord[]): CityPoint[] {
  const cities: Record<string, CityPoint> = {};
  for (const r of records) {
    for (const s of r.stops) {
      if (!s.lat || !s.lon) continue;
      const key = `${s.city}__${s.country}`;
      if (!cities[key]) cities[key] = { lat: s.lat, lon: s.lon, city: s.city, country: s.country, trips: [] };
      if (!cities[key].trips.includes(r.id)) cities[key].trips.push(r.id);
    }
  }
  return Object.values(cities);
}

// Google polylines have no dashArray — dashes/dots are drawn as repeated symbols.
function strokeIcons(transport: string | null, opacity: number, weight: number): google.maps.IconSequence[] | undefined {
  if (transport === "PLANE") {
    return [{ icon: { path: "M 0,-1 0,1", strokeOpacity: opacity, strokeWeight: weight, scale: 3 }, offset: "0", repeat: "14px" }];
  }
  if (transport === "FERRY") {
    return [{ icon: { path: google.maps.SymbolPath.CIRCLE, strokeOpacity: opacity, fillOpacity: opacity, scale: 1 }, offset: "0", repeat: "8px" }];
  }
  return undefined;
}

function Overlays({ records, selectedId, onSelectRecord }: Omit<Props, "apiKey" | "mapId">) {
  const map = useMap();
  const core = useMapsLibrary("core");
  const cities = useMemo(() => collectCities(records), [records]);

  // Fit to all stops once the map is ready / when trips are added or removed
  useEffect(() => {
    if (!map || !core || cities.length === 0) return;
    const bounds = new google.maps.LatLngBounds();
    cities.forEach((c) => bounds.extend({ lat: c.lat, lng: c.lon }));
    map.fitBounds(bounds, 50);
    const once = map.addListener("idle", () => {
      if ((map.getZoom() ?? 0) > 8) map.setZoom(8);
      once.remove();
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, core, records.length]);

  // Routes + city markers, redrawn whenever data or selection changes
  useEffect(() => {
    if (!map || !core) return;
    const shapes: (google.maps.Polyline | google.maps.Marker)[] = [];
    const listeners: google.maps.MapsEventListener[] = [];
    const tip = new google.maps.InfoWindow({ disableAutoPan: true, headerDisabled: true });
    const showTip = (text: string, pos: google.maps.LatLng | google.maps.LatLngLiteral) => {
      tip.setContent(`<span style="font-size:12px">${text}</span>`);
      tip.setPosition(pos);
      tip.open(map);
    };

    records.forEach((r, ri) => {
      const color = TRIP_COLORS[ri % TRIP_COLORS.length];
      const opacity = selectedId === null || selectedId === r.id ? 1 : 0.2;
      const weight = selectedId === r.id ? 3 : 2;
      for (let i = 1; i < r.stops.length; i++) {
        const from: TravelStop | undefined = r.stops[i - 1];
        const to: TravelStop | undefined = r.stops[i];
        if (!from?.lat || !from.lon || !to?.lat || !to.lon) continue;
        const icons = strokeIcons(to.transport, opacity, weight);
        const line = new google.maps.Polyline({
          map,
          path: [{ lat: from.lat, lng: from.lon }, { lat: to.lat, lng: to.lon }],
          geodesic: to.transport === "PLANE",
          strokeColor: color,
          strokeOpacity: icons ? 0 : opacity,
          strokeWeight: weight,
          icons,
        });
        const label = `${TRANSPORT_EMOJI[to.transport as keyof typeof TRANSPORT_EMOJI] ?? ""} ${from.city} → ${to.city}`;
        listeners.push(
          line.addListener("click", () => onSelectRecord(selectedId === r.id ? null : r.id)),
          line.addListener("mouseover", (e: google.maps.PolyMouseEvent) => e.latLng && showTip(label, e.latLng)),
          line.addListener("mouseout", () => tip.close()),
        );
        shapes.push(line);
      }
    });

    for (const c of cities) {
      const tripIdx = c.trips.length === 1 ? records.findIndex((r) => r.id === c.trips[0]) : -1;
      const color = tripIdx >= 0 ? TRIP_COLORS[tripIdx % TRIP_COLORS.length] : "#6b7280";
      const isSelected = selectedId !== null && c.trips.includes(selectedId);
      const faded = selectedId !== null && !c.trips.includes(selectedId);
      const marker = new google.maps.Marker({
        map,
        position: { lat: c.lat, lng: c.lon },
        icon: {
          path: google.maps.SymbolPath.CIRCLE,
          scale: isSelected ? 7 : 5,
          fillColor: color,
          fillOpacity: faded ? 0.3 : 0.9,
          strokeColor: "#fff",
          strokeOpacity: faded ? 0.3 : 1,
          strokeWeight: 1.5,
        },
      });
      listeners.push(
        marker.addListener("mouseover", () => showTip(`<b>${c.city}, ${c.country}</b>`, { lat: c.lat, lng: c.lon })),
        marker.addListener("mouseout", () => tip.close()),
      );
      shapes.push(marker);
    }

    return () => {
      listeners.forEach((l) => l.remove());
      shapes.forEach((s) => s.setMap(null));
      tip.close();
    };
  }, [map, core, records, cities, selectedId, onSelectRecord]);

  return null;
}

export default function GoogleTravelMap({ apiKey, mapId, ...rest }: Props) {
  return (
    <APIProvider apiKey={apiKey}>
      <Map
        style={{ height: "100%", width: "100%" }}
        defaultCenter={{ lat: 20, lng: 10 }}
        defaultZoom={2}
        minZoom={2}
        mapId={mapId ?? undefined}
        gestureHandling="greedy"
        disableDefaultUI
        zoomControl
        restriction={{ latLngBounds: { north: 85, south: -85, west: -180, east: 180 }, strictBounds: true }}
      />
      <Overlays {...rest} />
    </APIProvider>
  );
}
