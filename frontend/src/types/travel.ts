export const TRANSPORT_TYPES = ["PLANE", "TRAIN", "BUS", "FERRY", "CAR", "WALK"] as const;
export type TransportType = typeof TRANSPORT_TYPES[number];

export const TRANSPORT_LABELS: Record<TransportType, string> = {
  PLANE: "Plane",
  TRAIN: "Train",
  BUS:   "Bus",
  FERRY: "Ferry",
  CAR:   "Car",
  WALK:  "Walk",
};

export const TRANSPORT_EMOJI: Record<TransportType, string> = {
  PLANE: "✈",
  TRAIN: "🚆",
  BUS:   "🚌",
  FERRY: "⛴",
  CAR:   "🚗",
  WALK:  "🚶",
};

export interface TravelStop {
  city:         string;
  country:      string;
  lat:          number;
  lon:          number;
  transport:    TransportType | null;
  flightNumber?: string;
  seatNumber?:   string;
  notes?:       string;
}

export interface TravelExpense {
  category: string;
  amount:   number;
  currency: string;
}

export interface TravelRecord {
  id:         string;
  ownerEmail: string;
  title:      string;
  startDate:  string;
  endDate:    string;
  stops:      TravelStop[];
  expenses:   TravelExpense[];
  notes?:     string;
  createdAt:  string;
  updatedAt:  string;
}

export function tripDays(record: TravelRecord): number {
  const start = new Date(record.startDate);
  const end   = new Date(record.endDate);
  return Math.max(1, Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1);
}

export const TRIP_COLORS = [
  "#3b82f6", "#10b981", "#f59e0b", "#ef4444",
  "#8b5cf6", "#06b6d4", "#f97316", "#ec4899",
];

