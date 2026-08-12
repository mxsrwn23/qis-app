import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Ein Eintrag pro Farbauswahl in den "Ansicht"-Einstellungen. Gedämpfte, dezente Palette statt
/// der lauten Apple-Systemfarben. Die Sättigung ist bewusst niedriger gehalten, damit die Badges
/// in einer dichten Kartenliste ruhig wirken.
enum GradeColorKey: String, CaseIterable {
    case be, pv, fail, an

    var label: String {
        switch self {
        case .be: return "Bestanden"
        case .pv: return "Offen"
        case .fail: return "Nicht bestanden"
        case .an: return "Angemeldet"
        }
    }

    var defaultColor: Color {
        switch self {
        case .be: return Color(hex: 0x368A50)   // gedämpftes Grün, angenähert an das Markenwaldgrün
        case .pv: return Color(hex: 0x3A6F85)   // gedämpftes Schieferblau, angenähert an das Marken-Türkis
        case .fail: return Color(hex: 0xAA2F3D) // gedämpftes Rot, angenähert an das Markenkarmesin
        case .an: return Color(hex: 0xA96A2C)   // gedämpftes Amber, angenähert an das Markenbrandorange
        }
    }
}

enum GradeStatusCategory: String {
    case be, pv, fail, an
    /// Nicht zur Prüfung zugelassen (NZ) -- die Prüfungsvorleistung wurde nicht bekommen.
    case notAdmitted
    /// Prüfung trotz Zulassung nicht geschrieben (Rücktritt, nicht erschienen, krank), daher kommt
    /// in diesem Semester kein Ergebnis mehr.
    case noResult

    /// Nur die vier Standardkategorien haben eine anpassbare Markenfarbe (GradeColorKey). Die
    /// Vermerks-Kategorien erhalten in GradeStyling feste Farben.
    var colorKey: GradeColorKey? {
        switch self {
        case .be: return .be
        case .pv: return .pv
        case .fail: return .fail
        case .an: return .an
        case .notAdmitted, .noResult: return nil
        }
    }
}

enum GradeStyling {
    private static let sectionPrefixes = ["kernmodule", "pflichtmodule", "wahlpflichtmodule"]

    static func columnIndex(in header: [String], containing keyword: String) -> Int? {
        header.firstIndex { $0.lowercased().contains(keyword) }
    }

    static func isModuleRow(_ row: [String], prüfungstextIndex: Int?) -> Bool {
        guard let index = prüfungstextIndex, index < row.count else { return false }
        return row[index].hasPrefix("Modul:")
    }

    static func isSectionRow(_ row: [String], prüfungstextIndex: Int?) -> Bool {
        guard let index = prüfungstextIndex, index < row.count else { return false }
        let normalized = row[index].trimmingCharacters(in: .whitespaces).lowercased()
        return sectionPrefixes.contains { normalized.hasPrefix($0) }
    }

    // MARK: - Farbauflösung (Standardwerte, überschreibbar über GradeSettings.customColors)

    static func accent(for key: GradeColorKey, customColors: [String: String]) -> Color {
        if let hex = customColors[key.rawValue], let color = Color(hexString: hex) {
            return color
        }
        return key.defaultColor
    }

    static func backgroundTint(for key: GradeColorKey, customColors: [String: String]) -> Color {
        accent(for: key, customColors: customColors).opacity(0.14)
    }

    /// Farbe pro Status-Kategorie. "Nicht zugelassen" bekommt ein aufmerksamkeitsstarkes gedämpftes
    /// Amber (ein Hinweis, der auffallen soll), "Kein Ergebnis" ein neutrales Grau. Beide heben sich
    /// klar vom Blau des offenen Status ab.
    static func accent(for category: GradeStatusCategory, customColors: [String: String]) -> Color {
        switch category {
        case .notAdmitted: return Color(hex: 0xB2661F)
        case .noResult: return .gray
        default:
            guard let key = category.colorKey else { return .gray }
            return accent(for: key, customColors: customColors)
        }
    }

    static func backgroundTint(for category: GradeStatusCategory, customColors: [String: String]) -> Color {
        accent(for: category, customColors: customColors).opacity(0.14)
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    init?(hexString: String) {
        var value = hexString.trimmingCharacters(in: .whitespaces)
        if value.hasPrefix("#") { value.removeFirst() }
        guard value.count == 6, let parsed = UInt32(value, radix: 16) else { return nil }
        self.init(hex: parsed)
    }

    private func srgbComponents() -> (r: Double, g: Double, b: Double) {
        #if canImport(UIKit)
        let native = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        native.getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Double(r), Double(g), Double(b))
        #else
        let native = NSColor(self).usingColorSpace(.deviceRGB) ?? NSColor(self)
        return (Double(native.redComponent), Double(native.greenComponent), Double(native.blueComponent))
        #endif
    }

    var hexString: String {
        let c = srgbComponents()
        return String(
            format: "#%02X%02X%02X",
            Int((c.r * 255).rounded()), Int((c.g * 255).rounded()), Int((c.b * 255).rounded())
        )
    }
}
