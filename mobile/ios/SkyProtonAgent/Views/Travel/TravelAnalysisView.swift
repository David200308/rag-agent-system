import SwiftUI

/// Mirrors the web app's client-side `computeAnalysis` (TravelManager.tsx) — purely a local
/// aggregation over already-loaded trips, no backend call. First/last stop of each trip is
/// treated as the home base and excluded from country/city/transport counts, matching the web
/// version's `stops.slice(1, -1)`.
private struct TravelAnalysisStats {
    let totalTrips: Int
    let totalDays: Int
    let totalCities: Int
    let countries: [String]
    let topCountry: (name: String, count: Int)?
    let topCity: (name: String, count: Int)?
    let transportCounts: [TransportType: Int]
    let longestTrip: TravelRecord
    let shortestTrip: TravelRecord
    let years: [String]

    init?(trips: [TravelRecord]) {
        guard !trips.isEmpty else { return nil }
        totalTrips = trips.count
        totalDays = trips.reduce(0) { $0 + $1.dayCount }

        var countryCounts: [String: Int] = [:]
        var cityCounts: [String: Int] = [:]
        var transportCounts: [TransportType: Int] = [:]

        for trip in trips {
            let stops = trip.stops
            let intermediate = stops.count > 2 ? Array(stops[1..<(stops.count - 1)]) : []
            for city in Set(intermediate.map(\.city)) { cityCounts[city, default: 0] += 1 }
            for country in Set(intermediate.map(\.country)) { countryCounts[country, default: 0] += 1 }
            for stop in intermediate {
                if let t = stop.transport { transportCounts[t, default: 0] += 1 }
            }
        }

        totalCities = cityCounts.count
        countries = countryCounts.keys.sorted()
        topCountry = countryCounts.max { $0.value < $1.value }.map { (name: $0.key, count: $0.value) }
        topCity = cityCounts.max { $0.value < $1.value }.map { (name: $0.key, count: $0.value) }
        self.transportCounts = transportCounts

        let sortedByDays = trips.sorted { $0.dayCount > $1.dayCount }
        longestTrip = sortedByDays[0]
        shortestTrip = sortedByDays[sortedByDays.count - 1]

        years = Array(Set(trips.map { String($0.startDate.prefix(4)) })).sorted(by: >)
    }
}

struct TravelAnalysisView: View {
    let trips: [TravelRecord]
    @Environment(\.dismiss) private var dismiss

    private var stats: TravelAnalysisStats? { TravelAnalysisStats(trips: trips) }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let stats {
                    VStack(alignment: .leading, spacing: 16) {
                        summaryGrid(stats)
                        highlightsCard(stats)
                        if !stats.transportCounts.isEmpty {
                            transportCard(stats)
                        }
                        if !stats.countries.isEmpty {
                            countriesCard(stats)
                        }
                    }
                    .padding(20)
                } else {
                    Text("No trips to analyse yet.")
                        .foregroundStyle(Theme.inkFaint)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 60)
                }
            }
            .background(Theme.background)
            .navigationTitle("Travel Analysis")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
        .tint(Theme.travel)
    }

    private func summaryGrid(_ stats: TravelAnalysisStats) -> some View {
        let items: [(label: String, value: Int)] = [
            ("Trips", stats.totalTrips),
            ("Days", stats.totalDays),
            ("Countries", stats.countries.count),
            ("Cities", stats.totalCities),
        ]
        return LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            ForEach(items, id: \.label) { item in
                VStack(spacing: 4) {
                    Text("\(item.value)").font(Theme.serif(24, weight: .bold)).foregroundStyle(Theme.ink)
                    Text(item.label).font(.caption).foregroundStyle(Theme.inkFaint)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
    }

    private func highlightsCard(_ stats: TravelAnalysisStats) -> some View {
        ThemeCard {
            VStack(alignment: .leading, spacing: 10) {
                SectionLabel(text: "Highlights")
                if let top = stats.topCountry {
                    highlightRow("Most visited country", "\(top.name) (\(top.count)x)")
                }
                if let top = stats.topCity {
                    highlightRow("Most visited city", "\(top.name) (\(top.count)x)")
                }
                highlightRow("Longest trip", "\(stats.longestTrip.title) (\(stats.longestTrip.dayCount)d)")
                if stats.longestTrip.id != stats.shortestTrip.id {
                    highlightRow("Shortest trip", "\(stats.shortestTrip.title) (\(stats.shortestTrip.dayCount)d)")
                }
                highlightRow("Active years", stats.years.joined(separator: ", "))
            }
        }
    }

    private func highlightRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label).font(.subheadline).foregroundStyle(Theme.inkSoft)
            Spacer(minLength: 12)
            Text(value).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink).multilineTextAlignment(.trailing)
        }
    }

    private func transportCard(_ stats: TravelAnalysisStats) -> some View {
        let total = stats.transportCounts.values.reduce(0, +)
        let sortedEntries = stats.transportCounts.sorted { $0.value > $1.value }
        return ThemeCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "Transport Breakdown")
                ForEach(sortedEntries, id: \.key) { type, count in
                    let pct = total > 0 ? Int((Double(count) / Double(total) * 100).rounded()) : 0
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("\(type.analysisEmoji) \(type.label)").font(.subheadline).foregroundStyle(Theme.ink)
                            Spacer()
                            Text("\(count) leg\(count != 1 ? "s" : "") · \(pct)%").font(.caption).foregroundStyle(Theme.inkFaint)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Theme.hairline).frame(height: 5)
                                Capsule().fill(Theme.travel).frame(width: geo.size.width * CGFloat(pct) / 100, height: 5)
                            }
                        }
                        .frame(height: 5)
                    }
                }
            }
        }
    }

    private func countriesCard(_ stats: TravelAnalysisStats) -> some View {
        ThemeCard {
            VStack(alignment: .leading, spacing: 10) {
                SectionLabel(text: "Countries Visited")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 8)], alignment: .leading, spacing: 8) {
                    ForEach(stats.countries, id: \.self) { country in
                        Text(country)
                            .font(.caption)
                            .padding(.horizontal, 10).padding(.vertical, 5)
                            .background(Theme.chipFill)
                            .foregroundStyle(Theme.ink)
                            .clipShape(Capsule())
                    }
                }
            }
        }
    }
}

private extension TransportType {
    var analysisEmoji: String {
        switch self {
        case .plane: return "✈"
        case .train: return "🚆"
        case .bus:   return "🚌"
        case .ferry: return "⛴"
        case .car:   return "🚗"
        case .walk:  return "🚶"
        }
    }
}