export const CITY_LOOKUP: Record<string, { country: string; lat: number; lon: number }> = {
  "Hong Kong":       { country: "China",          lat: 22.3193,  lon: 114.1694 },
  "Macau":           { country: "China",          lat: 22.1987,  lon: 113.5439 },
  "Guangzhou":       { country: "China",          lat: 23.1291,  lon: 113.2644 },
  "Shenzhen":        { country: "China",          lat: 22.5431,  lon: 114.0579 },
  "Beijing":         { country: "China",          lat: 39.9042,  lon: 116.4074 },
  "Shanghai":        { country: "China",          lat: 31.2304,  lon: 121.4737 },
  "Chengdu":         { country: "China",          lat: 30.5728,  lon: 104.0668 },
  "Xi'an":           { country: "China",          lat: 34.3416,  lon: 108.9398 },
  "Taipei":          { country: "Taiwan",         lat: 25.0320,  lon: 121.5654 },
  "Tokyo":           { country: "Japan",          lat: 35.6762,  lon: 139.6503 },
  "Kyoto":           { country: "Japan",          lat: 35.0116,  lon: 135.7681 },
  "Osaka":           { country: "Japan",          lat: 34.6937,  lon: 135.5023 },
  "Seoul":           { country: "South Korea",    lat: 37.5665,  lon: 126.9780 },
  "Singapore":       { country: "Singapore",      lat: 1.3521,   lon: 103.8198 },
  "Kuala Lumpur":    { country: "Malaysia",       lat: 3.1390,   lon: 101.6869 },
  "Bangkok":         { country: "Thailand",       lat: 13.7563,  lon: 100.5018 },
  "Phuket":          { country: "Thailand",       lat: 7.8804,   lon: 98.3923  },
  "Bali":            { country: "Indonesia",      lat: -8.3405,  lon: 115.0920 },
  "Jakarta":         { country: "Indonesia",      lat: -6.2088,  lon: 106.8456 },
  "Manila":          { country: "Philippines",    lat: 14.5995,  lon: 120.9842 },
  "Ho Chi Minh City":{ country: "Vietnam",        lat: 10.8231,  lon: 106.6297 },
  "Hanoi":           { country: "Vietnam",        lat: 21.0285,  lon: 105.8542 },
  "Mumbai":          { country: "India",          lat: 19.0760,  lon: 72.8777  },
  "Delhi":           { country: "India",          lat: 28.7041,  lon: 77.1025  },
  "Dubai":           { country: "UAE",            lat: 25.2048,  lon: 55.2708  },
  "London":          { country: "United Kingdom", lat: 51.5074,  lon: -0.1278  },
  "Edinburgh":       { country: "United Kingdom", lat: 55.9533,  lon: -3.1883  },
  "Dublin":          { country: "Ireland",        lat: 53.3498,  lon: -6.2603  },
  "Paris":           { country: "France",         lat: 48.8566,  lon: 2.3522   },
  "Nice":            { country: "France",         lat: 43.7102,  lon: 7.2620   },
  "Amsterdam":       { country: "Netherlands",    lat: 52.3676,  lon: 4.9041   },
  "Brussels":        { country: "Belgium",        lat: 50.8503,  lon: 4.3517   },
  "Berlin":          { country: "Germany",        lat: 52.5200,  lon: 13.4050  },
  "Munich":          { country: "Germany",        lat: 48.1351,  lon: 11.5820  },
  "Frankfurt":       { country: "Germany",        lat: 50.1109,  lon: 8.6821   },
  "Zurich":          { country: "Switzerland",    lat: 47.3769,  lon: 8.5417   },
  "Geneva":          { country: "Switzerland",    lat: 46.2044,  lon: 6.1432   },
  "Vienna":          { country: "Austria",        lat: 48.2082,  lon: 16.3738  },
  "Prague":          { country: "Czech Republic", lat: 50.0755,  lon: 14.4378  },
  "Budapest":        { country: "Hungary",        lat: 47.4979,  lon: 19.0402  },
  "Rome":            { country: "Italy",          lat: 41.9028,  lon: 12.4964  },
  "Milan":           { country: "Italy",          lat: 45.4654,  lon: 9.1859   },
  "Venice":          { country: "Italy",          lat: 45.4408,  lon: 12.3155  },
  "Florence":        { country: "Italy",          lat: 43.7696,  lon: 11.2558  },
  "Barcelona":       { country: "Spain",          lat: 41.3851,  lon: 2.1734   },
  "Madrid":          { country: "Spain",          lat: 40.4168,  lon: -3.7038  },
  "Lisbon":          { country: "Portugal",       lat: 38.7223,  lon: -9.1393  },
  "Athens":          { country: "Greece",         lat: 37.9838,  lon: 23.7275  },
  "Istanbul":        { country: "Turkey",         lat: 41.0082,  lon: 28.9784  },
  "Moscow":          { country: "Russia",         lat: 55.7558,  lon: 37.6173  },
  "Copenhagen":      { country: "Denmark",        lat: 55.6761,  lon: 12.5683  },
  "Stockholm":       { country: "Sweden",         lat: 59.3293,  lon: 18.0686  },
  "Oslo":            { country: "Norway",         lat: 59.9139,  lon: 10.7522  },
  "Helsinki":        { country: "Finland",        lat: 60.1699,  lon: 24.9384  },
  "New York":        { country: "United States",  lat: 40.7128,  lon: -74.0060 },
  "Los Angeles":     { country: "United States",  lat: 34.0522,  lon: -118.2437},
  "San Francisco":   { country: "United States",  lat: 37.7749,  lon: -122.4194},
  "Chicago":         { country: "United States",  lat: 41.8781,  lon: -87.6298 },
  "Miami":           { country: "United States",  lat: 25.7617,  lon: -80.1918 },
  "Las Vegas":       { country: "United States",  lat: 36.1699,  lon: -115.1398},
  "Toronto":         { country: "Canada",         lat: 43.6532,  lon: -79.3832 },
  "Vancouver":       { country: "Canada",         lat: 49.2827,  lon: -123.1207},
  "Montreal":        { country: "Canada",         lat: 45.5017,  lon: -73.5673 },
  "Sydney":          { country: "Australia",      lat: -33.8688, lon: 151.2093 },
  "Melbourne":       { country: "Australia",      lat: -37.8136, lon: 144.9631 },
  "Auckland":        { country: "New Zealand",    lat: -36.8509, lon: 174.7645 },
  "Cape Town":       { country: "South Africa",   lat: -33.9249, lon: 18.4241  },
  "Cairo":           { country: "Egypt",          lat: 30.0444,  lon: 31.2357  },
  "Nairobi":         { country: "Kenya",          lat: -1.2921,  lon: 36.8219  },
  "Mexico City":     { country: "Mexico",         lat: 19.4326,  lon: -99.1332 },
  "São Paulo":       { country: "Brazil",         lat: -23.5505, lon: -46.6333 },
  "Buenos Aires":    { country: "Argentina",      lat: -34.6037, lon: -58.3816 },
};
