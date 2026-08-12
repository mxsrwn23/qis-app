import SwiftUI

struct LoginView: View {
    let onLoginSucceeded: (Credentials, GradeTable) -> Void

    private enum Field {
        case username, password
    }

    @State private var username = ""
    @State private var password = ""
    @State private var isPasswordVisible = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @FocusState private var focusedField: Field?

    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            VStack(spacing: 8) {
                Image("LoginLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 72, height: 72)
                Text("QIS+ Noten")
                    .font(.largeTitle.bold())
                Text("Melde dich mit deinen Hochschul-Zugangsdaten an")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: 14) {
                fieldRow(icon: "person.fill", isFocused: focusedField == .username) {
                    TextField("Benutzername", text: $username)
                        .textContentType(.username)
                        #if os(iOS)
                        .textInputAutocapitalization(.never)
                        #endif
                        .autocorrectionDisabled()
                        .focused($focusedField, equals: .username)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }
                }

                fieldRow(icon: "lock.fill", isFocused: focusedField == .password) {
                    Group {
                        if isPasswordVisible {
                            TextField("Passwort", text: $password)
                        } else {
                            SecureField("Passwort", text: $password)
                        }
                    }
                    .textContentType(.password)
                    #if os(iOS)
                    .textInputAutocapitalization(.never)
                    #endif
                    .autocorrectionDisabled()
                    .focused($focusedField, equals: .password)
                    .submitLabel(.go)
                    .onSubmit(login)

                    Button {
                        isPasswordVisible.toggle()
                    } label: {
                        Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(isPasswordVisible ? "Passwort verbergen" : "Passwort anzeigen")
                }
            }
            .frame(maxWidth: 360)

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 360)
            }

            Button(action: login) {
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: 360)
                } else {
                    Text("Anmelden")
                        .frame(maxWidth: 360)
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(username.isEmpty || password.isEmpty || isLoading)

            Spacer()
            Spacer()
        }
        .padding()
    }

    /// Einheitliche, moderne Eingabezeile: führendes SF-Symbol, das Eingabefeld und optionale
    /// Trailing-Inhalte (z. B. der Passwort-Umschalter), eingebettet in eine gefüllte Kachel mit
    /// dezentem Rahmen, der das aktive Feld hervorhebt.
    private func fieldRow(
        icon: String,
        isFocused: Bool,
        @ViewBuilder content: () -> some View
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(isFocused ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                .frame(width: 22)
            content()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(isFocused ? AnyShapeStyle(.tint) : AnyShapeStyle(.separator), lineWidth: isFocused ? 2 : 1)
        )
        .animation(.easeInOut(duration: 0.15), value: isFocused)
    }

    private func login() {
        guard !username.isEmpty, !password.isEmpty, !isLoading else { return }
        focusedField = nil
        errorMessage = nil
        isLoading = true
        let usernameValue = username
        let passwordValue = password
        Task {
            do {
                let gradeTable = try await QISClient().fetchGrades(
                    username: usernameValue, password: passwordValue, allowSessionReuse: false
                )
                isLoading = false
                onLoginSucceeded(Credentials(username: usernameValue, password: passwordValue), gradeTable)
            } catch {
                isLoading = false
                errorMessage = error.localizedDescription
            }
        }
    }
}
