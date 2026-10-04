import Foundation

struct AttemptRow: Identifiable {
    let id = UUID()
    let semester: String
    let note: String
    let versuch: String
    let datum: String
    let status: String

    var isFailed: Bool {
        let normalized = status.trimmingCharacters(in: .whitespaces).lowercased()
        return normalized == "nb" || normalized == "en"
    }
}

struct ModuleCardData: Identifiable {
    var id: String { "\(sectionTitle ?? "")|\(moduleName)" }
    let sectionTitle: String?
    let moduleName: String
    let grade: String
    let status: String
    let ects: String
    let attempts: [AttemptRow]
    /// True, wenn dieses Modul seit dem letzten bekannten Stand von "offen" auf "benotet"
    /// gewechselt ist, siehe SeenGradesStore.
    var isNew = false
    /// Aus den Versuchsvermerken ermittelter Status (`.notAdmitted` bei NZ, `.noResult` bei
    /// RT/NE/KR), der den regulären Modulstatus ersetzt, solange noch kein finales Ergebnis
    /// vorliegt. Wird von GradeCardBuilder gesetzt.
    var remarkStatus: GradeStatusCategory?

    /// True, wenn im jüngsten (untersten) Versuch bereits eine numerische Note steht -- auch wenn der
    /// Modulstatus noch "offen" ist. Noten erscheinen zuerst unten in der Versuchszeile, bevor der
    /// Modulstatus oben auf "bestanden" wechselt; das Neu-Badge soll schon ab diesem Zeitpunkt greifen.
    /// Bewusst nur der jüngste Versuch: Bei einem nicht bestandenen Versuch mit noch unbenotetem
    /// Wiederholungsversuch (neue leere Versuchszeile) gilt das Modul nicht als frisch benotet.
    var hasGradeEntered: Bool {
        guard let latest = attempts.last else { return false }
        return Double(latest.note.replacingOccurrences(of: ",", with: ".")) != nil
    }

    var statusCategory: GradeStatusCategory? {
        let base = baseStatusCategory
        // Ein Modul, das noch kein finales Ergebnis hat, aber einen Vermerk trägt, aus dem in diesem
        // Semester keine Note mehr folgt, wird als eigene Kategorie geführt -- sonst sähe es wie ein
        // normal offenes Modul aus.
        if let remarkStatus, base != .be, base != .fail {
            return remarkStatus
        }
        return base
    }

    private var baseStatusCategory: GradeStatusCategory? {
        switch status.trimmingCharacters(in: .whitespaces).lowercased() {
        case "be": return .be
        case "pv": return .pv
        case "nb", "en": return .fail
        case "an": return .an
        default: return nil
        }
    }

    var statusLabel: String {
        switch statusCategory {
        case .be: return "Bestanden"
        case .pv: return "Offen"
        case .fail: return "Nicht bestanden"
        case .an: return "Angemeldet"
        case .notAdmitted: return "Nicht zugelassen"
        case .noResult: return "Kein Ergebnis"
        case .none: return status.trimmingCharacters(in: .whitespaces)
        }
    }
}

enum GradesFilter: String, CaseIterable, Identifiable {
    case all, open

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return "Alle"
        case .open: return "Offen"
        }
    }
}

enum GradesSort: String, CaseIterable, Identifiable {
    case none, grade, semester
    var id: String { rawValue }
    var label: String {
        switch self {
        case .none: return "Standard"
        case .grade: return "Note"
        case .semester: return "Semester"
        }
    }
}
