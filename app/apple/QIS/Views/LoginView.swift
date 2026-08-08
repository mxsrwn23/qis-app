import SwiftUI

struct LoginView: View {
    let onLoginSucceeded: (Credentials, GradeTable) -> Void

    @State private var username = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

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

            VStack(spacing: 12) {
                TextField("Benutzername", text: $username)
                    .textContentType(.username)
                    #if os(iOS)
                    .textInputAutocapitalization(.never)
                    #endif
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                SecureField("Passwort", text: $password)
                    .textContentType(.password)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(login)
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

    private func login() {
        guard !username.isEmpty, !password.isEmpty, !isLoading else { return }
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
