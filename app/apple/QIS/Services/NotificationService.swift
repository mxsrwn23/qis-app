import Foundation
import UserNotifications

/// Kapselt die lokale Benachrichtigung über neu benotete Module sowie den zugehörigen
/// Einstellungs-Schalter. Nutzt ausschließlich lokale Notifications – es ist kein Server beteiligt.
enum NotificationService {
    private static let enabledKey = "qis.notificationsEnabled"

    /// Vom Nutzer in den Einstellungen gewählter Wunsch, über neue Noten benachrichtigt zu werden.
    static var isEnabled: Bool {
        UserDefaults.standard.bool(forKey: enabledKey)
    }

    static func setEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: enabledKey)
    }

    /// Fragt die Systemberechtigung an und liefert zurück, ob Benachrichtigungen erlaubt wurden.
    @discardableResult
    static func requestAuthorization() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Stellt eine lokale Benachrichtigung über die frisch benoteten Module zu.
    static func notifyNewGrades(_ moduleNames: [String]) async {
        guard !moduleNames.isEmpty else { return }

        let content = UNMutableNotificationContent()
        if moduleNames.count == 1 {
            content.title = "Neue Note"
            content.body = moduleNames[0]
        } else {
            let preview = moduleNames.prefix(3).joined(separator: ", ")
            content.title = "Neue Noten"
            content.body = "\(moduleNames.count) Module wurden benotet: \(preview)"
                + (moduleNames.count > 3 ? " …" : "")
        }
        content.sound = .default

        // trigger: nil stellt die Benachrichtigung sofort zu (der Abruf lief bereits im Hintergrund).
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        try? await UNUserNotificationCenter.current().add(request)
    }
}
