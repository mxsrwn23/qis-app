import SwiftUI

struct RootView: View {
    private enum Phase {
        case checkingCredentials
        case loggedOut
        case loading
        /// Login/Cache erfolgreich, aber QIS meldet mehrere Abschluss-Typen und/oder
        /// Studiengänge -- der Nutzer muss erst wählen, bevor eine Notenliste angezeigt wird.
        case needsDegreeSelection([GradeTable])
        case loaded([GradeTable])
        case failed(String)
    }

    @State private var phase: Phase = .checkingCredentials
    @State private var refreshErrorMessage: String?
    @State private var newModuleKeys: Set<String> = []
    @State private var noticeMessage: String?
    @State private var showingHelp = false

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
                LoginView { credentials, gradeTables in
                    KeychainStore.save(credentials)
                    GradeCache.save(gradeTables)
                    present(gradeTables)
                }
            case .loading:
                ProgressView("Lade Notenspiegel…")
            case .needsDegreeSelection(let gradeTables):
                NavigationStack {
                    DegreeSetupView(tables: gradeTables) {
                        present(gradeTables)
                    }
                }
            case .loaded(let gradeTables):
                NavigationStack {
                    GradesView(
                        gradeTables: gradeTables,
                        newModuleKeys: newModuleKeys,
                        onRefresh: { Task { await refresh() } },
                        onLogout: logout
                    )
                }
                .alert("Aktualisierung fehlgeschlagen", isPresented: refreshErrorBinding) {
                    Button("Hilfe öffnen") { showingHelp = true }
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
        .overlay(alignment: .top) {
            if let noticeMessage {
                NoticeBanner(message: noticeMessage)
                    .padding(.horizontal, 16)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: noticeMessage)
        .sheet(isPresented: $showingHelp) {
            NavigationStack {
                HelpView()
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Fertig") { showingHelp = false }
                        }
                    }
            }
        }
    }

    /// Zeigt eine kurzlebige Hinweismeldung an und blendet sie nach wenigen Sekunden wieder aus.
    private func showNotice(_ message: String) {
        noticeMessage = message
        Task {
            try? await Task.sleep(for: .seconds(3))
            if noticeMessage == message { noticeMessage = nil }
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
            present(cached)
        } else {
            phase = .loading
        }
        await performFetch(credentials: credentials, force: false)
    }

    /// Zentrale Stelle für jeden Wechsel zu (neu geladenen oder gecachten) Notentabellen: wendet
    /// zunächst die Auto-Erkennung auf `GradeSettings` an (siehe
    /// `GradeSettings.loadApplyingAutoDetection`) und zeigt bei mehreren noch unentschiedenen
    /// Abschluss-/Fach-Kombinationen erst den Ersteinrichtungs-Dialog, statt direkt eine
    /// (möglicherweise falsche) Tabelle anzuzeigen. Ist die Auswahl eindeutig, ermittelt sie
    /// einmalig die frisch benoteten Module der aktiven Tabelle gegenüber dem zuletzt gesehenen
    /// Stand. So läuft der Vergleich nur bei echten Datenwechseln, nicht bei jedem Re-Render von
    /// GradesView (Filter, Sortierung, Einstellungen).
    private func present(_ tables: [GradeTable]) {
        let settings = GradeSettings.loadApplyingAutoDetection(from: tables)
        guard !settings.needsDegreeSetup else {
            phase = .needsDegreeSelection(tables)
            return
        }
        if case .loaded(let current) = phase, current == tables {
            // Bereits angezeigte Tabellen (z. B. Cache-Anzeige gefolgt vom Fresh-Check in
            // performFetch): nicht erneut gegen die inzwischen aktualisierte Baseline abgleichen,
            // sonst würden gerade erkannte neue Noten sofort wieder verworfen.
            return
        }
        if let active = settings.activeTable(in: tables) {
            newModuleKeys = SeenGradesStore.newlyGradedModuleKeys(in: active)
        }
        phase = .loaded(tables)
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
        let wait = GradeCache.secondsUntilNextAttempt()
        guard wait == 0 else {
            // Nur bei bewussten Aktualisierungen Rückmeldung geben, nicht beim passiven App-Start.
            if force {
                showNotice("Bitte warte noch \(wait) \(wait == 1 ? "Sekunde" : "Sekunden"), bevor du erneut aktualisierst.")
            }
            return
        }
        if !force, GradeCache.isFresh(), let cached = GradeCache.load() {
            present(cached)
            return
        }
        GradeCache.recordAttempt()
        do {
            let gradeTables = try await QISClient().fetchGrades(username: credentials.username, password: credentials.password)
            GradeCache.save(gradeTables)
            refreshErrorMessage = nil
            present(gradeTables)
        } catch {
            if let cached = GradeCache.load() {
                present(cached)
                refreshErrorMessage = error.localizedDescription
            } else {
                phase = .failed(error.localizedDescription)
            }
        }
    }

    private func logout() {
        KeychainStore.clear()
        GradeCache.clear()
        ModuleArchiveStore.clear()
        AverageInclusionStore.clear()
        SessionCookieStore.clear()
        SeenGradesStore.clear()
        phase = .loggedOut
    }
}

/// Kurzlebige Hinweiskarte am oberen Bildschirmrand, z. B. für die Aktualisierungs-Wartezeit.
private struct NoticeBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "clock")
                .foregroundStyle(.secondary)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.primary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.08))
        }
        .shadow(color: .black.opacity(0.1), radius: 8, y: 4)
    }
}
