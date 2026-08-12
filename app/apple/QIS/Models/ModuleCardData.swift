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

    var statusCategory: GradeStatusCategory? {
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
        case .none: return status.trimmingCharacters(in: .whitespaces)
        }
    }
}

enum GradesFilter: String, CaseIterable, Identifiable {
    case all, passed, open
    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: return "Alle"
        case .passed: return "Bestanden"
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
