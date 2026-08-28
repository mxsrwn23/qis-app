import Foundation

/// Merkt sich, welche Module beim letzten Anzeigen bereits benotet waren, und ermittelt daraus,
/// welche Module seit dem letzten bekannten Stand von "offen" auf "benotet" gewechselt sind. Damit
/// kann GradesView frisch benotete Module mit einem Neu-Badge markieren.
enum SeenGradesStore {
    private static let seenKey = "qis.seenGradedModules"

    /// Vergleicht die aktuell benoteten Module mit dem zuletzt gemerkten Stand und liefert die
    /// Modulnamen, die neu hinzugekommen sind. Aktualisiert dabei den gemerkten Stand.
    ///
    /// Beim allerersten Aufruf (noch keine Baseline gespeichert) wird der aktuelle Stand nur
    /// gemerkt und eine leere Menge zurückgegeben, damit nicht sämtliche vorhandenen Noten
    /// fälschlich als neu markiert werden.
    static func newlyGradedModuleKeys(in table: GradeTable) -> Set<String> {
        let currentGraded = gradedModuleKeys(in: table)
        let defaults = UserDefaults.standard

        guard let stored = defaults.stringArray(forKey: seenKey) else {
            defaults.set(Array(currentGraded), forKey: seenKey)
            return []
        }

        let baseline = Set(stored)
        let newlyGraded = currentGraded.subtracting(baseline)
        defaults.set(Array(currentGraded), forKey: seenKey)
        return newlyGraded
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: seenKey)
    }

    /// Baut die Modulkarten wie die Anzeige und liefert die Namen aller Module, für die bereits eine
    /// Note vorliegt -- entweder als finales Ergebnis (bestanden/nicht bestanden) oder als numerische
    /// Note in einer Versuchszeile, während der Modulstatus noch "offen" ist. So wird das Neu-Badge
    /// schon gesetzt, sobald die Note unten erscheint, und nicht erst wenn der Status oben wechselt.
    private static func gradedModuleKeys(in table: GradeTable) -> Set<String> {
        let cards = GradeCardBuilder.buildCards(table: table, settings: GradeSettings())
        let graded = cards.filter { $0.statusCategory == .be || $0.statusCategory == .fail || $0.hasGradeEntered }
        return Set(graded.map(\.moduleName))
    }
}
