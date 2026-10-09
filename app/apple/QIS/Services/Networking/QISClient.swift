import Foundation
import SwiftSoup

enum QISError: Error, LocalizedError {
    case notOnLoginPage
    case invalidCredentials
    case sessionExpired
    case navigationFailed(String)
    case gradeTableNotFound
    case network(Error)

    var errorDescription: String? {
        switch self {
        case .notOnLoginPage: return "QIS hat nicht zur erwarteten Login-Seite weitergeleitet."
        case .invalidCredentials: return "Zugangsdaten wurden nicht akzeptiert."
        case .sessionExpired: return "Sitzung abgelaufen."
        case .navigationFailed(let step): return "Notenspiegel konnte nicht geladen werden (\(step))."
        case .gradeTableNotFound: return "Es wurde keine Notentabelle gefunden."
        case .network(let error): return error.localizedDescription
        }
    }

    /// Ob dieser Fehler auf eine abgelaufene/ungültige Session hindeutet (Redirect zurück zum
    /// IdP oder fehlende erwartete Daten), statt auf ein Netzwerkproblem. Nur in diesem Fall lohnt
    /// sich ein einmaliger Fallback auf den vollen Login.
    var indicatesInvalidSession: Bool {
        switch self {
        case .sessionExpired, .navigationFailed: return true
        default: return false
        }
    }
}

