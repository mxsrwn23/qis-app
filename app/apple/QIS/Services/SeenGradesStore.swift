import Foundation

/// Erkennt Module, deren Note seit dem letzten bekannten Stand von "offen" auf "benotet"
/// gewechselt ist, damit GradesView sie mit einem "Neu"-Badge markieren und automatisch aufklappen
/// kann. Der Vergleich läuft gegen den zuletzt gespeicherten Stand, der bei jedem Aufruf
/// aktualisiert wird -- das Badge erscheint also nur einmalig, bis die neue Note einmal angezeigt
/// wurde.
enum SeenGradesStore {
    private static let key = "qis.gradedModuleKeys"

    static func newlyGradedModuleKeys(in table: GradeTable) -> Set<String> {
        let currentGraded = gradedModuleKeys(in: table)
        defer { UserDefaults.standard.set(Array(currentGraded), forKey: key) }

        guard let previous = UserDefaults.standard.array(forKey: key) as? [String] else {
            // Kein vorheriger Stand vorhanden (erster Aufruf überhaupt): Baseline nur anlegen,
            // nichts als neu markieren.
            return []
        }
        return currentGraded.subtracting(previous)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }

    private static func gradedModuleKeys(in table: GradeTable) -> Set<String> {
        let cards = GradeCardBuilder.buildCards(table: table, settings: GradeSettings())
        return Set(cards.filter { !$0.grade.trimmingCharacters(in: .whitespaces).isEmpty }.map(\.moduleName))
    }
}
