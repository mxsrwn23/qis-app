import Foundation
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

    /// Frühester Abstand bis zum nächsten Lauf. iOS behandelt dies nur als Untergrenze.
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
        guard NotificationService.isEnabled, let credentials = KeychainStore.load() else { return }
        do {
            let table = try await QISClient().fetchGrades(
                username: credentials.username, password: credentials.password
            )
            GradeCache.save(table)
            let newModules = SeenGradesStore.newlyGradedForNotification(in: table)
            if !newModules.isEmpty {
                await NotificationService.notifyNewGrades(newModules.sorted())
            }
        } catch {
            // Hintergrundfehler still ignorieren; beim nächsten Lauf wird es erneut versucht.
        }
    }
}
