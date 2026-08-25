import SwiftUI

struct TravelListView: View {
    @State private var trips: [TravelRecord] = []
    @State private var isLoading = false
    @State private var loadError: String?

    private let service = AgentService.shared

    private var countryCount: Int {
        Set(trips.flatMap { $0.stops.map(\.country) }).count
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && trips.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Theme.background)
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            if let err = loadError {
                                HStack(spacing: 8) {
                                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                                    Text(err).font(.caption).foregroundStyle(Theme.inkSoft)
                                }
                                .padding(12)
                                .background(Theme.surface)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }

                            if !trips.isEmpty { statStrip }

                            if trips.isEmpty {
                                Text(loadError == nil ? "No trips yet" : "No trips")
                                    .foregroundStyle(Theme.inkFaint)
                                    .frame(maxWidth: .infinity)
                                    .padding(.top, 60)
                            } else {
                                ForEach(trips) { trip in
                                    NavigationLink {
                                        TravelDetailView(trip: trip)
                                    } label: {
                                        TripCard(trip: trip)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                    }
                    .background(Theme.background)
                    .tabBarSafeArea()
                    .refreshable { await load() }
                }
            }
            .navigationTitle("Travel")
            .task { await load() }
        }
        .tint(Theme.travel)
    }

    private var statStrip: some View {
        ThemeCard(padding: 16) {
            HStack {
                statColumn(value: "\(trips.count)", label: "Trips")
                Divider().overlay(Theme.hairline).frame(height: 28)
                statColumn(value: "\(countryCount)", label: "Countries")
            }
        }
    }

    private func statColumn(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(Theme.serif(24, weight: .semibold)).foregroundStyle(Theme.ink)
            Text(label).font(.caption).foregroundStyle(Theme.inkFaint)
        }
        .frame(maxWidth: .infinity)
    }

    private func load() async {
        isLoading = trips.isEmpty
        loadError = nil
        do {
            trips = try await service.listTravelRecords()
        } catch {
            loadError = error.localizedDescription
        }
        isLoading = false
    }
}

private struct TripCard: View {
    let trip: TravelRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topTrailing) {
                LinearGradient(colors: [Theme.travel, Theme.travel.opacity(0.65)],
                                startPoint: .topLeading, endPoint: .bottomTrailing)
                    .frame(height: 84)
                RouteSketch(stopCount: max(trip.stops.count, 1))
                    .frame(height: 84)
                    .clipped()
                if trip.isUpcoming {
                    Text("Upcoming")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(Theme.surface.opacity(0.92))
                        .foregroundStyle(Theme.travel)
                        .clipShape(Capsule())
                        .padding(10)
                }
            }
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22))

            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(trip.title).font(Theme.serif(19, weight: .semibold)).foregroundStyle(Theme.ink)
                    Text(trip.dateRangeLabel).font(.caption).foregroundStyle(Theme.inkFaint)
                }
                if !trip.stops.isEmpty {
                    Text(trip.routeLabel)
                        .font(.caption)
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(2)
                }
                Divider().overlay(Theme.hairline)
                Text("\(trip.dayCount) days · \(trip.stops.count) stops")
                    .font(.caption)
                    .foregroundStyle(Theme.inkSoft)
            }
            .padding(16)
        }
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 14, y: 6)
    }
}

private struct RouteSketch: View {
    let stopCount: Int

    var body: some View {
        GeometryReader { geo in
            let n = max(stopCount, 2)
            let points: [CGPoint] = (0..<n).map { i in
                let x = geo.size.width * CGFloat(i) / CGFloat(n - 1)
                let y = geo.size.height * (i % 2 == 0 ? 0.7 : 0.35)
                return CGPoint(x: x, y: y)
            }
            Path { path in
                guard let first = points.first else { return }
                path.move(to: first)
                for p in points.dropFirst() { path.addLine(to: p) }
            }
            .stroke(Color.white.opacity(0.6), style: StrokeStyle(lineWidth: 1.4, dash: [1, 6]))

            ForEach(Array(points.enumerated()), id: \.offset) { _, p in
                Circle().fill(Color.white).frame(width: 6, height: 6).position(p)
            }
        }
    }
}
