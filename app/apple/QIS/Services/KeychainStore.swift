import Foundation
import Security

enum KeychainStore {
    private static let service = "dev.maxsauerwein.qis.credentials"
    private static let account = "qis"

    static func save(_ credentials: Credentials) {
        let payload = "\(credentials.username)\u{0}\(credentials.password)".data(using: .utf8)!
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = payload
        // Schließt diesen Eintrag von Geräte-/iCloud-Backups aus. Andernfalls wäre das
        // SSO-Passwort aus einem wiederhergestellten (oder kompromittierten) Backup auf einem
        // anderen Gerät lesbar.
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(attributes as CFDictionary, nil)
    }

    static func load() -> Credentials? {
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
              let payload = String(data: data, encoding: .utf8) else {
            return nil
        }
        let parts = payload.split(separator: "\u{0}", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2 else { return nil }
        return Credentials(username: String(parts[0]), password: String(parts[1]))
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
