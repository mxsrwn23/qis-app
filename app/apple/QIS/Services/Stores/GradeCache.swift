import Foundation

/// Persistiert den zuletzt erfolgreich abgerufenen Notenspiegel lokal, damit die App das
/// QIS-Portal nicht bei jedem Start oder Pull-to-refresh neu kontaktieren muss.
enum GradeCache {
    /// Ab diesem Alter gilt der Cache als veraltet und ein passiver Abruf (App-Start) holt frisch.
    static let minCacheAge: TimeInterval = 15 * 60
    /// Harte Untergrenze zwischen zwei Abrufversuchen, auch bei bewusstem Force-Refresh.
    static let minRetryInterval: TimeInterval = 30

    private static let tableKey = "qis.cache.table"
    private static let lastFetchKey = "qis.cache.lastFetch"
    private static let lastAttemptKey = "qis.cache.lastAttempt"

    /// Lädt alle beim letzten Abruf gecachten (Abschluss, Fach)-Tabellen (meist genau eine).
    static func load() -> [GradeTable]? {
        guard let data = UserDefaults.standard.data(forKey: tableKey) else { return nil }
        return try? JSONDecoder().decode([GradeTable].self, from: data)
    }

    static func save(_ tables: [GradeTable]) {
        let defaults = UserDefaults.standard
        if let data = try? JSONEncoder().encode(tables) {
            defaults.set(data, forKey: tableKey)
        }
        defaults.set(Date().timeIntervalSince1970, forKey: lastFetchKey)
    }

    static func isFresh() -> Bool {
        let timestamp = UserDefaults.standard.double(forKey: lastFetchKey)
        guard timestamp > 0 else { return false }
        return Date().timeIntervalSince1970 - timestamp < minCacheAge
    }

    static func canAttempt() -> Bool {
        secondsUntilNextAttempt() == 0
    }

    /// Verbleibende Sekunden bis zum nächsten erlaubten Abrufversuch (0, wenn sofort möglich).
    static func secondsUntilNextAttempt() -> Int {
        let timestamp = UserDefaults.standard.double(forKey: lastAttemptKey)
        guard timestamp > 0 else { return 0 }
        let remaining = minRetryInterval - (Date().timeIntervalSince1970 - timestamp)
        return remaining > 0 ? Int(remaining.rounded(.up)) : 0
    }

    /// Zeitpunkt des zuletzt erfolgreich geladenen Notenspiegels, für die "Zuletzt aktualisiert"-Anzeige.
    static func lastFetchDate() -> Date? {
        let timestamp = UserDefaults.standard.double(forKey: lastFetchKey)
        guard timestamp > 0 else { return nil }
        return Date(timeIntervalSince1970: timestamp)
    }

    static func recordAttempt() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: lastAttemptKey)
    }

    static func clear() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: tableKey)
        defaults.removeObject(forKey: lastFetchKey)
        defaults.removeObject(forKey: lastAttemptKey)
    }
}
