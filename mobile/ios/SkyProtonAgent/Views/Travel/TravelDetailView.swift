import SwiftUI
import MapKit

struct TravelDetailView: View {
    let trip: TravelRecord
    @StateObject private var expenses: TripExpensesStore
    @State private var editorTarget: ExpenseEditorTarget?

    init(trip: TravelRecord) {
        self.trip = trip
        _expenses = StateObject(wrappedValue: TripExpensesStore(trip: trip))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {

                    VStack(alignment: .leading, spacing: 2) {
                        Text(trip.title).font(Theme.serif(24, weight: .semibold)).foregroundStyle(Theme.ink)
                        Text(trip.dateRangeLabel).font(.subheadline).foregroundStyle(Theme.inkFaint)
                    }

                    if !trip.stops.isEmpty {
                        RouteMapView(stops: trip.stops, trip: trip)
                            .frame(height: 190)
                            .background(Theme.chipFill)
                            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    }

                    if !trip.stops.isEmpty {
                        SectionLabel(text: "Itinerary")
                        itineraryCard
                    }

                    TripExpensesSection(
                        store: expenses,
                        scrollTo: { id in withAnimation(.easeInOut) { proxy.scrollTo(id, anchor: .top) } },
                        editorTarget: $editorTarget
                    )
                }
                .padding(20)
                // Room for the floating add button above the tab bar.
                .padding(.bottom, expenses.state == .ready && !expenses.data.isEmpty ? 56 : 0)
            }
        }
        .background(Theme.background)
        .tabBarSafeArea()
        .overlay(alignment: .bottomTrailing) {
            if expenses.state == .ready && !expenses.data.isEmpty {
                AddExpenseButton {
                    editorTarget = ExpenseEditorTarget(entry: nil, date: expenses.suggestedDate)
                }
                .padding(.trailing, 20)
                .padding(.bottom, 92)
                .transition(.scale.combined(with: .opacity))
            }
        }
        .sheet(item: $editorTarget) { target in
            ExpenseEditSheet(store: expenses, target: target)
        }
        .navigationTitle("Trip")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            // The trip list omits expenses (see `TravelRecord`), so fetch them on first appearance.
            if expenses.state == .loading { await expenses.load() }
        }
    }

    private var itineraryCard: some View {
        ThemeCard {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(trip.stops.enumerated()), id: \.offset) { index, stop in
                    HStack(alignment: .top, spacing: 10) {
                        Circle().fill(Theme.travel).frame(width: 9, height: 9).padding(.top, 5)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(stop.city), \(stop.country)").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                            if let notes = stop.notes, !notes.isEmpty {
                                Text(notes).font(.caption).foregroundStyle(Theme.inkFaint)
                            }
                        }
                    }
                    .padding(.vertical, 8)

                    if index < trip.stops.count - 1 {
                        let next = trip.stops[index + 1]
                        Rectangle().fill(Theme.hairline).frame(width: 1, height: 14).padding(.leading, 4)
                        HStack(spacing: 6) {
                            if let t = next.transport {
                                Image(systemName: t.sfSymbol).font(.caption)
                            }
                            Text(connectorLabel(for: next)).font(.caption)
                        }
                        .foregroundStyle(Theme.inkSoft)
                        .padding(.leading, 20)
                        Rectangle().fill(Theme.hairline).frame(width: 1, height: 14).padding(.leading, 4)
                    }
                }
            }
        }
    }

    private func connectorLabel(for stop: TravelStop) -> String {
        var parts: [String] = [stop.transport?.label ?? "Travel"]
        if let flight = stop.flightNumber, !flight.isEmpty { parts.append(flight) }
        if let seat = stop.seatNumber, !seat.isEmpty { parts.append("Seat \(seat)") }
        return parts.joined(separator: " · ")
    }
}

private extension TransportType {
    var sfSymbol: String {
        switch self {
        case .plane: return "airplane"
        case .train: return "tram.fill"
        case .bus:   return "bus.fill"
        case .ferry: return "ferry.fill"
        case .car:   return "car.fill"
        case .walk:  return "figure.walk"
        }
    }
}

/// A real MapKit route: a marker per stop, a polyline connecting them in order, dashed to
/// match each leg's transport (mirrors the web app's Leaflet map's `dashArray` convention —
/// plane legs dashed, ferry legs finely dashed, everything else solid).
private struct RouteMapView: View {
    let stops: [TravelStop]
    let trip: TravelRecord

    var body: some View {
        Map(initialPosition: fitRegion(for: [trip])) {
            ForEach(Array(legs(for: stops).enumerated()), id: \.offset) { _, leg in
                MapPolyline(coordinates: [
                    CLLocationCoordinate2D(latitude: leg.from.lat, longitude: leg.from.lon),
                    CLLocationCoordinate2D(latitude: leg.to.lat, longitude: leg.to.lon),
                ])
                .stroke(Theme.travel, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: mapDashPattern(for: leg.to.transport)))
            }
            ForEach(Array(stops.enumerated()), id: \.offset) { _, stop in
                Annotation(stop.city, coordinate: CLLocationCoordinate2D(latitude: stop.lat, longitude: stop.lon)) {
                    VStack(spacing: 3) {
                        Circle()
                            .fill(Theme.surface)
                            .overlay(Circle().stroke(Theme.travel, lineWidth: 2))
                            .frame(width: 10, height: 10)
                        Text(stop.city)
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(Theme.inkSoft)
                            .padding(.horizontal, 5).padding(.vertical, 2)
                            .background(Theme.surface.opacity(0.9))
                            .clipShape(Capsule())
                    }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
    }
}
