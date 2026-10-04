import Foundation

struct GradeTable: Equatable, Codable {
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
    /// Das (Abschluss, Fach)-Paar dieser Tabelle, falls beide Stammdaten-Felder beim Scrapen
    /// gefunden wurden. Dient als Schlüssel, um die Tabelle einer `DegreeOption` zuzuordnen.
    var degreeOption: DegreeOption? {
        guard let abschluss, !abschluss.isEmpty, let fach, !fach.isEmpty else { return nil }
        return DegreeOption(abschluss: abschluss, fach: fach)
    }
}
