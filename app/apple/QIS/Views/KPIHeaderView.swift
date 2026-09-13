import SwiftUI

struct KPIHeaderView: View {
    let overlineText: String
    let average: Double?
    let totalEcts: Double
    let targetEcts: Int
    var lastUpdated: Date?

    private var ectsValueText: String {
        totalEcts.truncatingRemainder(dividingBy: 1) == 0
            ? String(format: "%.0f", totalEcts)
            : String(format: "%.1f", totalEcts)
    }

    private var progress: Double {
        guard targetEcts > 0 else { return 0 }
        return min(max(totalEcts / Double(targetEcts), 0), 1)
    }

    var body: some View {
        VStack(spacing: 8) {
            Text(overlineText)
                .font(.caption2.weight(.semibold))
                .textCase(.uppercase)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            HStack(spacing: 0) {
                KPIView(title: "Schnitt", value: average.map { String(format: "%.2f", $0) } ?? "–")
                Divider().frame(height: 36)
                VStack(spacing: 4) {
                    KPIView(title: "ECTS", value: ectsValueText)
                    ProgressView(value: progress)
                        .frame(height: 3)
                        .padding(.horizontal, 28)
                }
                .frame(maxWidth: .infinity)
            }

            if let lastUpdated {
                Text("Zuletzt aktualisiert \(lastUpdated.formatted(.relative(presentation: .named)))")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.regularMaterial)
    }
}

private struct KPIView: View {
    let title: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.title.weight(.bold))
                .foregroundStyle(.primary)
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
