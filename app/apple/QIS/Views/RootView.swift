import SwiftUI

struct RootView: View {
    private enum Phase {
        case checkingCredentials
        case loggedOut
        case loading
        case loaded(GradeTable)
        case failed(String)
    }

    @State private var phase: Phase = .checkingCredentials
    @State private var refreshErrorMessage: String?

    var body: some View {
        Group {
            switch phase {
            case .checkingCredentials:
                ProgressView()
                    // Als eigener Task gestartet statt direkt in `.task` erwartet. Sobald
                    // bootstrap() `phase` auf `.loading` setzt, wird diese ProgressView entfernt,
                    // und SwiftUI bricht einen `.task`-Closure ab, der noch auf einer
                    // verschwundenen View läuft. Das würde die laufende Login-Anfrage mit
                    // URLError.cancelled abbrechen. Ein losgelöster Task ist nicht an die
                    // Lebensdauer dieser View gebunden und übersteht den Wechsel.
                    .task { Task { await bootstrap() } }
            case .loggedOut:
                LoginView { credentials, gradeTable in
                    KeychainStore.save(credentials)
                    GradeCache.save(gradeTable)
                    phase = .loaded(gradeTable)
                }
            case .loading:
                ProgressView("Lade Notenspiegel…")
            case .loaded(let gradeTable):
                NavigationStack {
                    GradesView(
                        gradeTable: gradeTable,
                        onRefresh: { Task { await refresh() } },
                        onLogout: logout
                    )
                }
                .alert("Aktualisierung fehlgeschlagen", isPresented: refreshErrorBinding) {
                    Button("OK", role: .cancel) { }
                } message: {
                    Text(refreshErrorMessage ?? "")
                }
            case .failed(let message):
                VStack(spacing: 16) {
                    Text(message)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                    Button("Erneut versuchen") {
                        Task { await refresh() }
                    }
                    Button("Abmelden", role: .destructive, action: logout)
                }
                .padding()
            }
        }
    }

    private var refreshErrorBinding: Binding<Bool> {
        Binding(
            get: { refreshErrorMessage != nil },
            set: { isPresented in if !isPresented { refreshErrorMessage = nil } }
        )
    }

    /// Passiver Abruf beim App-Start: zeigt vorhandene gecachte Daten sofort an (kein leerer
    /// Ladescreen) und holt nur bei Bedarf im Hintergrund nach.
    private func bootstrap() async {
        guard let credentials = KeychainStore.load() else {
            phase = .loggedOut
            return
        }
        if let cached = GradeCache.load() {
            phase = .loaded(cached)
        } else {
            phase = .loading
        }
        await performFetch(credentials: credentials, force: false)
    }

    /// Bewusster Force-Refresh, ausgelöst durch Pull-to-refresh oder den Retry-Button.
    private func refresh() async {
        guard let credentials = KeychainStore.load() else {
            phase = .loggedOut
            return
        }
        await performFetch(credentials: credentials, force: true)
    }

    /// Zentrale Stelle für alle Notenspiegel-Abrufe: respektiert Cache-Frische, Force-Refresh und
    /// die harte 30-Sekunden-Untergrenze zwischen Versuchen. Ruft das QIS-Portal nur, wenn
    /// wirklich nötig, und fällt bei einem Fehler auf zuletzt gecachte Daten zurück, falls
    /// vorhanden -- kein automatischer Sofort-Retry.
    private func performFetch(credentials: Credentials, force: Bool) async {
        guard GradeCache.canAttempt() else {
            return
        }
        if !force, GradeCache.isFresh(), let cached = GradeCache.load() {
            phase = .loaded(cached)
            return
        }
        GradeCache.recordAttempt()
        do {
            let gradeTable = try await QISClient().fetchGrades(username: credentials.username, password: credentials.password)
            GradeCache.save(gradeTable)
            refreshErrorMessage = nil
            phase = .loaded(gradeTable)
        } catch {
            if let cached = GradeCache.load() {
                phase = .loaded(cached)
                refreshErrorMessage = error.localizedDescription
            } else {
                phase = .failed(error.localizedDescription)
            }
        }
    }

    private func logout() {
        KeychainStore.clear()
        GradeCache.clear()
        SessionCookieStore.clear()
        phase = .loggedOut
    }
}
