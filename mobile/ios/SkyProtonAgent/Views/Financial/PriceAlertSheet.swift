import SwiftUI

/// Price-alert management for one symbol — list existing alerts, add/edit one, toggle
/// enabled, delete. Mirrors the web app's `AlertModal` (list + inline form, same fields).
struct PriceAlertSheet: View {
    let symbol: String
    let assetType: String  // "CRYPTO" | "STOCK"
    @Environment(\.dismiss) private var dismiss

    @State private var alerts: [PriceAlert] = []
    @State private var isLoading = false
    @State private var loadError: String?

    @State private var showForm = false
    @State private var editingAlert: PriceAlert?
    @State private var direction = ">="
    @State private var threshold = ""
    @State private var freqUnit = "HOUR"
    @State private var freqNumber = "1"
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var pendingDelete: PriceAlert?

    private var needsFreqNumber: Bool { freqUnit == "HOUR" || freqUnit == "DAY" }
    private var isValid: Bool {
        Double(threshold) != nil && (!needsFreqNumber || (Int(freqNumber) ?? 0) > 0)
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && alerts.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    Form {
                        if let loadError {
                            Section { Text(loadError).font(.caption).foregroundStyle(.red) }
                        }
                        if alerts.isEmpty && !showForm {
                            Section {
                                Text("No alerts set for \(symbol) yet.")
                                    .foregroundStyle(Theme.inkFaint)
                                    .frame(maxWidth: .infinity)
                            }
                        } else if !alerts.isEmpty {
                            Section("Existing alerts") {
                                ForEach(alerts) { alert in
                                    alertRow(alert)
                                }
                            }
                        }

                        if showForm {
                            Section(editingAlert == nil ? "New alert" : "Edit alert") {
                                Picker("Direction", selection: $direction) {
                                    ForEach(alertDirections, id: \.self) { d in
                                        Text("\(d)  \(alertDirectionLabels[d] ?? "")").tag(d)
                                    }
                                }
                                TextField("Threshold", text: $threshold)
                                    .keyboardType(.decimalPad)
                                Picker("Frequency", selection: $freqUnit) {
                                    ForEach(alertFrequencyUnits, id: \.self) { u in
                                        Text(alertFrequencyUnitLabels[u] ?? u).tag(u)
                                    }
                                }
                                if needsFreqNumber {
                                    Stepper("Every \(freqNumber) \(freqUnit == "HOUR" ? "hour(s)" : "day(s)")",
                                            value: Binding(
                                                get: { Int(freqNumber) ?? 1 },
                                                set: { freqNumber = String(max(1, $0)) }
                                            ), in: 1...999)
                                }
                                if let saveError {
                                    Text(saveError).font(.caption).foregroundStyle(.red)
                                }
                                HStack {
                                    Button("Cancel") { closeForm() }
                                    Spacer()
                                    if isSaving {
                                        ProgressView()
                                    } else {
                                        Button(editingAlert == nil ? "Save Alert" : "Update Alert") {
                                            Task { await save() }
                                        }
                                        .disabled(!isValid)
                                    }
                                }
                            }
                        } else {
                            Section {
                                Button {
                                    startCreate()
                                } label: {
                                    Label("Add Alert", systemImage: "plus")
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Alerts — \(symbol)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
            .confirmationDialog(
                "Delete this alert?",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) {
                    if let a = pendingDelete { Task { await delete(a) } }
                    pendingDelete = nil
                }
                Button("Cancel", role: .cancel) { pendingDelete = nil }
            }
        }
        .task { await load() }
    }

    private func alertRow(_ alert: PriceAlert) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("\(alert.symbol) \(alert.direction) \(formatNum(alert.threshold))")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(alert.enabled ? Theme.ink : Theme.inkFaint)
                Text(formatAlertFrequency(alert.frequency))
                    .font(.caption)
                    .foregroundStyle(Theme.inkFaint)
            }
            Spacer()
            Button {
                Task { await toggleEnabled(alert) }
            } label: {
                Image(systemName: alert.enabled ? "bell.fill" : "bell.slash")
                    .foregroundStyle(alert.enabled ? Theme.positive : Theme.inkFaint)
            }
            .buttonStyle(.plain)
            Button { startEdit(alert) } label: {
                Image(systemName: "pencil").foregroundStyle(Theme.inkSoft)
            }
            .buttonStyle(.plain)
            .padding(.leading, 6)
            Button { pendingDelete = alert } label: {
                Image(systemName: "trash").foregroundStyle(.red)
            }
            .buttonStyle(.plain)
            .padding(.leading, 6)
        }
    }

    private func startCreate() {
        editingAlert = nil
        direction = ">="
        threshold = ""
        freqUnit = "HOUR"
        freqNumber = "1"
        saveError = nil
        showForm = true
    }

    private func startEdit(_ alert: PriceAlert) {
        editingAlert = alert
        direction = alert.direction
        threshold = formatNum(alert.threshold)
        freqUnit = alert.frequency?.unit ?? "HOUR"
        freqNumber = String(alert.frequency?.number ?? 1)
        saveError = nil
        showForm = true
    }

    private func closeForm() {
        showForm = false
        editingAlert = nil
        saveError = nil
    }

    private func load() async {
        isLoading = true; loadError = nil
        do {
            alerts = try await AgentService.shared.listPriceAlerts().filter { $0.symbol == symbol }
        } catch {
            if (error as? APIError)?.isCancellation != true {
                loadError = error.localizedDescription
            }
        }
        isLoading = false
    }

    private func save() async {
        guard let thresholdValue = Double(threshold) else { return }
        isSaving = true; saveError = nil
        var frequency: [String: Any] = ["unit": freqUnit]
        if needsFreqNumber { frequency["number"] = Int(freqNumber) ?? 1 }

        do {
            if let editingAlert {
                try await AgentService.shared.updatePriceAlert(id: editingAlert.id, [
                    "threshold": thresholdValue, "direction": direction, "frequency": frequency,
                ])
            } else {
                try await AgentService.shared.createPriceAlert([
                    "symbol": symbol, "assetType": assetType,
                    "threshold": thresholdValue, "direction": direction, "frequency": frequency,
                ])
            }
            closeForm()
            await load()
        } catch {
            saveError = error.localizedDescription
        }
        isSaving = false
    }

    private func toggleEnabled(_ alert: PriceAlert) async {
        do {
            try await AgentService.shared.updatePriceAlert(id: alert.id, ["enabled": !alert.enabled])
            await load()
        } catch {
            loadError = error.localizedDescription
        }
    }

    private func delete(_ alert: PriceAlert) async {
        do {
            try await AgentService.shared.deletePriceAlert(id: alert.id)
            await load()
        } catch {
            loadError = error.localizedDescription
        }
    }
}
