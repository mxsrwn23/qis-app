import Foundation

/// Speichert die Module, die der Nutzer vom Notenschnitt ausschließt.
enum AverageInclusionStore {
    private static let excludedModuleKeysKey = "qis.excludedFromAverageModuleKeys"

    static func loadExcludedModuleKeys() -> Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: excludedModuleKeysKey) ?? [])
    }

    static func saveExcludedModuleKeys(_ moduleKeys: Set<String>) {
        UserDefaults.standard.set(Array(moduleKeys), forKey: excludedModuleKeysKey)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: excludedModuleKeysKey)
    }
}
