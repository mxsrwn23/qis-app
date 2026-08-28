import SwiftUI

/// Erläutert die Aktualisierungsgrenzen der App und die auftretenden Meldungen in verständlicher
/// Sprache. Die Wartezeiten werden direkt aus GradeCache abgeleitet, damit die Texte immer zu den
/// tatsächlich wirksamen Werten passen.
struct HelpView: View {
    private var refreshMinutes: Int { max(1, Int(GradeCache.minCacheAge / 60)) }
    private var retrySeconds: Int { Int(GradeCache.minRetryInterval) }

    var body: some View {
        List {
            Section {
                infoRow(
                    icon: "arrow.clockwise",
                    tint: .blue,
                    title: "Automatische Aktualisierung",
                    message: "Beim Öffnen der App siehst du sofort deine zuletzt geladenen Noten. Ein neuer Abruf beim QIS-Portal erfolgt erst, wenn die Daten älter als \(refreshMinutes) Minuten sind."
                )
                infoRow(
                    icon: "hand.raised",
                    tint: .orange,
                    title: "Manuelle Aktualisierung",
                    message: "Du kannst jederzeit durch Herunterziehen der Liste aktualisieren. Zwischen zwei Abrufen liegt eine Mindestwartezeit von \(retrySeconds) Sekunden."
                )
            } header: {
                Text("Aktualisierung")
            } footer: {
                Text("Diese Grenzen schonen das QIS-Portal, das nicht für häufige automatische Abrufe ausgelegt ist. Das vermeidet unnötige Serverlast, senkt das Risiko einer vorübergehenden Sperrung deines Zugangs und schont Akku sowie Datenvolumen.")
            }

            Section {
                infoRow(
                    icon: "sparkles",
                    tint: .red,
                    title: "Neu-Kennzeichnung",
                    message: "Module mit einer neu eingetragenen Note werden mit einem roten „NEU\"-Hinweis markiert. Die Markierung erscheint bereits, sobald eine Note in einer Versuchszeile vorliegt – auch wenn der Modulstatus noch „Offen\" lautet."
                )
            } header: {
                Text("Neue Noten")
            }

            Section {
                messageRow(
                    title: "Keine Verbindung",
                    message: "Es konnte keine Verbindung zum QIS-Portal hergestellt werden. Bitte prüfe deine Internetverbindung und versuche es erneut."
                )
                messageRow(
                    title: "Zugangsdaten wurden nicht akzeptiert",
                    message: "Benutzername oder Passwort stimmen nicht mit deinem QIS-Konto überein. Bitte prüfe deine Eingaben in den Einstellungen."
                )
                messageRow(
                    title: "Sitzung abgelaufen",
                    message: "Deine Anmeldung am QIS-Portal ist nicht mehr gültig. Die App meldet sich beim nächsten Abruf automatisch erneut an – in der Regel musst du nichts tun."
                )
                messageRow(
                    title: "Anmeldung fehlgeschlagen",
                    message: "Die Anmeldung war nicht erfolgreich. Häufig liegt eine kurzfristige Störung des Portals vor; ein erneuter Versuch nach kurzer Wartezeit hilft meist."
                )
                messageRow(
                    title: "Notenspiegel nicht gefunden",
                    message: "Der Notenspiegel konnte nicht geladen werden. Das QIS-Portal hat unerwartet geantwortet – etwa wegen Wartungsarbeiten oder einer geänderten Seitenstruktur. Bitte versuche es später erneut."
                )
            } header: {
                Text("Meldungen verstehen")
            } footer: {
                Text("Bei wiederkehrenden Problemen melde dich in den Einstellungen ab und erneut an, um eine frische Sitzung herzustellen.")
            }

            Section {
                infoRow(
                    icon: "lock.shield",
                    tint: .green,
                    title: "Sicher gespeichert",
                    message: "Deine Zugangsdaten werden ausschließlich verschlüsselt im Schlüsselbund deines Geräts abgelegt. Die abgerufenen Noten werden nur lokal zwischengespeichert und an keine Dritten weitergegeben."
                )
            } header: {
                Text("Datenschutz")
            }
        }
        .navigationTitle("Hilfe & Info")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
    }

    private func infoRow(icon: String, tint: Color, title: String, message: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.body.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 28, height: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.callout.weight(.semibold))
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func messageRow(title: String, message: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.callout.weight(.semibold))
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}
