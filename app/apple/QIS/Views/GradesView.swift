import SwiftUI

struct GradesView: View {
    let gradeTable: GradeTable
    let newModuleKeys: Set<String>
    let onRefresh: () async -> Void
    let onLogout: () -> Void

    @State private var showingSettings = false
    @State private var showingDisplayOptions = false
    @State private var gradeSettings: GradeSettings
    @AppStorage("qis.filter") private var filter: GradesFilter = .all
    @AppStorage("qis.sort") private var sort: GradesSort = .none

    init(gradeTable: GradeTable, newModuleKeys: Set<String>, onRefresh: @escaping () async -> Void, onLogout: @escaping () -> Void) {
        self.gradeTable = gradeTable
        self.newModuleKeys = newModuleKeys
        self.onRefresh = onRefresh
        self.onLogout = onLogout
        _gradeSettings = State(initialValue: GradeSettings.loadApplyingAutoDetection(from: gradeTable))
    }

    private var allCards: [ModuleCardData] {
        GradeCardBuilder.buildCards(table: gradeTable, settings: gradeSettings, newModuleKeys: newModuleKeys)
    }

    private var visibleCards: [ModuleCardData] {
        GradeCardBuilder.sorted(GradeCardBuilder.filtered(allCards, by: filter), by: sort)
    }

    private var average: Double? {
        GradeAnalysis.average(table: gradeTable, mode: gradeSettings.averageMode)
    }

    private var totalEcts: Double {
        GradeCardBuilder.totalEarnedECTS(cards: allCards)
    }

    private var overlineText: String {
        var parts: [String] = []
        let studiengang = gradeSettings.studiengang.trimmingCharacters(in: .whitespaces)
        if !studiengang.isEmpty { parts.append(studiengang) }
        if gradeSettings.semester > 0 { parts.append("\(gradeSettings.semester). Semester") }
        return parts.isEmpty ? "Notenspiegel" : parts.joined(separator: " · ")
    }

    private struct DisplaySection {
        let title: String?
        let cards: [ModuleCardData]
    }

    private var displaySections: [DisplaySection] {
        guard sort == .none else {
            return [DisplaySection(title: nil, cards: visibleCards)]
        }
        var sections: [DisplaySection] = []
        for card in visibleCards {
            if sections.isEmpty || sections[sections.count - 1].title != card.sectionTitle {
                sections.append(DisplaySection(title: card.sectionTitle, cards: [card]))
            } else {
                sections[sections.count - 1] = DisplaySection(
                    title: sections[sections.count - 1].title,
                    cards: sections[sections.count - 1].cards + [card]
                )
            }
        }
        return sections
    }

    var body: some View {
        VStack(spacing: 0) {
            KPIHeaderView(overlineText: overlineText, average: average, totalEcts: totalEcts, targetEcts: gradeSettings.effectiveTargetEcts)
            FilterSortBar(filter: $filter, sort: $sort)
            Divider()

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(displaySections.enumerated()), id: \.offset) { _, section in
                        if let title = section.title {
                            Text(title)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 16)
                                .padding(.top, 4)
                        }
                        ForEach(section.cards) { card in
                            ModuleCardView(
                                card: card,
                                visibleFields: gradeSettings.visibleAttemptFields,
                                customColors: gradeSettings.customColors
                            )
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .padding(.vertical, 12)
            }
            .overlay {
                if visibleCards.isEmpty {
                    ContentUnavailableView("Keine Module gefunden", systemImage: "tray")
                }
            }
            .refreshable { await onRefresh() }
        }
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    showingDisplayOptions = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                }
                .accessibilityLabel("Ansicht")
                Button {
                    showingSettings = true
                } label: {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("Einstellungen")
            }
        }
        .sheet(isPresented: $showingDisplayOptions) {
            DisplayOptionsView(gradeSettings: $gradeSettings)
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(gradeSettings: $gradeSettings) {
                showingSettings = false
                onLogout()
            }
        }
        .onChange(of: gradeSettings) { _, newValue in
            newValue.save()
        }
    }
}
