import SwiftUI
import MapKit

/// Same palette as the web app's `TRIP_COLORS` (types/travel.ts), cycled by trip index, so a
/// trip reads as the same color on both platforms' multi-trip overview maps.
let tripColors: [Color] = [
    Color(hex: 0x3b82f6), Color(hex: 0x10b981), Color(hex: 0xf59e0b), Color(hex: 0xef4444),
    Color(hex: 0x8b5cf6), Color(hex: 0x06b6d4), Color(hex: 0xf97316), Color(hex: 0xec4899),
]

/// Mirrors the web app's Leaflet map's `dashArray` convention: plane legs dashed, ferry legs
/// finely dashed, everything else (train/bus/car/walk) solid.
func mapDashPattern(for transport: TransportType?) -> [CGFloat] {
    switch transport {
    case .plane: return [8, 6]
    case .ferry: return [3, 5]
    default:     return []
    }
}

/// A trip's stops as consecutive (from, to) legs, e.g. for drawing one polyline per leg.
func legs(for stops: [TravelStop]) -> [(from: TravelStop, to: TravelStop)] {
    guard stops.count > 1 else { return [] }
    return Array(zip(stops, stops.dropFirst()))
}

/// Smallest region containing every stop across the given trips, with a little padding —
/// used to auto-fit the camera on both the single-trip and multi-trip overview maps.
func fitRegion(for trips: [TravelRecord]) -> MapCameraPosition {
    let coords = trips.flatMap(\.stops).map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
    guard let first = coords.first else { return .automatic }
    var minLat = first.latitude, maxLat = first.latitude
    var minLon = first.longitude, maxLon = first.longitude
    for c in coords {
        minLat = min(minLat, c.latitude); maxLat = max(maxLat, c.latitude)
        minLon = min(minLon, c.longitude); maxLon = max(maxLon, c.longitude)
    }
    let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
    let span = MKCoordinateSpan(
        latitudeDelta: max((maxLat - minLat) * 1.7, 6),
        longitudeDelta: max((maxLon - minLon) * 1.7, 6)
    )
    return .region(MKCoordinateRegion(center: center, span: span))
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
