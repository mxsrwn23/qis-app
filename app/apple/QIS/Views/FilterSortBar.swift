import SwiftUI

struct FilterSortBar: View {
    @Binding var filter: GradesFilter
    @Binding var sort: GradesSort

    var body: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(GradesFilter.allCases) { option in
                        ChoiceChip(label: option.label, isSelected: filter == option) {
                            filter = option
                        }
                    }
                }
            }
            SortMenu(sort: $sort)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

private struct SortMenu: View {
    @Binding var sort: GradesSort

    var body: some View {
        Menu {
            ForEach(GradesSort.allCases) { option in
                Button {
                    sort = option
                } label: {
                    if sort == option {
                        Label(option.label, systemImage: "checkmark")
                    } else {
                        Text(option.label)
                    }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(sort == .none ? "Sortieren" : sort.label)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption2)
            }
            .font(.subheadline.weight(sort == .none ? .regular : .semibold))
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .foregroundStyle(sort == .none ? Color.primary : Color.accentColor)
            .background(
                Capsule().strokeBorder(sort == .none ? Color.primary.opacity(0.2) : Color.accentColor, lineWidth: 1)
            )
        }
        .accessibilityLabel("Sortierung")
        .fixedSize()
    }
}

private struct ChoiceChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .background(
                    Capsule().fill(isSelected ? Color.accentColor : Color.primary.opacity(0.08))
                )
        }
        .buttonStyle(.plain)
    }
}
