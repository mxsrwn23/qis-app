import Foundation

/// Modul-Gruppierung und gewichtete Durchschnittsberechnung, portiert aus der qis-extension
/// (groupRowsByModule / calcAvgGrade in entrypoints/qis-content.content/index.js).
enum GradeAnalysis {
    struct ModuleGroup {
        let headerRowIndex: Int
        var detailRowIndices: [Int] = []
    }

    /// Gruppiert Zeilen unter die nächstgelegene vorangehende "Modul: …"-Kopfzeile; eine
    /// Abschnitts-Trennzeile (Kernmodule/Pflichtmodule/Wahlpflichtmodule) beendet die aktuelle
    /// Gruppe.
    static func moduleGroups(rows: [[String]], prüfungstextIndex: Int?) -> [ModuleGroup] {
        var groups: [ModuleGroup] = []
        var currentGroupIndex: Int?
        for (index, row) in rows.enumerated() {
            if GradeStyling.isModuleRow(row, prüfungstextIndex: prüfungstextIndex) {
                groups.append(ModuleGroup(headerRowIndex: index))
                currentGroupIndex = groups.count - 1
            } else if GradeStyling.isSectionRow(row, prüfungstextIndex: prüfungstextIndex) {
                currentGroupIndex = nil
            } else if let groupIndex = currentGroupIndex {
                groups[groupIndex].detailRowIndices.append(index)
            }
        }
        return groups
    }

    private struct Attempt {
        let grade: Double
        let ects: Double
        let versuch: Int
    }

    private static func parseGermanDecimal(_ value: String) -> Double? {
        Double(value.replacingOccurrences(of: ",", with: "."))
    }

    private static func attempts(
        in group: ModuleGroup, rows: [[String]], noteIndex: Int, ectsIndex: Int, versuchIndex: Int?
    ) -> [Attempt] {
        group.detailRowIndices.compactMap { index in
            let row = rows[index]
            guard noteIndex < row.count, ectsIndex < row.count,
                  let grade = parseGermanDecimal(row[noteIndex]), grade > 0, grade < 5,
                  let ects = parseGermanDecimal(row[ectsIndex]), ects > 0 else {
                return nil
            }
            let versuch = versuchIndex.flatMap { $0 < row.count ? Int(row[$0].trimmingCharacters(in: .whitespaces)) : nil } ?? 0
            return Attempt(grade: grade, ects: ects, versuch: versuch)
        }
    }

    /// ECTS-gewichteter Durchschnitt über alle Module: "all" summiert jeden gültigen Versuch,
    /// "last" wählt pro Modul den Versuch mit der höchsten Versuchsnummer, "best" wählt die beste
    /// (niedrigste) Note.
    static func average(table: GradeTable, mode: GradeSettings.AverageMode) -> Double? {
        guard let noteIndex = GradeStyling.columnIndex(in: table.header, containing: "note"),
              let ectsIndex = GradeStyling.columnIndex(in: table.header, containing: "ects") else {
            return nil
        }
        let prüfungstextIndex = GradeStyling.columnIndex(in: table.header, containing: "prüfungstext")
        let versuchIndex = GradeStyling.columnIndex(in: table.header, containing: "versuch")
        let groups = moduleGroups(rows: table.rows, prüfungstextIndex: prüfungstextIndex)

        var weightedSum = 0.0
        var totalEcts = 0.0

        for group in groups {
            let groupAttempts = attempts(in: group, rows: table.rows, noteIndex: noteIndex, ectsIndex: ectsIndex, versuchIndex: versuchIndex)
            guard !groupAttempts.isEmpty else { continue }

            let selected: [Attempt]
            switch mode {
            case .all:
                selected = groupAttempts
            case .last:
                selected = [groupAttempts.max { $0.versuch < $1.versuch } ?? groupAttempts[0]]
            case .best:
                selected = [groupAttempts.min { $0.grade < $1.grade } ?? groupAttempts[0]]
            }
            for attempt in selected {
                weightedSum += attempt.grade * attempt.ects
                totalEcts += attempt.ects
            }
        }

        guard totalEcts > 0 else { return nil }
        return weightedSum / totalEcts
    }
}
