import Foundation

/// Merkt sich, welche Module beim letzten Anzeigen bereits benotet waren, und ermittelt daraus,
/// welche Module seit dem letzten bekannten Stand von "offen" auf "benotet" gewechselt sind. Damit
/// kann GradesView frisch benotete Module mit einem Neu-Badge markieren.
enum SeenGradesStore {
    private static let seenKey = "qis.seenGradedModules"
    /// Eigene Baseline für Push-Benachrichtigungen, getrennt vom UI-Neu-Badge (`seenKey`). So kann
    /// ein Hintergrund-Abruf über neue Noten benachrichtigen, ohne die Badge-Baseline zu verbrauchen,
    /// und ohne bei jedem Lauf erneut über dieselbe Note zu benachrichtigen.
    private static let notifiedKey = "qis.notifiedGradedModules"

    /// Vergleicht die aktuell benoteten Module mit dem zuletzt gemerkten Stand und liefert die
    /// Modulnamen, die neu hinzugekommen sind. Aktualisiert dabei den gemerkten Stand.
    ///
    /// Beim allerersten Aufruf (noch keine Baseline gespeichert) wird der aktuelle Stand nur
    /// gemerkt und eine leere Menge zurückgegeben, damit nicht sämtliche vorhandenen Noten
    /// fälschlich als neu markiert werden.
    static func newlyGradedModuleKeys(in table: GradeTable) -> Set<String> {
        let currentGraded = gradedModuleKeys(in: table)
        let defaults = UserDefaults.standard

        // Was der Nutzer jetzt in der App sieht, muss später nicht mehr per Push gemeldet werden.
        defaults.set(Array(currentGraded), forKey: notifiedKey)

        guard let stored = defaults.stringArray(forKey: seenKey) else {
            defaults.set(Array(currentGraded), forKey: seenKey)
            return []
        }

        let baseline = Set(stored)
        let newlyGraded = currentGraded.subtracting(baseline)
        defaults.set(Array(currentGraded), forKey: seenKey)
        return newlyGraded
    }

    /// Wie `newlyGradedModuleKeys`, aber gegen die Benachrichtigungs-Baseline (`notifiedKey`) und
    /// ohne die UI-Baseline zu verändern. Für den Hintergrund-Abruf gedacht.
    static func newlyGradedForNotification(in table: GradeTable) -> Set<String> {
        let currentGraded = gradedModuleKeys(in: table)
        let defaults = UserDefaults.standard

        guard let stored = defaults.stringArray(forKey: notifiedKey) else {
            defaults.set(Array(currentGraded), forKey: notifiedKey)
            return []
        }

        let newly = currentGraded.subtracting(Set(stored))
        defaults.set(Array(currentGraded), forKey: notifiedKey)
        return newly
    }

    static func clear() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: seenKey)
        defaults.removeObject(forKey: notifiedKey)
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
