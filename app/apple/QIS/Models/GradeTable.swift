import Foundation

struct GradeTable: Equatable {
    let header: [String]
    let rows: [[String]]
    /// "(angestrebter) Abschluss" / "Fach" aus der Stammdaten-Tabelle oberhalb der Notentabelle,
    /// z. B. "Bachelor of Science" / "Informatik (dual) (PO-Version 2024)". Wird verwendet, um die
    /// Studiengang-Kopfzeile automatisch zu befüllen und einen Bachelor-/Master-ECTS-Standardwert
    /// zu wählen, ohne eine weitere Anfrage.
    var abschluss: String?
    var fach: String?
}

extension GradeTable {
    private static let degreeAbbreviations: [String: String] = [
        "Bachelor of Science": "B.Sc.",
        "Master of Science": "M.Sc.",
        "Bachelor of Arts": "B.A.",
        "Master of Arts": "M.A.",
        "Bachelor of Engineering": "B.Eng.",
        "Master of Engineering": "M.Eng."
    ]

    var detectedDegreeAbbreviation: String? {
        guard let abschluss else { return nil }
        let normalized = abschluss.trimmingCharacters(in: .whitespaces)
        if normalized.isEmpty { return nil }
        return Self.degreeAbbreviations[normalized] ?? normalized
    }

    var detectedDegreeType: String {
        guard let abschluss else { return "" }
        return abschluss.localizedCaseInsensitiveContains("master") ? "master" : "bachelor"
    }

    var detectedFachName: String? {
        guard let fach else { return nil }
        let stripped = fach.replacingOccurrences(
            of: #"\s*\(PO-Version\s*\d{4}\)"#, with: "", options: .regularExpression
        )
        let trimmed = stripped.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? nil : trimmed
    }

    var detectedStudiengangLabel: String? {
        let parts = [detectedDegreeAbbreviation, detectedFachName].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }
}
