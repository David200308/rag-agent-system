"use client";

import { useEffect, useState } from "react";
import type { TravelRecord } from "@/types/travel";
import TravelMap from "./TravelMap";
import GoogleTravelMap from "./GoogleTravelMap";

interface Props {
  records: TravelRecord[];
  selectedId: string | null;
  onSelectRecord: (id: string | null) => void;
}

interface MapsConfig { googleMapsApiKey: string | null; googleMapsMapId: string | null }

/** Uses Google Maps when GOOGLE_MAPS_API_KEY is configured, otherwise the free Leaflet/Esri map. */
export default function TravelMapView(props: Props) {
  const [config, setConfig] = useState<MapsConfig | null>(null);

  useEffect(() => {
    fetch("/api/config/maps")
      .then((res) => (res.ok ? res.json() : null))
      .then((data: MapsConfig | null) => setConfig(data ?? { googleMapsApiKey: null, googleMapsMapId: null }))
      .catch(() => setConfig({ googleMapsApiKey: null, googleMapsMapId: null }));
  }, []);

  if (!config) return <div className="h-full w-full bg-[--color-surface]" />;
  if (config.googleMapsApiKey) {
    return <GoogleTravelMap apiKey={config.googleMapsApiKey} mapId={config.googleMapsMapId} {...props} />;
  }
  return <TravelMap {...props} />;
}
