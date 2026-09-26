import SwiftUI
import Charts

/// Mirrors the web app's client-side `computeAnalysis` (TravelManager.tsx) — purely a local
/// aggregation over already-loaded trips, no backend call. First/last stop of each trip is
/// treated as the home base and excluded from country/city/transport counts, matching the web
/// version's `stops.slice(1, -1)`.
private struct TravelAnalysisStats {
    let totalTrips: Int
    let totalDays: Int
    let totalCities: Int
    let countries: [String]
    /// Countries by number of trips that visited them, most-visited first.
    let countryVisits: [(name: String, count: Int)]
    let topCountry: (name: String, count: Int)?
    let topCity: (name: String, count: Int)?
    let transportCounts: [TransportType: Int]
    let longestTrip: TravelRecord
    let shortestTrip: TravelRecord
    let years: [String]
    /// Trip count per start year, oldest first — feeds the per-year bar chart.
    let tripsPerYear: [(year: String, count: Int)]

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
        countryVisits = countryCounts
            .sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }
            .map { (name: $0.key, count: $0.value) }
        // Ties break alphabetically — `Dictionary` order is random, so a bare `max` would
        // show a different "top" city each time the sheet opens.
        let ranked: ((key: String, value: Int), (key: String, value: Int)) -> Bool = {
            $0.value != $1.value ? $0.value < $1.value : $0.key > $1.key
        }
        topCountry = countryCounts.max(by: ranked).map { (name: $0.key, count: $0.value) }
        topCity = cityCounts.max(by: ranked).map { (name: $0.key, count: $0.value) }
        self.transportCounts = transportCounts

        let sortedByDays = trips.sorted { $0.dayCount > $1.dayCount }
        longestTrip = sortedByDays[0]
        shortestTrip = sortedByDays[sortedByDays.count - 1]

        years = Array(Set(trips.map { String($0.startDate.prefix(4)) })).sorted(by: >)
        var perYear: [String: Int] = [:]
        for trip in trips { perYear[String(trip.startDate.prefix(4)), default: 0] += 1 }
        tripsPerYear = perYear.sorted { $0.key < $1.key }.map { (year: $0.key, count: $0.value) }
    }
}

struct TravelAnalysisView: View {
    let trips: [TravelRecord]

    private var stats: TravelAnalysisStats? { TravelAnalysisStats(trips: trips) }

    var body: some View {
        FormSheet("Your travels", subtitle: stats.map(subtitle)) {
            if let stats {
                heroCard(stats)
                if stats.tripsPerYear.count > 1 { yearsCard(stats) }
                highlightsGrid(stats)
                if !stats.transportCounts.isEmpty { transportCard(stats) }
                if !stats.countryVisits.isEmpty { countriesCard(stats) }
            } else {
                emptyState
            }
        }
        .tint(Theme.travel)
    }

    private func subtitle(_ stats: TravelAnalysisStats) -> String {
        guard let first = stats.years.last, let last = stats.years.first else { return "" }
        return first == last ? "Everything from \(first)" : "Everything from \(first) to \(last)"
    }

    // MARK: Hero

