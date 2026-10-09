import Foundation

/// Gibt die Login-/Scrape-Schritte aus `QISClient` in der Xcode-Konsole aus, damit sich Probleme
/// beim QIS-Zugriff auch ohne Breakpoints nachvollziehen lassen.
enum DebugLog {
    static func log(_ message: String) {
        print("[QIS] \(message)")
    }
}
