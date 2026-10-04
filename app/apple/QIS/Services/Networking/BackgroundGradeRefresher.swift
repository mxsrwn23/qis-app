import Foundation
import os
#if os(iOS)
import BackgroundTasks
#endif

/// Plant und verarbeitet den periodischen Hintergrund-Abruf des Notenspiegels über BGTaskScheduler
/// (nur iOS). iOS entscheidet selbst über den genauen Ausführungszeitpunkt – die Ausführung ist
/// daher „best effort" und kann sich verzögern. Bei neu benoteten Modulen wird eine lokale
/// Benachrichtigung zugestellt.
enum BackgroundGradeRefresher {
    /// Muss identisch in der Info.plist unter `BGTaskSchedulerPermittedIdentifiers` hinterlegt sein.
    static let taskIdentifier = "dev.maxsauerwein.qis.refresh"

    private static let logger = Logger(subsystem: "dev.maxsauerwein.qis", category: "BackgroundGradeRefresher")

    /// Frühester Abstand bis zum nächsten Lauf. iOS behandelt dies nur als Untergrenze.
    /// Im Debug-Build kurz gehalten, damit sich der echte Hintergrund-Lauf beim Testen nicht durch
    /// jedes erneute Backgrounding um weitere 4h verschiebt (jedes `schedule()` ersetzt die zuvor
    /// eingeplante Anfrage).
    private static let earliestInterval: TimeInterval = 4 * 60 * 60

    /// Registriert den Task-Handler. Muss vor Abschluss des App-Starts aufgerufen werden.
    static func register() {
        #if os(iOS)
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(task: refreshTask)
        }
        #endif
    }

    /// Plant den nächsten Hintergrund-Abruf, sofern der Nutzer Benachrichtigungen aktiviert hat.
    static func schedule() {
        #if os(iOS)
        guard NotificationService.isEnabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: earliestInterval)
        // Wirft im Simulator oder ohne erteilte Berechtigung – im Hintergrund unkritisch.
        try? BGTaskScheduler.shared.submit(request)
        #endif
    }

    static func cancel() {
        #if os(iOS)
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        #endif
    }

    #if os(iOS)
    private static func handle(task: BGAppRefreshTask) {
        // Direkt den nächsten Lauf einplanen, damit die Kette nicht abreißt.
        schedule()

        let work = Task {
            await performRefresh()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = {
            work.cancel()
            task.setTaskCompleted(success: false)
        }
    }
    #endif

    /// Holt den Notenspiegel, aktualisiert den Cache und benachrichtigt über neu benotete Module.
    /// Nutzt die gespeicherte Session (leichter Abruf ohne vollen SAML-Login, wenn möglich).
    static func performRefresh() async {
        logger.log("performRefresh gestartet")
        guard NotificationService.isEnabled else {
            logger.log("Abbruch: Benachrichtigungen sind deaktiviert")
            return
        }
        guard let credentials = KeychainStore.load() else {
            logger.error("Abbruch: keine Credentials im Keychain gefunden")
            return
        }
        do {
            let tables = try await QISClient().fetchGrades(
                username: credentials.username, password: credentials.password
            )
            GradeCache.save(tables)
            // Solange der Nutzer bei mehreren Abschluss-/Fach-Kombinationen noch keine Wahl
            // getroffen hat (siehe GradeSettings.needsDegreeSetup), lässt sich nicht eindeutig
            // bestimmen, welche Tabelle für die Benachrichtigung relevant ist -- in diesem Fall
            // wird nur gecacht, aber nicht benachrichtigt (die Auswahl erfolgt beim nächsten
            // App-Start im Ersteinrichtungs-Dialog).
            let settings = GradeSettings.loadApplyingAutoDetection(from: tables)
            guard let active = settings.activeTable(in: tables) else {
                logger.log("Abruf erfolgreich, aber Abschluss-/Studiengangswahl steht noch aus")
                return
            }
            let newModules = SeenGradesStore.newlyGradedForNotification(in: active)
            logger.log("Abruf erfolgreich, \(newModules.count) neu benotete Module")
            if !newModules.isEmpty {
                await NotificationService.notifyNewGrades(newModules.sorted())
            }
        } catch {
            // Hintergrundfehler still ignorieren; beim nächsten Lauf wird es erneut versucht.
            logger.error("Abbruch durch Fehler: \(error.localizedDescription)")
        }
    }
}