    private func heroCard(_ stats: TravelAnalysisStats) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(stats.totalDays)")
                    .font(Theme.serif(52, weight: .regular))
                    .contentTransition(.numericText())
                Text(stats.totalDays == 1 ? "day on the road" : "days on the road")
                    .font(.system(size: 15, weight: .medium))
                    .opacity(0.85)
            }
            HStack(spacing: 0) {
                heroStat(stats.totalTrips, "Trips")
                heroDivider
                heroStat(stats.countries.count, "Countries")
                heroDivider
                heroStat(stats.totalCities, "Cities")
            }
        }
        .foregroundStyle(Color.white)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(22)
        .background(
            ZStack(alignment: .topTrailing) {
                LinearGradient(colors: [Theme.travel, Theme.travel.opacity(0.82)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
                Image(systemName: "globe.asia.australia.fill")
                    .font(.system(size: 130))
                    .foregroundStyle(Color.white.opacity(0.10))
                    .offset(x: 30, y: -24)
            }
        )
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: Theme.travel.opacity(0.28), radius: 18, y: 8)
    }

    private func heroStat(_ value: Int, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(value)").font(Theme.serif(24, weight: .semibold))
            Text(label).font(.system(size: 12, weight: .medium)).opacity(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var heroDivider: some View {
        Rectangle().fill(Color.white.opacity(0.25)).frame(width: 1, height: 32).padding(.trailing, 14)
    }

    // MARK: Trips per year

    private func yearsCard(_ stats: TravelAnalysisStats) -> some View {
        FormSection("Trips per year") {
            Chart(stats.tripsPerYear, id: \.year) { item in
                BarMark(x: .value("Year", item.year), y: .value("Trips", item.count), width: .ratio(0.55))
                    .foregroundStyle(Theme.travel.gradient)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    .annotation(position: .top, spacing: 4) {
                        Text("\(item.count)")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(Theme.inkSoft)
                    }
            }
            .chartYAxis(.hidden)
            .chartXAxis {
                AxisMarks { _ in
                    AxisValueLabel().font(.caption2).foregroundStyle(Theme.inkFaint)
                }
            }
            .frame(height: 150)
            .padding(16)
        }
    }

    // MARK: Highlights

    private func highlightsGrid(_ stats: TravelAnalysisStats) -> some View {
        var tiles: [(icon: String, label: String, value: String, detail: String)] = []
        if let top = stats.topCountry {
            tiles.append(("flag.fill", "Top country", top.name, visits(top.count)))
        }
        if let top = stats.topCity {
            tiles.append(("building.2.fill", "Top city", top.name, visits(top.count)))
        }
        tiles.append(("arrow.up.right", "Longest trip", stats.longestTrip.title, days(stats.longestTrip.dayCount)))
        if stats.longestTrip.id != stats.shortestTrip.id {
            tiles.append(("arrow.down.right", "Shortest trip", stats.shortestTrip.title, days(stats.shortestTrip.dayCount)))
        }
        return VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: "Highlights").padding(.horizontal, 4)
            // `Grid` (not LazyVGrid) so both tiles in a row stretch to the taller one's height.
            Grid(horizontalSpacing: 10, verticalSpacing: 10) {
                ForEach(Array(stride(from: 0, to: tiles.count, by: 2)), id: \.self) { i in
                    GridRow {
                        ForEach(tiles[i..<min(i + 2, tiles.count)], id: \.label) { tile in
                            highlightTile(icon: tile.icon, label: tile.label, value: tile.value, detail: tile.detail)
                        }
                        if i + 1 >= tiles.count { Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]) }
                    }
                }
            }
        }
    }

    private func highlightTile(icon: String, label: String, value: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.travel)
                .frame(width: 30, height: 30)
                .background(Theme.travelSoft)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(label.uppercased())
                    .font(.system(size: 10.5, weight: .semibold))
                    .kerning(0.4)
                    .foregroundStyle(Theme.inkFaint)
                Text(value)
                    .font(Theme.serif(18, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                Text(detail).font(.caption).foregroundStyle(Theme.inkSoft)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(14)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.hairline, lineWidth: 1))
    }

    private func visits(_ n: Int) -> String { n == 1 ? "1 visit" : "\(n) visits" }
    private func days(_ n: Int) -> String { n == 1 ? "1 day" : "\(n) days" }

    // MARK: Transport

    /// Terracotta shades, darkest for the most-used mode, so the stacked bar reads as one family.
    private let transportShades: [Double] = [1.0, 0.72, 0.5, 0.34, 0.22, 0.14]

    private func transportCard(_ stats: TravelAnalysisStats) -> some View {
        let total = stats.transportCounts.values.reduce(0, +)
        let entries = stats.transportCounts.sorted { $0.value > $1.value }
        return FormSection("How you got around") {
            VStack(alignment: .leading, spacing: 16) {
                GeometryReader { geo in
                    HStack(spacing: 3) {
                        ForEach(Array(entries.enumerated()), id: \.element.key) { idx, entry in
                            RoundedRectangle(cornerRadius: 4, style: .continuous)
                                .fill(Theme.travel.opacity(transportShades[min(idx, transportShades.count - 1)]))
                                .frame(width: max(6, (geo.size.width - CGFloat(entries.count - 1) * 3)
                                                     * CGFloat(entry.value) / CGFloat(max(total, 1))))
                        }
                    }
                }
                .frame(height: 12)
                .clipShape(Capsule())

                VStack(spacing: 12) {
                    ForEach(Array(entries.enumerated()), id: \.element.key) { idx, entry in
                        let pct = total > 0 ? Int((Double(entry.value) / Double(total) * 100).rounded()) : 0
                        HStack(spacing: 12) {
                            Image(systemName: entry.key.symbolName)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(idx < 2 ? Color.white : Theme.travel)
                                .frame(width: 32, height: 32)
                                .background(Theme.travel.opacity(transportShades[min(idx, transportShades.count - 1)]))
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            Text(entry.key.label).font(.system(size: 15, weight: .medium)).foregroundStyle(Theme.ink)
                            Spacer()
                            Text(entry.value == 1 ? "1 leg" : "\(entry.value) legs")
                                .font(.system(size: 13)).foregroundStyle(Theme.inkFaint)
                            Text("\(pct)%")
                                .font(.system(size: 15, weight: .semibold).monospacedDigit())
                                .foregroundStyle(Theme.ink)
                                .frame(minWidth: 42, alignment: .trailing)
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    // MARK: Countries

    private func countriesCard(_ stats: TravelAnalysisStats) -> some View {
        FormSection("Countries visited · \(stats.countryVisits.count)") {
            TravelFlowLayout(spacing: 8) {
                ForEach(stats.countryVisits, id: \.name) { item in
                    HStack(spacing: 6) {
                        Text(item.name).font(.system(size: 13, weight: .medium)).foregroundStyle(Theme.ink)
                        if item.count > 1 {
                            Text("×\(item.count)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(Theme.travel)
                        }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(item.count > 1 ? Theme.travelSoft : Theme.chipFill)
                    .clipShape(Capsule())
                }
            }
            .padding(16)
        }
    }

    // MARK: Empty

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "suitcase.rolling.fill")
                .font(.system(size: 28))
                .foregroundStyle(Theme.travel)
                .frame(width: 68, height: 68)
                .background(Theme.travelSoft)
                .clipShape(Circle())
            Text("No trips yet").font(Theme.serif(22)).foregroundStyle(Theme.ink)
            Text("Add a trip and your travel stats will show up here.")
                .font(.system(size: 14)).foregroundStyle(Theme.inkFaint)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
    }
}

/// Wrapping row layout for the country chips — they flow onto new lines instead of
/// being squeezed into fixed grid columns (which truncated long names).
private struct TravelFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                y += rowHeight + spacing
                x = 0
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: min(widest, maxWidth), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                y += rowHeight + spacing
                x = bounds.minX
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

private extension TransportType {
    var symbolName: String {
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
