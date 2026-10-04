import Foundation

/// Gemeinsame Hilfsfunktionen, um die rohen QIS-Stammdaten-Strings ("Abschluss", "Fach") in
/// kurze, menschenlesbare Labels zu übersetzen. Wird sowohl für die automatisch erkannte
/// Studiengangs-Kopfzeile als auch für die Auswahlmöglichkeiten im Profil verwendet, damit beide
/// Stellen exakt dieselbe Normalisierung anwenden.
enum QISLabels {
    private static let degreeAbbreviations: [String: String] = [
        "Bachelor of Science": "B.Sc.",
        "Master of Science": "M.Sc.",
        "Bachelor of Arts": "B.A.",
        "Master of Arts": "M.A.",
        "Bachelor of Engineering": "B.Eng.",
        "Master of Engineering": "M.Eng."
    ]

    static func degreeAbbreviation(for abschluss: String) -> String {
        let normalized = abschluss.trimmingCharacters(in: .whitespaces)
        return degreeAbbreviations[normalized] ?? normalized
    }

    /// Ausgeschriebener Abschluss-Name (z. B. "Bachelor of Science"), für Stellen mit genug Platz
    /// wie die Profil-Verwaltung -- im Gegensatz zur Kopfzeile, die aus Platzgründen die
    /// Abkürzung verwendet.
    static func degreeFullName(for abschluss: String) -> String {
        abschluss.trimmingCharacters(in: .whitespaces)
    }

    static func degreeType(for abschluss: String) -> String {
        abschluss.localizedCaseInsensitiveContains("master") ? "master" : "bachelor"
    }

    /// Entfernt den "(PO-Version JJJJ)"-Zusatz aus dem rohen Fach-Text, für die Anzeige.
    static func fachName(for fach: String) -> String {
        let stripped = fach.replacingOccurrences(
            of: #"\s*\(PO-Version\s*\d{4}\)"#, with: "", options: .regularExpression
        )
        return stripped.trimmingCharacters(in: .whitespaces)
    }
}
