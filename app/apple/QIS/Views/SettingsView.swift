import SwiftUI

struct SettingsView: View {
    @Binding var gradeSettings: GradeSettings
    let onLogout: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var username = KeychainStore.load()?.username ?? ""
    @State private var password = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

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
                    Button("Abmelden", role: .destructive, action: onLogout)
                }
            }
            .navigationTitle("Einstellungen")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fertig") { dismiss() }
                }
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

    private func save() {
        guard let existing = KeychainStore.load() else { return }
        let newUsername = username
        let newPassword = password.isEmpty ? existing.password : password
        errorMessage = nil
        isSaving = true
        Task {
            do {
                _ = try await QISClient().fetchGrades(username: newUsername, password: newPassword)
                KeychainStore.save(Credentials(username: newUsername, password: newPassword))
                isSaving = false
                dismiss()
            } catch {
                isSaving = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
