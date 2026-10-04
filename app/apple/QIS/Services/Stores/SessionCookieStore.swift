import Foundation

/// Persistiert die QIS-Session-Cookies (JSESSIONID, _shibsession_…) im Keychain, damit
/// Folgeabrufe die bestehende Session wiederverwenden können, statt den vollen SAML-Login
/// erneut durchzuführen. Nie auf einem Server abgelegt, nur lokal im Keychain.
enum SessionCookieStore {
    private static let service = "dev.maxsauerwein.qis.session"
    private static let account = "qis"

    private struct StoredSession: Codable {
        let username: String
        let cookies: [StoredCookie]
    }

    private struct StoredCookie: Codable {
        let name: String
        let value: String
        let domain: String
        let path: String
        let expiresAt: Date?
        let isSecure: Bool

        init(cookie: HTTPCookie) {
            name = cookie.name
            value = cookie.value
            domain = cookie.domain
            path = cookie.path
            expiresAt = cookie.expiresDate
            isSecure = cookie.isSecure
        }

        var httpCookie: HTTPCookie? {
            var properties: [HTTPCookiePropertyKey: Any] = [
                .name: name,
                .value: value,
                .domain: domain,
                .path: path,
                .secure: isSecure
            ]
            if let expiresAt {
                properties[.expires] = expiresAt
            }
            return HTTPCookie(properties: properties)
        }
    }

    static func save(username: String, cookies: [HTTPCookie]) {
        guard !cookies.isEmpty else { return }
        let stored = StoredSession(username: username, cookies: cookies.map(StoredCookie.init))
        guard let data = try? JSONEncoder().encode(stored) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(attributes as CFDictionary, nil)
    }

    /// Liefert die gespeicherten Cookies nur, wenn sie zum angefragten Benutzernamen gehören.
    /// Verhindert, dass beim Testen geänderter Zugangsdaten (Einstellungen) versehentlich die
    /// Session der alten Zugangsdaten wiederverwendet wird.
    static func load(username: String) -> [HTTPCookie]? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let stored = try? JSONDecoder().decode(StoredSession.self, from: data),
              stored.username == username else {
            return nil
        }
        let cookies = stored.cookies.compactMap(\.httpCookie)
        return cookies.isEmpty ? nil : cookies
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
