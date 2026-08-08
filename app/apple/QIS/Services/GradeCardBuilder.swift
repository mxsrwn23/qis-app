import Foundation

/// Wandelt die flachen GradeTable-Zeilen in eine Karte pro Modul um (Detail-/Versuchszeilen
/// werden darunter gruppiert und mit dem umgebenden Kernmodule-/Pflichtmodule-/
/// Wahlpflichtmodule-Abschnitt versehen).
enum GradeCardBuilder {
    static func buildCards(table: GradeTable, settings: GradeSettings) -> [ModuleCardData] {
        let prüfungstextIndex = GradeStyling.columnIndex(in: table.header, containing: "prüfungstext")
        let statusIndex = GradeStyling.columnIndex(in: table.header, containing: "status")
        let noteIndex = GradeStyling.columnIndex(in: table.header, containing: "note")
        let ectsIndex = GradeStyling.columnIndex(in: table.header, containing: "ects")
        let semesterIndex = GradeStyling.columnIndex(in: table.header, containing: "semester")
        let versuchIndex = GradeStyling.columnIndex(in: table.header, containing: "versuch")
        let datumIndex = GradeStyling.columnIndex(in: table.header, containing: "datum")

        func value(_ index: Int?, in row: [String]) -> String {
            guard let index, index < row.count else { return "" }
            return row[index]
        }

        let rows = table.rows
        var cards: [ModuleCardData] = []
        var currentSectionTitle: String?
        var index = 0

        while index < rows.count {
            let row = rows[index]

            if GradeStyling.isSectionRow(row, prüfungstextIndex: prüfungstextIndex) {
                let text = value(prüfungstextIndex, in: row).trimmingCharacters(in: .whitespacesAndNewlines)
                currentSectionTitle = text.isEmpty ? nil : text
                index += 1
                continue
            }

            guard GradeStyling.isModuleRow(row, prüfungstextIndex: prüfungstextIndex) else {
                index += 1
                continue
            }

            var detailRows: [[String]] = []
            var lookahead = index + 1
            while lookahead < rows.count,
                  !GradeStyling.isModuleRow(rows[lookahead], prüfungstextIndex: prüfungstextIndex),
                  !GradeStyling.isSectionRow(rows[lookahead], prüfungstextIndex: prüfungstextIndex) {
                detailRows.append(rows[lookahead])
                lookahead += 1
            }
            index = lookahead

            var filteredDetails = detailRows
            if settings.hideStudienleistungen {
                filteredDetails = filteredDetails.filter {
                    !value(prüfungstextIndex, in: $0).lowercased().contains("studienleistung")
                }
            }

            let attempts = filteredDetails.map { detailRow in
                AttemptRow(
                    semester: value(semesterIndex, in: detailRow).trimmingCharacters(in: .whitespaces),
                    note: value(noteIndex, in: detailRow).trimmingCharacters(in: .whitespaces),
                    versuch: value(versuchIndex, in: detailRow).trimmingCharacters(in: .whitespaces),
                    datum: value(datumIndex, in: detailRow).trimmingCharacters(in: .whitespaces),
                    status: value(statusIndex, in: detailRow)
                )
            }

            let moduleName = value(prüfungstextIndex, in: row)
                .replacingOccurrences(of: "Modul:", with: "")
                .components(separatedBy: .whitespacesAndNewlines)
                .filter { !$0.isEmpty }
                .joined(separator: " ")

            cards.append(ModuleCardData(
                sectionTitle: currentSectionTitle,
                moduleName: moduleName,
                grade: value(noteIndex, in: row).trimmingCharacters(in: .whitespaces),
                status: value(statusIndex, in: row),
                ects: value(ectsIndex, in: row).trimmingCharacters(in: .whitespaces),
                attempts: attempts
            ))
        }

        return cards
    }

    static func totalEarnedECTS(cards: [ModuleCardData]) -> Double {
        cards.reduce(0) { partial, card in
            guard card.statusCategory == .be,
                  let value = Double(card.ects.replacingOccurrences(of: ",", with: ".")) else {
                return partial
            }
            return partial + value
        }
    }

    static func filtered(_ cards: [ModuleCardData], by filter: GradesFilter) -> [ModuleCardData] {
        switch filter {
        case .all: return cards
        case .passed: return cards.filter { $0.statusCategory == .be }
        case .open: return cards.filter { $0.statusCategory != .be }
        }
    }

    static func sorted(_ cards: [ModuleCardData], by sort: GradesSort) -> [ModuleCardData] {
        switch sort {
        case .none:
            return cards
        case .grade:
            return cards.sorted { lhs, rhs in
                let l = Double(lhs.grade.replacingOccurrences(of: ",", with: "."))
                let r = Double(rhs.grade.replacingOccurrences(of: ",", with: "."))
                switch (l, r) {
                case let (l?, r?): return l < r
                case (nil, _): return false
                case (_, nil): return true
                }
            }
        case .semester:
            return cards.sorted { lhs, rhs in
                semesterKey(lhs.attempts.last?.semester) > semesterKey(rhs.attempts.last?.semester)
            }
        }
    }

    /// Sortierbarer chronologischer Schlüssel für deutsche Semesterbezeichnungen ("WiSe 23/24",
    /// "SoSe 24"); fehlende Semester rutschen unabhängig von der Sortierrichtung ans Ende.
    private static func semesterKey(_ text: String?) -> Int {
        guard let text, !text.isEmpty else { return Int.min }
        let normalized = text.lowercased()
        let digits = text.filter(\.isNumber)
        if normalized.hasPrefix("wise"), let startYear = Int(digits.prefix(2)) {
            return startYear * 2
        } else if normalized.hasPrefix("sose"), let year = Int(digits.prefix(2)) {
            return (year - 1) * 2 + 1
        }
        return Int.min
    }
}
