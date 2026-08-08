import Foundation

/// Spiegelt die DEFAULT_SETTINGS der Extension (utils/settings.js), soweit sie noch für das
/// kartenbasierte mobile Layout gelten. hiddenColumns wurde zu visibleAttemptFields, da es keine
/// Tabelle mehr gibt.
struct GradeSettings: Equatable {
    enum AverageMode: String, CaseIterable, Hashable {
        case all, last, best

        var label: String {
            switch self {
            case .all: return "Alle bestandenen Versuche"
            case .last: return "Nur letzter Versuch"
            case .best: return "Nur beste Note"
            }
        }
    }

    static let attemptFields = ["semester", "note", "versuch", "datum"]
    static let attemptFieldLabels = ["semester": "Semester", "note": "Note", "versuch": "Versuch", "datum": "Datum"]

    var averageMode: AverageMode = .best
    var hideStudienleistungen = false
    var visibleAttemptFields: Set<String> = Set(attemptFields)
    var customColors: [String: String] = [:]
    var studiengang = ""
    var semester = 0
    /// 0 bedeutet "automatisch". Fällt auf `effectiveTargetEcts` basierend auf `degreeType`
    /// zurück, bis der Nutzer einen expliziten Wert einträgt.
    var targetEcts = 0
    /// "bachelor" / "master" / "" (unbekannt), wird zusammen mit `studiengang` gesetzt, wenn
    /// automatisch aus der Stammdaten-Tabelle erkannt. Steuert den Bachelor-/Master-Standardwert
    /// für `effectiveTargetEcts`.
    var degreeType = ""

    var effectiveTargetEcts: Int {
        if targetEcts > 0 { return targetEcts }
        return degreeType == "master" ? 120 : 180
    }

    private enum Keys {
        static let averageMode = "qis.averageMode"
        static let hideStudienleistungen = "qis.hideStudienleistungen"
        static let visibleAttemptFields = "qis.visibleAttemptFields"
        static let customColors = "qis.customColors"
        static let studiengang = "qis.studiengang"
        static let semester = "qis.semester"
        static let targetEcts = "qis.targetEcts"
        static let degreeType = "qis.degreeType"
    }

    static func load() -> GradeSettings {
        let defaults = UserDefaults.standard
        var settings = GradeSettings()
        if let raw = defaults.string(forKey: Keys.averageMode), let mode = AverageMode(rawValue: raw) {
            settings.averageMode = mode
        }
        settings.hideStudienleistungen = defaults.bool(forKey: Keys.hideStudienleistungen)
        if let fields = defaults.stringArray(forKey: Keys.visibleAttemptFields) {
            settings.visibleAttemptFields = Set(fields)
        }
        if let colors = defaults.dictionary(forKey: Keys.customColors) as? [String: String] {
            settings.customColors = colors
        }
        settings.studiengang = defaults.string(forKey: Keys.studiengang) ?? ""
        settings.semester = defaults.integer(forKey: Keys.semester)
        settings.targetEcts = defaults.integer(forKey: Keys.targetEcts)
        settings.degreeType = defaults.string(forKey: Keys.degreeType) ?? ""
        return settings
    }

    /// Lädt die gespeicherten Einstellungen und befüllt den Studiengang (sowie den Bachelor-/
    /// Master-Abschlusstyp) nur dann automatisch aus dem Stammdaten-Bereich der frisch geladenen
    /// Notentabelle, wenn noch keiner gesetzt ist. Bereits gesetzte Werte werden nie
    /// überschrieben, eine manuelle Änderung bleibt also erhalten.
    static func loadApplyingAutoDetection(from gradeTable: GradeTable) -> GradeSettings {
        var settings = load()
        if settings.studiengang.trimmingCharacters(in: .whitespaces).isEmpty,
           let label = gradeTable.detectedStudiengangLabel {
            settings.studiengang = label
            settings.degreeType = gradeTable.detectedDegreeType
            settings.save()
        }
        return settings
    }

    func save() {
        let defaults = UserDefaults.standard
        defaults.set(averageMode.rawValue, forKey: Keys.averageMode)
        defaults.set(hideStudienleistungen, forKey: Keys.hideStudienleistungen)
        defaults.set(Array(visibleAttemptFields), forKey: Keys.visibleAttemptFields)
        defaults.set(customColors, forKey: Keys.customColors)
        defaults.set(studiengang, forKey: Keys.studiengang)
        defaults.set(semester, forKey: Keys.semester)
        defaults.set(targetEcts, forKey: Keys.targetEcts)
        defaults.set(degreeType, forKey: Keys.degreeType)
    }
}
