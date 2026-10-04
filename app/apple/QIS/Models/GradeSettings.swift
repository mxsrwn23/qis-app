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
    var semester = 0
    /// 0 bedeutet "automatisch". Fällt auf `effectiveTargetEcts` basierend auf `degreeType`
    /// zurück, bis der Nutzer einen expliziten Wert einträgt.
    var targetEcts = 0

    /// Alle beim letzten Login tatsächlich im QIS-Studiengangs-Baum des Nutzers gefundenen
    /// (Abschluss, Fach)-Kombinationen. Niemals hartkodiert -- einzige Quelle für Picker-Optionen
    /// in der Profil-Verwaltung und beim Ersteinrichtungs-Dialog.
    var availableDegreeOptions: [DegreeOption] = []
    /// Roh-"Abschluss"-Wert der bestätigten Auswahl (z. B. "Bachelor of Science"). Leer, solange
    /// bei mehreren verfügbaren Abschluss-Typen noch keine Auswahl getroffen wurde.
    var selectedAbschluss = ""
    /// Roh-"Fach"-Wert (inkl. PO-Version) der bestätigten Auswahl. Leer, solange bei mehreren
    /// Fachrichtungen innerhalb des gewählten Abschlusses noch keine Auswahl getroffen wurde.
    var selectedFach = ""

    /// Anzeige-Label aus der bestätigten Auswahl, z. B. "B.Sc. Informatik". Bewusst nicht mehr frei
    /// eintippbar: der Nutzer darf nur sehen und wählen, was QIS tatsächlich für ihn meldet.
    var studiengang: String {
        let parts = [
            selectedAbschluss.isEmpty ? nil : QISLabels.degreeAbbreviation(for: selectedAbschluss),
            selectedFach.isEmpty ? nil : QISLabels.fachName(for: selectedFach)
        ].compactMap { $0 }
        return parts.joined(separator: " ")
    }

    /// "bachelor" / "master" / "" (unbekannt), aus `selectedAbschluss` abgeleitet. Steuert den
    /// Bachelor-/Master-Standardwert für `effectiveTargetEcts`.
    var degreeType: String {
        selectedAbschluss.isEmpty ? "" : QISLabels.degreeType(for: selectedAbschluss)
    }

    var effectiveTargetEcts: Int {
        if targetEcts > 0 { return targetEcts }
        return degreeType == "master" ? 120 : 180
    }

    /// Alle unterschiedlichen Abschluss-Typen, die `availableDegreeOptions` enthält, in der
    /// Reihenfolge ihres ersten Auftretens.
    var distinctAbschlussOptions: [String] {
        var seen: Set<String> = []
        var result: [String] = []
        for option in availableDegreeOptions where seen.insert(option.abschluss).inserted {
            result.append(option.abschluss)
        }
        return result
    }

    /// Alle Fachrichtungen innerhalb eines bestimmten Abschluss-Typs.
    func fachOptions(forAbschluss abschluss: String) -> [String] {
        guard !abschluss.isEmpty else { return [] }
        var seen: Set<String> = []
        var result: [String] = []
        for option in availableDegreeOptions where option.abschluss == abschluss && seen.insert(option.fach).inserted {
            result.append(option.fach)
        }
        return result
    }

    /// Ob der Nutzer aus mehreren Abschluss-Typen wählen muss, aber noch keine Wahl getroffen hat.
    var needsAbschlussSelection: Bool {
        distinctAbschlussOptions.count > 1 && selectedAbschluss.isEmpty
    }

    /// Ob der Nutzer (nach feststehendem Abschluss) aus mehreren Fachrichtungen wählen muss, aber
    /// noch keine Wahl getroffen hat.
    var needsFachSelection: Bool {
        guard !selectedAbschluss.isEmpty else { return false }
        return fachOptions(forAbschluss: selectedAbschluss).count > 1 && selectedFach.isEmpty
    }

    /// Ob vor der Notenanzeige noch ein Ersteinrichtungs-Dialog (Szenario 2 und/oder 3) nötig ist.
    var needsDegreeSetup: Bool { needsAbschlussSelection || needsFachSelection }

    /// Die Tabelle aus `tables`, die der bestätigten (oder einzig möglichen) Abschluss-/Fach-Wahl
    /// entspricht. Liefert `nil`, solange `needsDegreeSetup` noch zutrifft.
    func activeTable(in tables: [GradeTable]) -> GradeTable? {
        if tables.count == 1 { return tables.first }
        guard !selectedAbschluss.isEmpty, !selectedFach.isEmpty else { return nil }
        return tables.first { $0.abschluss == selectedAbschluss && $0.fach == selectedFach }
    }

    private enum Keys {
        static let averageMode = "qis.averageMode"
        static let hideStudienleistungen = "qis.hideStudienleistungen"
        static let visibleAttemptFields = "qis.visibleAttemptFields"
        static let customColors = "qis.customColors"
        static let semester = "qis.semester"
        static let targetEcts = "qis.targetEcts"
        static let availableDegreeOptions = "qis.availableDegreeOptions"
        static let selectedAbschluss = "qis.selectedAbschluss"
        static let selectedFach = "qis.selectedFach"
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
        settings.semester = defaults.integer(forKey: Keys.semester)
        settings.targetEcts = defaults.integer(forKey: Keys.targetEcts)
        if let data = defaults.data(forKey: Keys.availableDegreeOptions),
           let options = try? JSONDecoder().decode([DegreeOption].self, from: data) {
            settings.availableDegreeOptions = options
        }
        settings.selectedAbschluss = defaults.string(forKey: Keys.selectedAbschluss) ?? ""
        settings.selectedFach = defaults.string(forKey: Keys.selectedFach) ?? ""
        return settings
    }

    /// Lädt die gespeicherten Einstellungen, aktualisiert `availableDegreeOptions` auf den
    /// tatsächlich beim letzten Login gefundenen Stand und wendet so viel Auto-Erkennung an, wie
    /// sich eindeutig ableiten lässt:
    /// - genau ein Abschluss-Typ -> automatisch übernommen (Szenario 1 und 3)
    /// - genau eine Fachrichtung innerhalb des (automatischen oder gespeicherten) Abschlusses ->
    ///   automatisch übernommen (Szenario 1)
    /// - mehrere Möglichkeiten -> keine Auswahl erzwungen, `needsDegreeSetup` wird `true`
    ///   (Szenario 2 und 3), bis der Nutzer explizit wählt
    /// Eine bereits gespeicherte, unter den aktuellen Optionen aber nicht mehr gültige Auswahl
    /// (z. B. nach Exmatrikulation/Studiengangswechsel) wird zurückgesetzt statt stillschweigend
    /// beibehalten.
    static func loadApplyingAutoDetection(from tables: [GradeTable]) -> GradeSettings {
        var settings = load()

        var seen: Set<DegreeOption> = []
        let freshOptions = tables.compactMap(\.degreeOption).filter { seen.insert($0).inserted }
        if !freshOptions.isEmpty {
            settings.availableDegreeOptions = freshOptions
        }

        let abschlussOptions = settings.distinctAbschlussOptions
        if abschlussOptions.count == 1 {
            settings.selectedAbschluss = abschlussOptions[0]
        } else if !settings.selectedAbschluss.isEmpty && !abschlussOptions.contains(settings.selectedAbschluss) {
            settings.selectedAbschluss = ""
            settings.selectedFach = ""
        }

        if !settings.selectedAbschluss.isEmpty {
            let fachOptions = settings.fachOptions(forAbschluss: settings.selectedAbschluss)
            if fachOptions.count == 1 {
                settings.selectedFach = fachOptions[0]
            } else if !settings.selectedFach.isEmpty && !fachOptions.contains(settings.selectedFach) {
                settings.selectedFach = ""
            }
        } else {
            settings.selectedFach = ""
        }

        settings.save()
        return settings
    }

    func save() {
        let defaults = UserDefaults.standard
        defaults.set(averageMode.rawValue, forKey: Keys.averageMode)
        defaults.set(hideStudienleistungen, forKey: Keys.hideStudienleistungen)
        defaults.set(Array(visibleAttemptFields), forKey: Keys.visibleAttemptFields)
        defaults.set(customColors, forKey: Keys.customColors)
        defaults.set(semester, forKey: Keys.semester)
        defaults.set(targetEcts, forKey: Keys.targetEcts)
        if let data = try? JSONEncoder().encode(availableDegreeOptions) {
            defaults.set(data, forKey: Keys.availableDegreeOptions)
        }
        defaults.set(selectedAbschluss, forKey: Keys.selectedAbschluss)
        defaults.set(selectedFach, forKey: Keys.selectedFach)
    }
}
