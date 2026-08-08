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

    private func bootstrap() async {
        guard let credentials = KeychainStore.load() else {
            phase = .loggedOut
            return
        }
        phase = .loading
        await load(with: credentials)
    }

    private func refresh() async {
        guard let credentials = KeychainStore.load() else {
            phase = .loggedOut
            return
        }
        phase = .loading
        await load(with: credentials)
    }

    private func load(with credentials: Credentials) async {
        do {
            let gradeTable = try await QISClient().fetchGrades(username: credentials.username, password: credentials.password)
            phase = .loaded(gradeTable)
        } catch {
            phase = .failed(error.localizedDescription)
        }
    }

    private func logout() {
        KeychainStore.clear()
        phase = .loggedOut
    }
}
