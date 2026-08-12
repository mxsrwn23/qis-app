import SwiftUI

struct ModuleCardView: View {
    let card: ModuleCardData
    let visibleFields: Set<String>
    let customColors: [String: String]

    @State private var isExpanded: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(card: ModuleCardData, visibleFields: Set<String>, customColors: [String: String]) {
        self.card = card
        self.visibleFields = visibleFields
        self.customColors = customColors
        _isExpanded = State(initialValue: card.isNew)
    }

    var body: some View {
        VStack(spacing: 0) {
            Button {
                if reduceMotion {
                    isExpanded.toggle()
                } else {
                    withAnimation(.snappy(duration: 0.25)) { isExpanded.toggle() }
                }
            } label: {
                header
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isExpanded ? .isSelected : [])
            .accessibilityHint(isExpanded ? "Zugeklappt zeigen" : "Versuche anzeigen")

            if isExpanded && !card.attempts.isEmpty {
                Divider().padding(.horizontal, 16)
                VStack(spacing: 0) {
                    ForEach(Array(card.attempts.enumerated()), id: \.element.id) { offset, attempt in
                        AttemptRowView(attempt: attempt, visibleFields: visibleFields)
                        if offset < card.attempts.count - 1 {
                            Divider().padding(.leading, 16)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .background(.background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.08))
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text(card.moduleName)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                HStack(spacing: 6) {
                    if card.isNew {
                        NewBadge()
                    }
                    if let category = card.statusCategory {
                        StatusChip(label: card.statusLabel, category: category, customColors: customColors)
                    }
                    if !card.ects.isEmpty {
                        ECTSChip(value: card.ects)
                    }
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Text(card.grade.isEmpty ? "–" : card.grade)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(16)
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }
}

private struct StatusChip: View {
    let label: String
    let category: GradeStatusCategory
    let customColors: [String: String]

    var body: some View {
        Text(label)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(GradeStyling.accent(for: category.colorKey, customColors: customColors))
            .background(GradeStyling.backgroundTint(for: category.colorKey, customColors: customColors), in: Capsule())
    }
}

private struct NewBadge: View {
    var body: some View {
        Text("NEU")
            .font(.caption2.weight(.bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(.white)
            .background(Color.accentColor, in: Capsule())
    }
}

private struct ECTSChip: View {
    let value: String

    var body: some View {
        Text("\(value) ECTS")
            .font(.caption2.weight(.medium))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(.secondary)
            .background(Color.primary.opacity(0.06), in: Capsule())
    }
}

private struct AttemptRowView: View {
    let attempt: AttemptRow
    let visibleFields: Set<String>

    var body: some View {
        HStack(spacing: 12) {
            if visibleFields.contains("semester"), !attempt.semester.isEmpty {
                Text(attempt.semester)
                    .frame(minWidth: 72, alignment: .leading)
            }
            if visibleFields.contains("note") {
                Text(attempt.note.isEmpty ? "–" : attempt.note)
                    .strikethrough(attempt.isFailed)
                    .frame(minWidth: 44, alignment: .leading)
            }
            if visibleFields.contains("versuch"), !attempt.versuch.isEmpty {
                Text("Versuch \(attempt.versuch)")
            }
            Spacer(minLength: 4)
            if visibleFields.contains("datum"), !attempt.datum.isEmpty {
                Text(attempt.datum)
                    .foregroundStyle(.tertiary)
            }
        }
        .font(.subheadline)
        .foregroundStyle(attempt.isFailed ? .tertiary : .secondary)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .opacity(attempt.isFailed ? 0.6 : 1)
    }
}
