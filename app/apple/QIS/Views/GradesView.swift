import SwiftUI

struct GradesView: View {
    let gradeTables: [GradeTable]
    let newModuleKeys: Set<String>
    let onRefresh: () async -> Void
    let onLogout: () -> Void

    @State private var showingSettings = false
    @State private var showingDisplayOptions = false
    @State private var searchText = ""
    @State private var gradeSettings: GradeSettings
    @State private var archivedModuleKeys = ModuleArchiveStore.load()
    @State private var excludedFromAverageModuleKeys = AverageInclusionStore.loadExcludedModuleKeys()
    @State private var isArchiveExpanded = false
    @AppStorage("qis.filter") private var filter: GradesFilter = .all
    @AppStorage("qis.sort") private var sort: GradesSort = .none

    init(gradeTables: [GradeTable], newModuleKeys: Set<String>, onRefresh: @escaping () async -> Void, onLogout: @escaping () -> Void) {
        self.gradeTables = gradeTables
        self.newModuleKeys = newModuleKeys
        self.onRefresh = onRefresh
        self.onLogout = onLogout
        _gradeSettings = State(initialValue: GradeSettings.loadApplyingAutoDetection(from: gradeTables))
    }

    /// Die Tabelle der bestätigten (oder einzig möglichen) Abschluss-/Fach-Wahl. RootView stellt
    /// sicher, dass diese View erst erreicht wird, wenn `gradeSettings.needsDegreeSetup` `false`
    /// ist; der Fallback auf die erste Tabelle greift daher nur defensiv.
    private var gradeTable: GradeTable {
        gradeSettings.activeTable(in: gradeTables) ?? gradeTables[0]
    }

    private var allCards: [ModuleCardData] {
        GradeCardBuilder.buildCards(table: gradeTable, settings: gradeSettings, newModuleKeys: newModuleKeys)
    }

    private var visibleCards: [ModuleCardData] {
        let activeCards = allCards.filter { !archivedModuleKeys.contains($0.id) }
        let base = GradeCardBuilder.sorted(GradeCardBuilder.filtered(activeCards, by: filter), by: sort)
        return cardsMatchingSearch(base)
    }

    private var archivedCards: [ModuleCardData] {
        cardsMatchingSearch(allCards.filter { archivedModuleKeys.contains($0.id) })
    }

    private var average: Double? {
        GradeAnalysis.average(
            table: gradeTable,
            mode: gradeSettings.averageMode,
            excludedModuleKeys: excludedFromAverageModuleKeys
        )
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
        switch sort {
        case .none:
            return groupedSections(for: visibleCards) { $0.sectionTitle }
        case .semester:
            return groupedSections(for: visibleCards) { semesterTitle(for: $0) }
        case .grade:
            return [DisplaySection(title: nil, cards: visibleCards)]
        }
    }

    private func groupedSections(
        for cards: [ModuleCardData],
        title: (ModuleCardData) -> String?
    ) -> [DisplaySection] {
        var sections: [DisplaySection] = []
        for card in cards {
            let sectionTitle = title(card)
            if sections.isEmpty || sections[sections.count - 1].title != sectionTitle {
                sections.append(DisplaySection(title: sectionTitle, cards: [card]))
            } else {
                sections[sections.count - 1] = DisplaySection(
                    title: sectionTitle,
                    cards: sections[sections.count - 1].cards + [card]
                )
            }
        }
        return sections
    }

    private func semesterTitle(for card: ModuleCardData) -> String {
        let semester = card.attempts.last?.semester.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return semester.isEmpty ? "Ohne Semester" : semester
    }

    var body: some View {
        VStack(spacing: 0) {
            KPIHeaderView(overlineText: overlineText, average: average, totalEcts: totalEcts, targetEcts: gradeSettings.effectiveTargetEcts, lastUpdated: GradeCache.lastFetchDate())
            FilterSortBar(filter: $filter, sort: $sort)
            Divider()

            List {
                ForEach(displaySections, id: \.title) { section in
                    Section {
                        ForEach(section.cards) { card in
                            moduleCard(card)
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button {
                                        archive(card)
                                    } label: {
                                        Label("Archivieren", systemImage: "archivebox")
                                    }
                                    .tint(.orange)
                                }
                        }
                    } header: {
                        if let title = section.title {
                            if sort == .semester {
                                SemesterDivider(title: title)
                            } else {
                                Text(title)
                            }
                        }
                    }
                }

                if !archivedCards.isEmpty {
                    Section {
                        ArchiveToggleRow(
                            count: archivedCards.count,
                            isExpanded: isArchiveExpanded,
                            toggleArchive: { isArchiveExpanded.toggle() }
                        )
                        .listRowSeparator(.hidden)

                        if isArchiveExpanded {
                            ForEach(archivedCards) { card in
                                moduleCard(card)
                                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                        Button {
                                            restore(card)
                                        } label: {
                                            Label("Einblenden", systemImage: "arrow.uturn.backward")
                                        }
                                        .tint(.green)
                                    }
                            }
                        }
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .overlay {
                if visibleCards.isEmpty && archivedCards.isEmpty {
                    if searchText.trimmingCharacters(in: .whitespaces).isEmpty {
                        ContentUnavailableView("Keine Module gefunden", systemImage: "tray")
                    } else {
                        ContentUnavailableView.search(text: searchText)
                    }
                }
            }
            .refreshable { await onRefresh() }
        }
        .searchable(text: $searchText, prompt: "Modul suchen")
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

    @ViewBuilder
    private func moduleCard(_ card: ModuleCardData) -> some View {
        ModuleCardView(
            card: card,
            visibleFields: gradeSettings.visibleAttemptFields,
            customColors: gradeSettings.customColors,
            isIncludedInAverage: averageInclusionBinding(for: card)
        )
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func cardsMatchingSearch(_ cards: [ModuleCardData]) -> [ModuleCardData] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return cards }
        return cards.filter { $0.moduleName.localizedCaseInsensitiveContains(query) }
    }

    private func averageInclusionBinding(for card: ModuleCardData) -> Binding<Bool> {
        Binding(
            get: { !excludedFromAverageModuleKeys.contains(card.id) },
            set: { isIncluded in
                if isIncluded {
                    excludedFromAverageModuleKeys.remove(card.id)
                } else {
                    excludedFromAverageModuleKeys.insert(card.id)
                }
                AverageInclusionStore.saveExcludedModuleKeys(excludedFromAverageModuleKeys)
            }
        )
    }

    private func archive(_ card: ModuleCardData) {
        archivedModuleKeys.insert(card.id)
        ModuleArchiveStore.save(archivedModuleKeys)
    }

    private func restore(_ card: ModuleCardData) {
        archivedModuleKeys.remove(card.id)
        ModuleArchiveStore.save(archivedModuleKeys)
    }
}

private struct SemesterDivider: View {
    let title: String

    var body: some View {
        HStack(spacing: 10) {
            Divider()
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .textCase(nil)
            Divider()
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

private struct ArchiveToggleRow: View {
    let count: Int
    let isExpanded: Bool
    let toggleArchive: () -> Void

    var body: some View {
        Button(action: toggleArchive) {
            HStack(spacing: 12) {
                Label("Archiviert", systemImage: "archivebox")
                    .font(.headline)
                Spacer()
                Text("\(count)")
                    .foregroundStyle(.secondary)
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Archivierte Module")
        .accessibilityValue("\(count) Module")
    }
}
