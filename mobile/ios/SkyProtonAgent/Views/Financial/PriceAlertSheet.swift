import SwiftUI

/// Price-alert management for one symbol — list existing alerts, add/edit one, toggle
/// enabled, delete. Mirrors the web app's `AlertModal` (list + inline form, same fields).
struct PriceAlertSheet: View {
    let symbol: String
    let assetType: String  // "CRYPTO" | "STOCK"

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
        FormSheet(
            "Price alerts",
            subtitle: "Get notified when \(symbol) crosses a price",
            primaryLabel: showForm ? (editingAlert == nil ? "Save alert" : "Update alert") : "New alert",
            isPrimaryEnabled: !showForm || isValid,
            isSaving: isSaving,
            errorMessage: loadError,
            onPrimary: {
                if showForm { Task { await save() } } else { startCreate() }
            }
        ) {
            if isLoading && alerts.isEmpty {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 40)
            } else if alerts.isEmpty && !showForm {
                emptyState
            } else if !alerts.isEmpty {
                FormSection("Active on \(symbol)") {
                    ForEach(Array(alerts.enumerated()), id: \.element.id) { idx, alert in
                        alertRow(alert)
                        if idx < alerts.count - 1 { FormDivider() }
                    }
                }
            }

            if showForm {
                FormSection(
                    editingAlert == nil ? "New alert" : "Edit alert",
                    trailing: AnyView(
                        Button("Cancel") { closeForm() }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.inkSoft)
                    )
                ) {
                    FormChoiceRow("When price is") {
                        VStack(alignment: .leading, spacing: 8) {
                            FormSegmented(options: alertDirections, selection: $direction) { directionSymbol($0) }
                            Text(alertDirectionLabels[direction] ?? "")
                                .font(.caption)
                                .foregroundStyle(Theme.inkFaint)
                        }
                    }
                    FormDivider()
                    FormAmountHero(label: "Threshold", amount: $threshold, placeholder: "0.00")
                    FormDivider()
                    FormChoiceRow("Repeat") {
                        FormSegmented(options: alertFrequencyUnits, selection: $freqUnit) { frequencyShortLabel($0) }
                    }
                    if needsFreqNumber {
                        FormDivider()
                        FormRow(freqUnit == "HOUR" ? "Every N hours" : "Every N days") {
                            FormStepper(
                                value: Binding(
                                    get: { Int(freqNumber) ?? 1 },
                                    set: { freqNumber = String(max(1, $0)) }
                                ),
                                range: 1...999,
                                format: { "\($0)\(freqUnit == "HOUR" ? "h" : "d")" }
                            )
                        }
                    }
                    if let saveError {
                        FormDivider()
                        Text(saveError)
                            .font(.system(size: 14))
                            .foregroundStyle(Theme.negative)
                            .formRowPadding()
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .animation(.easeOut(duration: 0.2), value: showForm)
        .animation(.easeOut(duration: 0.2), value: needsFreqNumber)
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
        .task { await load() }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "bell.badge")
                .font(.system(size: 26))
                .foregroundStyle(Theme.inkSoft)
                .frame(width: 60, height: 60)
                .background(Theme.chipFill)
                .clipShape(Circle())
            Text("No alerts yet").font(Theme.serif(20)).foregroundStyle(Theme.ink)
            Text("Create one to be notified when \(symbol) moves past a price you choose.")
                .font(.system(size: 14))
                .foregroundStyle(Theme.inkFaint)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 28)
        .padding(.horizontal, 20)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.hairline, lineWidth: 1))
    }

    private func alertRow(_ alert: PriceAlert) -> some View {
        HStack(spacing: 12) {
            Image(systemName: alert.enabled ? "bell.fill" : "bell.slash")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(alert.enabled ? Theme.positive : Theme.inkFaint)
                .frame(width: 34, height: 34)
                .background(alert.enabled ? Theme.positiveSoft : Theme.chipFill)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text("\(directionSymbol(alert.direction)) \(formatNum(alert.threshold))")
                    .font(Theme.serif(19))
                    .foregroundStyle(alert.enabled ? Theme.ink : Theme.inkFaint)
                Text(formatAlertFrequency(alert.frequency))
                    .font(.caption)
                    .foregroundStyle(Theme.inkFaint)
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { alert.enabled },
                set: { _ in Task { await toggleEnabled(alert) } }
            ))
            .labelsHidden()
            .tint(Theme.positive)
            Menu {
                Button { startEdit(alert) } label: { Label("Edit", systemImage: "pencil") }
                Button(role: .destructive) { pendingDelete = alert } label: { Label("Delete", systemImage: "trash") }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.inkSoft)
                    .frame(width: 30, height: 30)
                    .contentShape(Rectangle())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture { startEdit(alert) }
    }

    private func directionSymbol(_ d: String) -> String {
        switch d {
        case ">=": return "≥"
        case "<=": return "≤"
        default:   return d
        }
    }

    private func frequencyShortLabel(_ unit: String) -> String {
        switch unit {
        case "HOUR":  return "Hourly"
        case "DAY":   return "Daily"
        case "ONCE":  return "Once"
        case "NEVER": return "Never"
        default:      return unit
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
