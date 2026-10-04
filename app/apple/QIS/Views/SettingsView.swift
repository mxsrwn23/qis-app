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

                Section {
                    degreeTypeRow
                    fachRow
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
                } header: {
                    Text("Profil")
                } footer: {
                    if gradeSettings.distinctAbschlussOptions.count > 1 || gradeSettings.fachOptions(forAbschluss: gradeSettings.selectedAbschluss).count > 1 {
                        Text("QIS meldet mehrere Möglichkeiten für deinen Account. Wähle aus, welche Noten angezeigt werden sollen.")
                    }
                }

                Section {
                    Toggle("Neue Noten melden", isOn: $notificationsEnabled)
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
            .onChange(of: gradeSettings.selectedAbschluss) { _, newAbschluss in
                // Fachwahl ist an den Abschluss gebunden: beim Wechsel automatisch übernehmen,
                // wenn es nur eine Fachrichtung gibt, sonst zur erneuten Auswahl zurücksetzen.
                let options = gradeSettings.fachOptions(forAbschluss: newAbschluss)
                if !options.contains(gradeSettings.selectedFach) {
                    gradeSettings.selectedFach = options.count == 1 ? options[0] : ""
                }
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

    /// Abschluss-Typ: Picker mit den echten, bei QIS gefundenen Optionen, sobald es mehr als eine
    /// gibt -- sonst reiner Hinweistext (Szenario 1/3), niemals ein frei editierbares Textfeld.
    @ViewBuilder
    private var degreeTypeRow: some View {
        let options = gradeSettings.distinctAbschlussOptions
        if options.count > 1 {
            Picker("Abschluss-Typ", selection: $gradeSettings.selectedAbschluss) {
                ForEach(options, id: \.self) { abschluss in
                    Text(QISLabels.degreeFullName(for: abschluss)).tag(abschluss)
                }
            }
        } else if !gradeSettings.selectedAbschluss.isEmpty {
            LabeledContent("Abschluss-Typ", value: QISLabels.degreeFullName(for: gradeSettings.selectedAbschluss))
        }
    }

    /// Studiengang: Picker mit den Fachrichtungen des gewählten Abschlusses, sobald es mehr als
    /// eine gibt -- sonst reiner Hinweistext, ebenfalls strikt aus den echten QIS-Daten.
    @ViewBuilder
    private var fachRow: some View {
        let options = gradeSettings.fachOptions(forAbschluss: gradeSettings.selectedAbschluss)
        if options.count > 1 {
            Picker("Studiengang", selection: $gradeSettings.selectedFach) {
                ForEach(options, id: \.self) { fach in
                    Text(QISLabels.fachName(for: fach)).tag(fach)
                }
            }
        } else if !gradeSettings.selectedFach.isEmpty {
            LabeledContent("Studiengang", value: QISLabels.fachName(for: gradeSettings.selectedFach))
        }
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
                let gradeTables = try await QISClient().fetchGrades(
                    username: newUsername, password: newPassword, allowSessionReuse: false
                )
                KeychainStore.save(Credentials(username: newUsername, password: newPassword))
                GradeCache.save(gradeTables)
                // Neu anmelden kann (z. B. bei einem Accountwechsel) andere Abschluss-/Fach-
                // Kombinationen zutage fördern -- availableDegreeOptions und die Auswahl müssen
                // daher wie beim Login neu abgeglichen werden, nicht nur die rohe Tabelle.
                gradeSettings = GradeSettings.loadApplyingAutoDetection(from: gradeTables)
                isSaving = false
                dismiss()
            } catch {
                isSaving = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