/// Portiert den Login- und Notenspiegel-Scrape-Ablauf von `qis-unlocked.py` nach Swift.
actor QISClient {
    private static let base = URL(string: "https://qis.hochschule-trier.de/qisserver/rds")!
    private static let menuURL = URL(string: "https://qis.hochschule-trier.de/qisserver/rds?state=change&type=1&moduleParameter=studyPOSMenu&nextdir=change&next=menu.vm&subdir=applications&xml=menu&purge=y&navigationPosition=functions%2CstudyPOSMenu&breadcrumb=studyPOSMenu&topitem=functions&subitem=studyPOSMenu")!

    private let session: URLSession
    private let cookieStorage: HTTPCookieStorage?

    init() {
        // Bewusst beim ephemeren In-Memory-Cookie-Speicher belassen. Ein separat zugewiesener
        // HTTPCookieStorage() an dieser Stelle unterbricht die automatische Cookie-Weitergabe
        // über die QIS-zu-SP-zu-IdP-Weiterleitungskette. Der IdP sieht die Folgeanfrage dann als
        // veraltet an (fehlendes __Host-JSESSIONID) und lehnt sie ab, bevor csrf_token überhaupt
        // existiert.
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) QISApp/1.0",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7"
        ]
        self.session = URLSession(configuration: configuration)
        self.cookieStorage = configuration.httpCookieStorage
    }

    /// Holt den Notenspiegel -- eine Tabelle pro tatsächlich vorhandener (Abschluss, Fach)-
    /// Kombination (meist genau eine). Mit `allowSessionReuse: true` (Normalfall, z. B. Pull-to-refresh)
    /// wird zuerst versucht, eine gespeicherte QIS-Session wiederzuverwenden, statt den vollen
    /// SAML-Login erneut zu durchlaufen -- nur bei abgelaufener/ungültiger Session (Redirect
    /// zurück zum IdP oder fehlende Daten) folgt ein einmaliger Fallback auf den vollen Login.
    /// Reine Netzwerkfehler lösen dagegen keinen zweiten Versuch aus. Mit `allowSessionReuse:
    /// false` (z. B. beim expliziten Testen neuer Zugangsdaten in den Einstellungen) wird immer
    /// vollständig neu angemeldet.
    func fetchGrades(username: String, password: String, allowSessionReuse: Bool = true) async throws -> [GradeTable] {
        DebugLog.log("fetchGrades gestartet (allowSessionReuse: \(allowSessionReuse))")
        if allowSessionReuse, let cookies = SessionCookieStore.load(username: username) {
            DebugLog.log("Gespeicherte Session gefunden (\(cookies.count) Cookies), versuche Wiederverwendung")
            applyCookies(cookies)
            do {
                let tables = try await fetchGradeTables()
                DebugLog.log("Session-Wiederverwendung erfolgreich")
                return tables
            } catch let error as QISError where error.indicatesInvalidSession {
                // Die ungültige Session-Cookie muss vor dem vollen Login entfernt werden, sonst
                // schickt der erste Request der Login-Seite die abgelaufene JSESSIONID/
                // _shibsession_-Cookie mit. QIS liefert dann nicht die erwartete leere
                // Login-Seite, wodurch der Fallback-Login selbst mit "notOnLoginPage" fehlschlägt.
                DebugLog.log("Session ungültig (\(error)), verwerfe Cookies und melde neu an")
                clearCookies()
            }
        }
        try await login(username: username, password: password)
        saveCurrentSession(username: username)
        let tables = try await fetchGradeTables()
        DebugLog.log("fetchGrades abgeschlossen: \(tables.count) Tabelle(n)")
        return tables
    }

    private func applyCookies(_ cookies: [HTTPCookie]) {
        for cookie in cookies {
            cookieStorage?.setCookie(cookie)
        }
    }

    private func clearCookies() {
        guard let cookieStorage, let url = URL(string: "https://qis.hochschule-trier.de") else { return }
        for cookie in cookieStorage.cookies(for: url) ?? [] {
            cookieStorage.deleteCookie(cookie)
        }
    }

    private func saveCurrentSession(username: String) {
        guard let cookieStorage, let url = URL(string: "https://qis.hochschule-trier.de") else { return }
        let cookies = cookieStorage.cookies(for: url) ?? []
        SessionCookieStore.save(username: username, cookies: cookies)
    }

    /// Erkennt das native QIS-Login-Formular (Felder "asdf"/"fdsa"). Die Hochschule hat den
    /// föderierten Shibboleth/SAML-Login (Felder "j_username"/"j_password" + SAML-Redirect-Dance)
    /// inzwischen abgeschaltet -- "state=user&type=0" liefert seither direkt die Portalseite mit
    /// diesem nativen Formular, ohne Redirect zu einem externen IdP.
    private func isLoginPage(_ document: Document) -> Bool {
        (try? document.select("input[name=asdf]").first()) != nil
    }

    // MARK: - Anmeldung

    private func login(username: String, password: String) async throws {
        DebugLog.log("Login: lade Login-Seite")
        let loginPageURL = appendingQuery(Self.base, ["state": "user", "type": "0"])
        let (loginPageData, _) = try await get(loginPageURL)
        let loginDoc = try document(from: loginPageData)
        guard isLoginPage(loginDoc) else {
            DebugLog.log("Login fehlgeschlagen: keine Login-Seite gefunden")
            throw QISError.notOnLoginPage
        }
        DebugLog.log("Login: sende Zugangsdaten")

        let qisLoginURL = appendingQuery(Self.base, [
            "state": "user",
            "type": "1",
            "category": "auth.login",
            "startpage": "portal.vm",
            "breadCrumbSource": "portal"
        ])
        let qisLoginBody = formURLEncoded(["asdf": username, "fdsa": password, "submit": "Anmelden"])
        let (finalData, finalResponse) = try await post(qisLoginURL, body: qisLoginBody)
        let finalHTML = String(data: finalData, encoding: .utf8) ?? ""
        let finalURLString = finalResponse.url?.absoluteString ?? ""
        guard finalURLString.contains("menu.browse") || finalHTML.contains("Abmelden") else {
            DebugLog.log("Login fehlgeschlagen: Zugangsdaten nicht akzeptiert (URL: \(finalURLString))")
            throw QISError.invalidCredentials
        }
        DebugLog.log("Login erfolgreich")
    }

    // MARK: - Notenspiegel-Navigation (qis-unlocked.py: TEIL 3)

    /// Ermittelt alle tatsächlich im Studiengangs-Baum des Nutzers vorhandenen (Abschluss, Fach)-
    /// Kombinationen und lädt für jede davon ihre eigene Notenliste. Es wird nie geraten oder
    /// hartkodiert, welcher Abschluss/Studiengang "der richtige" ist -- hat der Nutzer nur eine
    /// Kombination, liefert diese Funktion genau ein Element; hat er mehrere (z. B. zwei
    /// Abschlüsse, oder ein Abschluss mit zwei Fachrichtungen), liefert sie alle, damit die
    /// Auswahl darunter dem Nutzer überlassen werden kann (siehe `GradeSettings`).
    private func fetchGradeTables() async throws -> [GradeTable] {
        let menuDoc = try await getDocument(Self.menuURL)
        guard !isLoginPage(menuDoc) else {
            DebugLog.log("fetchGradeTables fehlgeschlagen: Session abgelaufen (Menü zeigt Login-Seite)")
            throw QISError.sessionExpired
        }

        guard let notenspiegelHref = try findHref(in: menuDoc, matching: "state=notenspiegelStudent.*next=tree\\.vm"),
              let notenspiegelURL = URL(string: notenspiegelHref, relativeTo: Self.menuURL)?.absoluteURL else {
            DebugLog.log("fetchGradeTables fehlgeschlagen: Notenspiegel-Link nicht im Menü gefunden")
            throw QISError.navigationFailed("Notenspiegel-Link")
        }
        let treeDoc = try await getDocument(notenspiegelURL)

        // Oberste Baumebene: eine Kategorie pro Abschluss-Typ (z. B. "Abschluss Bachelor of
        // Science", "Abschluss Bachelor of Engineering"). Ein frisch eingeklappter Baum zeigt hier
        // normalerweise genau einen Kandidaten pro tatsächlich vorhandenem Abschluss -- bei
        // Studierenden mit mehreren Abschlüssen (Doppelstudium/Zweitstudium) können es aber auch
        // mehrere sein. Alle werden einzeln expandiert, keiner wird vorab verworfen.
        let topLevelHrefs = try findAllHrefs(in: treeDoc, matching: "struct=auswahlBaum.*expand=0")
        guard !topLevelHrefs.isEmpty else {
            DebugLog.log("fetchGradeTables fehlgeschlagen: Studiengangs-Baum ist leer")
            throw QISError.navigationFailed("Studiengangs-Baum")
        }
        DebugLog.log("Studiengangs-Baum: \(topLevelHrefs.count) Abschluss-Kandidat(en)")

        var listURLs: [URL] = []
        for topHref in topLevelHrefs {
            guard let expandURL = URL(string: topHref, relativeTo: notenspiegelURL)?.absoluteURL else { continue }
            let expandedDoc = try await getDocument(expandURL)

            // Die eigentliche Studiengangs-Auswahl (und damit der PO-Versions-Tiebreak pro Fach)
            // wird erst hier verfügbar, sobald diese Seite die einzelnen Studiengänge dieses
            // Abschlusses auflistet. Innerhalb desselben Fachs gewinnt die neueste PO-Version
            // (Schwerpunktwechsel innerhalb derselben Prüfungsordnung); unterschiedliche Fächer
            // bleiben dagegen beide erhalten, statt dass eines dem anderen "zum Opfer fällt".
            let fachURLs = try findNewestPerFach(in: expandedDoc, matching: "next=list\\.vm", relativeTo: expandURL)
            listURLs.append(contentsOf: fachURLs)
        }
        guard !listURLs.isEmpty else {
            DebugLog.log("fetchGradeTables fehlgeschlagen: keine Notenliste gefunden")
            throw QISError.navigationFailed("Notenliste")
        }
        DebugLog.log("Notenlisten: \(listURLs.count) Fach-Kombination(en) gefunden")

        var tables: [GradeTable] = []
        for listURL in listURLs {
            let gradesDoc = try await getDocument(listURL)
            var gradeTable = try parseGradeTable(from: gradesDoc)
            let studentInfo = try parseStudentInfo(from: gradesDoc)
            gradeTable.abschluss = studentInfo.abschluss
            gradeTable.fach = studentInfo.fach
            tables.append(gradeTable)
            DebugLog.log("Tabelle geparst: \(studentInfo.abschluss ?? "?") / \(studentInfo.fach ?? "?"), \(gradeTable.rows.count) Zeile(n)")
        }
        return tables
    }

    /// Liest "(angestrebter) Abschluss" und "Fach" aus der Tabelle "Stammdaten des Studierenden",
    /// die oberhalb der Notentabelle auf derselben bereits geladenen Seite steht.
    private func parseStudentInfo(from document: Document) throws -> (abschluss: String?, fach: String?) {
        var abschluss: String?
        var fach: String?
        for row in try document.select("tr").array() {
            guard let th = try row.select("th").first(), let td = try row.select("td").first() else { continue }
            let header = try th.text().trimmingCharacters(in: .whitespaces)
            let value = try td.text().trimmingCharacters(in: .whitespaces)
            if header.localizedCaseInsensitiveContains("abschluss") {
                abschluss = value
            } else if header == "Fach" {
                fach = value
            }
        }
        return (abschluss, fach)
    }

    private func findHref(in document: Document, matching pattern: String) throws -> String? {
        let regex = try NSRegularExpression(pattern: pattern)
        for link in try document.select("a[href]").array() {
            let href = try link.attr("href")
            let range = NSRange(href.startIndex..., in: href)
            if regex.firstMatch(in: href, range: range) != nil {
                return href
            }
        }
        return nil
    }

    /// Sammelt alle Hrefs, deren URL auf `pattern` passt, in Dokumentreihenfolge (ohne Duplikate).
    private func findAllHrefs(in document: Document, matching pattern: String) throws -> [String] {
        let regex = try NSRegularExpression(pattern: pattern)
        var seen: Set<String> = []
        var hrefs: [String] = []
        for link in try document.select("a[href]").array() {
            let href = try link.attr("href")
            let range = NSRange(href.startIndex..., in: href)
            guard regex.firstMatch(in: href, range: range) != nil, seen.insert(href).inserted else { continue }
            hrefs.append(href)
        }
        return hrefs
    }

    /// Gruppiert die Studiengangs-Links nach Fach (Linktext ohne den "(PO-Version JJJJ)"-Zusatz)
    /// und behält pro Fach nur den Link mit der neuesten PO-Version (bevorzugt bei Gleichstand den
    /// zuletzt aufgeführten) -- z. B. bei einem Schwerpunktwechsel innerhalb derselben
    /// Prüfungsordnung wie "Informatik" zu "Informatik Schwerpunkt KI", beide unter PO 2024. QIS
    /// listet ältere Einschreibungen im Baum zuerst auf, daher würde ein blindes "ersten Treffer
    /// nehmen" stillschweigend eine veraltete, unvollständige Modulliste laden statt der aktuellen.
    /// Unterschiedliche Fächer (unterschiedlicher Linktext nach Entfernen der PO-Version) werden
    /// dagegen nie gegeneinander ausgespielt -- beide bleiben als eigene Kombination erhalten.
    private func findNewestPerFach(in document: Document, matching pattern: String, relativeTo base: URL) throws -> [URL] {
        let regex = try NSRegularExpression(pattern: pattern)
        let poRegex = try NSRegularExpression(pattern: "PO-Version\\s*(\\d{4})")

        var order: [String] = []
        var bestByFach: [String: (year: Int, href: String)] = [:]
        for link in try document.select("a[href]").array() {
            let href = try link.attr("href")
            let hrefRange = NSRange(href.startIndex..., in: href)
            guard regex.firstMatch(in: href, range: hrefRange) != nil else { continue }

            let text = try link.text()
            let fachKey = QISLabels.fachName(for: text)
            let textRange = NSRange(text.startIndex..., in: text)
            let year = poRegex.firstMatch(in: text, range: textRange).flatMap { match -> Int? in
                guard let yearRange = Range(match.range(at: 1), in: text) else { return nil }
                return Int(text[yearRange])
            } ?? 0

            if let existing = bestByFach[fachKey] {
                if year >= existing.year {
                    bestByFach[fachKey] = (year, href)
                }
            } else {
                bestByFach[fachKey] = (year, href)
                order.append(fachKey)
            }
        }
        return order.compactMap { fachKey in
            bestByFach[fachKey].flatMap { URL(string: $0.href, relativeTo: base)?.absoluteURL }
        }
    }

    /// Führt alle passenden `<table>`-Elemente auf der Seite zusammen, nicht nur die erste.
    /// qis-unlocked.py (und unser erster Port davon) stoppte bei der ersten Tabelle, deren Text
    /// "Prüfungstext"/"Note" enthielt. Wenn QIS mehr als eine solche Tabelle rendert (z. B. pro
    /// Studiengang oder pro Modulabschnitt), wurde jedes Modul in den späteren Tabellen
    /// stillschweigend verworfen.
    private func parseGradeTable(from document: Document) throws -> GradeTable {
        var header: [String]?
        var rows: [[String]] = []

        for table in try document.select("table").array() {
            let text = try table.text()
            // Nur "Prüfungstext" (nicht "|| Note"). Die Legenden-Tabellen am Seitenende
            // ("Notengebung", "Notenverbesserung") enthalten "Note" als Teilstring und wurden
            // fälschlich als Zeilen übernommen, sobald jede passende Tabelle (nicht nur die
            // erste) durchsucht wird.
            guard text.contains("Prüfungstext") else { continue }

            let allRows = try table.select("tr").array()
            guard let headerRow = allRows.first else { continue }
            let tableHeader = try flatCells(in: headerRow)
            guard tableHeader.contains(where: { !$0.isEmpty }) else { continue }

            let columnCount = header?.count ?? tableHeader.count
            if header == nil {
                header = tableHeader
            }

            var rowspanCarry: [Int: Int] = [:]
            for rawRow in allRows.dropFirst() {
                let values = try gridCells(in: rawRow, columnCount: columnCount, rowspanCarry: &rowspanCarry)
                if values.contains(where: { !$0.isEmpty }) {
                    rows.append(values)
                }
            }
        }

        guard let header else {
            throw QISError.gradeTableNotFound
        }
        return GradeTable(header: header, rows: rows)
    }

    /// Colspan-bewusste Extraktion für eine einzelne Zeile ohne zeilenübergreifenden Kontext.
    /// Wird nur für die Kopfzeile verwendet, deren Zellen nie in Folgezeilen hineinragen.
    private func flatCells(in row: Element) throws -> [String] {
        var values: [String] = []
        // Der Selector-Parser von SwiftSoup unterstützt im Gegensatz zu Jsoup keinen führenden
        // Kombinator wie "> td". Daher werden direkte Kindelemente manuell gefiltert statt über
        // row.select("> th, > td").
        let directCells = try row.children().filter { $0.tagName() == "td" || $0.tagName() == "th" }
        for cell in directCells {
            let text = try cell.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let span = max(1, Int(try cell.attr("colspan")) ?? 1)
            values.append(text)
            for _ in 1..<span {
                values.append("")
            }
        }
        return values
    }

    /// Platziert die Zellen einer Datenzeile in einem Grid fester Breite unter Berücksichtigung
    /// von `colspan` und `rowspan` aus vorherigen Zeilen (verfolgt über `rowspanCarry`). Echte
    /// QIS-Zeilen teilen sich über rowspan oft eine "Prüfungstext"-Zelle über mehrere
    /// Versuchszeilen hinweg. Ohne diese Behandlung fehlt der Zeile für den 2. oder 3. Versuch
    /// die erste Zelle komplett, sodass jeder folgende Wert (einschließlich der Note) um eine
    /// Spalte nach links verschoben wird und im falschen Feld landet.
    private func gridCells(in row: Element, columnCount: Int, rowspanCarry: inout [Int: Int]) throws -> [String] {
        let directCells = try row.children().filter { $0.tagName() == "td" || $0.tagName() == "th" }
        var values = Array(repeating: "", count: columnCount)
        var cellIndex = 0
        var column = 0

        while column < columnCount {
            if let remaining = rowspanCarry[column], remaining > 0 {
                rowspanCarry[column] = remaining - 1
                column += 1
                continue
            }
            guard cellIndex < directCells.count else { break }
            let cell = directCells[cellIndex]
            cellIndex += 1
            let text = try cell.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let colspan = max(1, Int(try cell.attr("colspan")) ?? 1)
            let rowspan = max(1, Int(try cell.attr("rowspan")) ?? 1)
            for offset in 0..<colspan where column + offset < columnCount {
                values[column + offset] = offset == 0 ? text : ""
                if rowspan > 1 {
                    rowspanCarry[column + offset] = rowspan - 1
                }
            }
            column += colspan
        }

        // Sicherheitsnetz: Wenn jede Spalte durch veraltete oder unerwartete Rowspan-Buchführung
        // verschluckt wurde, obwohl diese Zeile eigene Zellen hat, ist unsere Carry-Verfolgung
        // von der echten Tabelle abgedriftet (z. B. ein größerer Rowspan weiter oben als
        // angenommen). Diese Abweichung korrigiert sich nicht von selbst, bis der falsche
        // Carry-Zähler ausläuft, wodurch alle Zeilen dazwischen stillschweigend verloren gehen.
        // Das Zurücksetzen des Carrys und das flache Platzieren der Zellen dieser Zeile verliert
        // die Spaltenausrichtung nur für diese eine Zeile, was weit besser ist, als sie (und alle
        // folgenden Zeilen) komplett zu verlieren.
        if cellIndex == 0 && !directCells.isEmpty {
            rowspanCarry.removeAll()
            values = try flatCells(in: row)
            if values.count < columnCount {
                values.append(contentsOf: Array(repeating: "", count: columnCount - values.count))
            } else if values.count > columnCount {
                values = Array(values.prefix(columnCount))
            }
        }

        return values
    }

    // MARK: - HTTP-Hilfsfunktionen

    private func get(_ url: URL) async throws -> (Data, HTTPURLResponse) {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        return try await perform(request)
    }

    private func getDocument(_ url: URL) async throws -> Document {
        let (data, _) = try await get(url)
        return try document(from: data)
    }

    private func post(_ url: URL, body: Data) async throws -> (Data, HTTPURLResponse) {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        return try await perform(request)
    }

    private func perform(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let method = request.httpMethod ?? "GET"
        let urlString = request.url?.absoluteString ?? "?"
        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                DebugLog.log("\(method) \(urlString) -> keine HTTP-Antwort")
                throw QISError.network(URLError(.badServerResponse))
            }
            DebugLog.log("\(method) \(urlString) -> \(httpResponse.statusCode)")
            return (data, httpResponse)
        } catch let error as QISError {
            throw error
        } catch {
            DebugLog.log("\(method) \(urlString) -> Fehler: \(error.localizedDescription)")
            throw QISError.network(error)
        }
    }

    private func document(from data: Data) throws -> Document {
        let html = String(data: data, encoding: .utf8) ?? ""
        return try SwiftSoup.parse(html)
    }

    private func appendingQuery(_ url: URL, _ params: [String: String]) -> URL {
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        components.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        return components.url!
    }

    private func formURLEncoded(_ params: [String: String]) -> Data {
        let allowed = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "+&="))
        let pairs = params.map { key, value -> String in
            let encodedKey = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
            let encodedValue = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
            return "\(encodedKey)=\(encodedValue)"
        }
        return pairs.joined(separator: "&").data(using: .utf8)!
    }
}
