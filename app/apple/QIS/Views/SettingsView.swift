import SwiftUI

struct SettingsView: View {
    @Binding var gradeSettings: GradeSettings
    let onLogout: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var username = KeychainStore.load()?.username ?? ""
    @State private var password = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var notificationsEnabled = NotificationService.isEnabled
    @State private var showingNotificationDeniedAlert = false
    #if DEBUG
    @State private var debugRefreshStatus: String?
    #endif

    var body: some View {
        NavigationStack {
            List {
                Section("Zugangsdaten") {
                    TextField("Benutzername", text: $username)
                        #if os(iOS)
                        .textInputAutocapitalization(.never)
                        #elseif os(macOS)
                        .textFieldStyle(.roundedBorder)
                        #endif
                        .autocorrectionDisabled()
                    SecureField("Neues Passwort (leer = unverändert)", text: $password)
                        #if os(macOS)
                        .textFieldStyle(.roundedBorder)
                        #endif
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Section {
                    Button(action: save) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Text("Speichern")
                        }
                    }
                    .disabled(username.isEmpty || isSaving)
                }

                Section("Profil") {
                    TextField("Studiengang (z. B. B.Sc. Informatik)", text: $gradeSettings.studiengang)
                        #if os(macOS)
                        .textFieldStyle(.roundedBorder)
                        #endif
                        .autocorrectionDisabled()
                    HStack {
                        Text("Semester")
                        Spacer()
                        TextField("z. B. 6", text: semesterText)
                            #if os(iOS)
                            .keyboardType(.numberPad)
                            #elseif os(macOS)
                            .textFieldStyle(.roundedBorder)
                            #endif
                            .multilineTextAlignment(.trailing)
                            .frame(width: 60)
                    }
                    HStack {
                        Text("Ziel-ECTS")
                        Spacer()
                        TextField("automatisch", text: targetEctsText)
                            #if os(iOS)
                            .keyboardType(.numberPad)
                            #elseif os(macOS)
                            .textFieldStyle(.roundedBorder)
                            #endif
                            .multilineTextAlignment(.trailing)
                            .frame(width: 80)
                    }
                }

                Section {
                    Toggle("Neue Noten melden", isOn: $notificationsEnabled)
                    #if DEBUG
                    Button("Hintergrund-Abruf jetzt testen (Debug)") {
                        debugRefreshStatus = "Läuft…"
                        Task {
                            await BackgroundGradeRefresher.performRefresh()
                            debugRefreshStatus = "Fertig – Details in der Xcode-Konsole"
                        }
                    }
                    Button("Benachrichtigungs-Baseline zurücksetzen (Debug)") {
                        SeenGradesStore.debugForceEmptyNotifiedBaseline()
                        debugRefreshStatus = "Baseline geleert – jetzt \"Hintergrund-Abruf jetzt testen\" tippen"
                    }
                    if let debugRefreshStatus {
                        Text(debugRefreshStatus)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    #endif
                } header: {
                    Text("Benachrichtigungen")
                } footer: {
                    Text("Die App prüft im Hintergrund gelegentlich auf neue Noten und benachrichtigt dich. Der genaue Zeitpunkt wird vom System bestimmt, Meldungen können sich daher verzögern.")
                }

                Section {
                    NavigationLink {
                        HelpView()
                    } label: {
                        Label("Hilfe & Info", systemImage: "questionmark.circle")
                    }
                }

                Section {
                    Button("Abmelden", role: .destructive, action: onLogout)
                }
            }
            .navigationTitle("Einstellungen")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fertig") { dismiss() }
                }
            }
            .onChange(of: notificationsEnabled) { _, enabled in
                Task { await updateNotifications(enabled: enabled) }
            }
            .alert("Benachrichtigungen deaktiviert", isPresented: $showingNotificationDeniedAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text("Erlaube Benachrichtigungen für QIS+ in den Systemeinstellungen, um über neue Noten informiert zu werden.")
            }
        }
        #if os(macOS)
        .frame(minWidth: 420, minHeight: 480)
        #endif
    }

    private var semesterText: Binding<String> {
        Binding(
            get: { gradeSettings.semester > 0 ? String(gradeSettings.semester) : "" },
            set: { gradeSettings.semester = Int($0) ?? 0 }
        )
    }

    private var targetEctsText: Binding<String> {
        Binding(
            get: { gradeSettings.targetEcts > 0 ? String(gradeSettings.targetEcts) : "" },
            set: { gradeSettings.targetEcts = Int($0) ?? 0 }
        )
    }

    /// Schaltet Benachrichtigungen ein/aus: fragt beim Aktivieren die Systemberechtigung an und
    /// plant den Hintergrund-Abruf, oder bricht ihn beim Deaktivieren ab.
    private func updateNotifications(enabled: Bool) async {
        if enabled {
            let granted = await NotificationService.requestAuthorization()
            guard granted else {
                NotificationService.setEnabled(false)
                notificationsEnabled = false
                showingNotificationDeniedAlert = true
                return
            }
            NotificationService.setEnabled(true)
            BackgroundGradeRefresher.schedule()
        } else {
            NotificationService.setEnabled(false)
            BackgroundGradeRefresher.cancel()
        }
    }

    private func save() {
        guard let existing = KeychainStore.load() else { return }
        let newUsername = username
        let newPassword = password.isEmpty ? existing.password : password
        errorMessage = nil
        isSaving = true
        Task {
            do {
                let gradeTable = try await QISClient().fetchGrades(
                    username: newUsername, password: newPassword, allowSessionReuse: false
                )
                KeychainStore.save(Credentials(username: newUsername, password: newPassword))
                GradeCache.save(gradeTable)
                isSaving = false
                dismiss()
            } catch {
                isSaving = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
