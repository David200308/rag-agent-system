import type { TravelRecord } from "@/types/travel";

export interface LatLon { lat: number; lon: number }

const CORE_RADIUS_KM = 3000;

function distanceKm(a: LatLon, b: LatLon): number {
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLon = (b.lon - a.lon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}

/**
 * Points of the user's "home region": the densest cluster of visited cities, weighted by how
 * many times each city was stopped at. Fitting to this instead of every stop keeps one-off
 * long-haul trips from zooming the map out to the whole world.
 */
export function corePoints(records: TravelRecord[]): LatLon[] {
  const visits: Record<string, LatLon & { count: number }> = {};
  for (const r of records) {
    for (const s of r.stops) {
      if (!s.lat || !s.lon) continue;
      const key = `${s.city}__${s.country}`;
      visits[key] ??= { lat: s.lat, lon: s.lon, count: 0 };
      visits[key].count++;
    }
  }
  const cities = Object.values(visits);
  let center: LatLon | undefined;
  let best = -1;
  for (const c of cities) {
    const score = cities.reduce((sum, o) => (distanceKm(c, o) <= CORE_RADIUS_KM ? sum + o.count : sum), 0);
    if (score > best) { best = score; center = c; }
  }
  if (!center) return [];
  const c0 = center;
  return cities.filter((c) => distanceKm(c0, c) <= CORE_RADIUS_KM);
}
