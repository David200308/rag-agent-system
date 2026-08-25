import SwiftUI

struct TravelDetailView: View {
    let trip: TravelRecord

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {

                VStack(alignment: .leading, spacing: 2) {
                    Text(trip.title).font(Theme.serif(24, weight: .semibold)).foregroundStyle(Theme.ink)
                    Text(trip.dateRangeLabel).font(.subheadline).foregroundStyle(Theme.inkFaint)
                }

                if !trip.stops.isEmpty {
                    RouteMapView(stops: trip.stops)
                        .frame(height: 190)
                        .background(Theme.chipFill)
                        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                }

                if !trip.stops.isEmpty {
                    SectionLabel(text: "Itinerary")
                    itineraryCard
                }

                if let data = trip.expenseData, !data.dateExpenses.isEmpty {
                    HStack {
                        SectionLabel(text: "Expenses")
                        Spacer()
                        Text(expenseTotalLabel(data))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.ink)
                    }
                    expensesCard(data)
                }
            }
            .padding(20)
        }
        .background(Theme.background)
        .tabBarSafeArea()
        .navigationTitle("Trip")
        .navigationBarTitleDisplayMode(.inline)
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

    private func expensesCard(_ data: TripExpenseData) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(data.dateExpenses.enumerated()), id: \.offset) { index, group in
                if !group.entries.isEmpty {
                    dateGroupCard(group, currency: data.defaultCurrency)
                }
            }
        }
    }

    private func dateGroupCard(_ group: DateExpenseGroup, currency: String) -> some View {
        let subtotal = group.entries.reduce(0.0) { $0 + ($1.amounts[currency] ?? 0) }
        return ThemeCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(formattedGroupDate(group.date))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.inkSoft)
                    Spacer()
                    Text(formatAmount(subtotal, currency: currency))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.inkFaint)
                        .monospacedDigit()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Theme.chipFill)

                ForEach(Array(group.entries.enumerated()), id: \.offset) { idx, entry in
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.name).font(.subheadline).foregroundStyle(Theme.ink)
                            if entry.cashback > 0 {
                                tag("Cashback \(formatAmount(entry.cashback, currency: currency))", tint: Theme.positive)
                            }
                        }
                        Spacer(minLength: 12)
                        Text(formatAmount(entry.amounts[currency] ?? entry.amounts.values.first ?? 0, currency: currency))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.ink)
                            .monospacedDigit()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    if idx < group.entries.count - 1 {
                        Divider().overlay(Theme.hairline).padding(.leading, 16)
                    }
                }
            }
        }
    }

    private func tag(_ text: String, tint: Color) -> some View {
        Text(text)
            .font(.caption2.weight(.medium))
            .padding(.horizontal, 7).padding(.vertical, 2)
            .background(tint.opacity(0.12))
            .foregroundStyle(tint)
            .clipShape(Capsule())
    }

    private func formattedGroupDate(_ raw: String) -> String {
        let inFmt = DateFormatter()
        inFmt.dateFormat = "yyyy-MM-dd"
        inFmt.timeZone = TimeZone(identifier: "UTC")
        guard let date = inFmt.date(from: raw) else { return raw }
        let outFmt = DateFormatter()
        outFmt.dateFormat = "EEE, MMM d"
        return outFmt.string(from: date)
    }

    private func expenseTotalLabel(_ data: TripExpenseData) -> String {
        let total = data.dateExpenses
            .flatMap(\.entries)
            .reduce(0.0) { $0 + ($1.amounts[data.defaultCurrency] ?? 0) }
        return formatAmount(total, currency: data.defaultCurrency)
    }

    private func formatAmount(_ value: Double, currency: String) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = currency
        f.maximumFractionDigits = 0
        return f.string(from: NSNumber(value: value)) ?? "\(currency) \(Int(value))"
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

/// A stylized route diagram (not a real map — no network map tiles are fetched).
private struct RouteMapView: View {
    let stops: [TravelStop]

    var body: some View {
        GeometryReader { geo in
            let n = max(stops.count, 2)
            let pad: CGFloat = 28
            let points: [CGPoint] = (0..<n).map { i in
                let x = pad + (geo.size.width - 2 * pad) * CGFloat(i) / CGFloat(n - 1)
                let wobble: CGFloat = (i % 2 == 0) ? 0.72 : 0.32
                let y = pad + (geo.size.height - 2 * pad) * wobble
                return CGPoint(x: x, y: y)
            }
            ZStack {
                Path { path in
                    guard let first = points.first else { return }
                    path.move(to: first)
                    for p in points.dropFirst() { path.addLine(to: p) }
                }
                .stroke(Theme.travel, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))

                ForEach(Array(points.enumerated()), id: \.offset) { i, p in
                    Circle()
                        .fill(Theme.surface)
                        .overlay(Circle().stroke(Theme.travel, lineWidth: 2))
                        .frame(width: 12, height: 12)
                        .position(p)
                    Text(stops[i].city)
                        .font(.caption2)
                        .foregroundStyle(Theme.inkSoft)
                        .position(x: p.x, y: min(p.y + 16, geo.size.height - 8))
                }
            }
        }
    }
}
