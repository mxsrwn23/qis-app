import Foundation

/// Speichert die vom Nutzer ausgeblendeten Module lokal für die aktuelle Anmeldung.
enum ModuleArchiveStore {
    private static let archivedModuleKeysKey = "qis.archivedModuleKeys"

    static func load() -> Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: archivedModuleKeysKey) ?? [])
    }

    static func save(_ moduleKeys: Set<String>) {
        UserDefaults.standard.set(Array(moduleKeys), forKey: archivedModuleKeysKey)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: archivedModuleKeysKey)
    }
}
