package dev.maxsauerwein.qis.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.storage.SessionCookieStore;
import dev.maxsauerwein.qis.util.QISLabels;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Portiert den Login- und Notenspiegel-Scrape-Ablauf von qis-unlocked.py nach Java. */
public final class QISClient {

    public interface Callback {
        void onSuccess(List<GradeTable> gradeTables);
        void onError(Exception error);
    }

    public static final class QISException extends Exception {
        public QISException(String message) {
            super(message);
        }
    }

    private static final String BASE = "https://qis.hochschule-trier.de/qisserver/rds";
    private static final String MENU_URL = "https://qis.hochschule-trier.de/qisserver/rds?state=change&type=1&moduleParameter=studyPOSMenu&nextdir=change&next=menu.vm&subdir=applications&xml=menu&purge=y&navigationPosition=functions%2CstudyPOSMenu&breadcrumb=studyPOSMenu&topitem=functions&subitem=studyPOSMenu";

    private final OkHttpClient httpClient;
    private final InMemoryCookieJar cookieJar;
    private final SessionCookieStore sessionCookieStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public QISClient(Context context) {
        cookieJar = new InMemoryCookieJar();
        sessionCookieStore = new SessionCookieStore(context.getApplicationContext());
        httpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /** Speichert Cookies im Arbeitsspeicher für die Lebensdauer dieses Clients, analog zu requests.Session. */
    private static final class InMemoryCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            List<Cookie> existing = cookieStore.computeIfAbsent(url.host(), key -> new ArrayList<>());
            for (Cookie cookie : cookies) {
                existing.removeIf(c -> c.name().equals(cookie.name()));
                existing.add(cookie);
            }
        }

        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = cookieStore.get(url.host());
            if (cookies == null) {
                return new ArrayList<>();
            }
            List<Cookie> valid = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Cookie cookie : cookies) {
                if (cookie.expiresAt() > now) {
                    valid.add(cookie);
                }
            }
            return valid;
        }

        /** Seedet zuvor gespeicherte Cookies (z. B. aus SessionCookieStore) für einen Host, bevor
         *  ein Request gestellt wird -- ermöglicht die Wiederverwendung einer Session ohne
         *  erneuten Login. */
        synchronized void seed(HttpUrl url, List<Cookie> cookies) {
            saveFromResponse(url, cookies);
        }

        /** Entfernt alle für einen Host gespeicherten Cookies. */
        synchronized void clear(HttpUrl url) {
            cookieStore.remove(url.host());
        }
    }

    /** Holt den Notenspiegel für den Callback-Aufrufer (Haupt-Thread). Siehe
     *  {@link #fetchGradesBlocking(String, String, boolean)} für die Semantik von
     *  allowSessionReuse. */
    public void fetchGrades(String username, String password, boolean allowSessionReuse, Callback callback) {
        executor.execute(() -> {
            try {
                List<GradeTable> gradeTables = fetchGradesBlocking(username, password, allowSessionReuse);
                mainHandler.post(() -> callback.onSuccess(gradeTables));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /** Synchrone Variante für Aufrufer, die bereits nicht auf dem Haupt-Thread laufen.
     *
     *  Mit {@code allowSessionReuse: true} (Normalfall, z. B. Pull-to-refresh) wird zuerst
     *  versucht, eine gespeicherte QIS-Session wiederzuverwenden, statt den vollen SAML-Login
     *  erneut zu durchlaufen -- nur bei abgelaufener/ungültiger Session (Redirect zurück zum IdP
     *  oder fehlende Daten, erkennbar an einer QISException) folgt ein einmaliger Fallback auf
     *  den vollen Login. Reine Netzwerkfehler (IOException) lösen dagegen keinen zweiten Versuch
     *  aus. Mit {@code allowSessionReuse: false} (z. B. beim expliziten Testen neuer Zugangsdaten
     *  in den Einstellungen) wird immer vollständig neu angemeldet. */
    public List<GradeTable> fetchGradesBlocking(String username, String password, boolean allowSessionReuse)
            throws IOException, QISException {
        if (allowSessionReuse) {
            HttpUrl baseUrl = HttpUrl.parse(BASE);
            List<Cookie> stored = sessionCookieStore.load(username, baseUrl);
            if (stored != null) {
                cookieJar.seed(baseUrl, stored);
                try {
                    return fetchGradeTables();
                } catch (QISException sessionInvalid) {
                    // Session abgelaufen oder Seite hat nicht die erwartete Struktur (z. B.
                    // Redirect zurück zum IdP): einmaliger Fallback auf den vollen Login unten.
                    // Die ungültige Cookie muss vorher entfernt werden, sonst schickt der erste
                    // Request der Login-Seite die abgelaufene JSESSIONID/_shibsession_-Cookie mit
                    // und QIS liefert nicht die erwartete leere Login-Seite.
                    cookieJar.clear(baseUrl);
                }
            }
        }
        login(username, password);
        saveCurrentSession(username);
        return fetchGradeTables();
    }

    private void saveCurrentSession(String username) {
        HttpUrl baseUrl = HttpUrl.parse(BASE);
        List<Cookie> cookies = cookieJar.loadForRequest(baseUrl);
        if (!cookies.isEmpty()) {
            sessionCookieStore.save(username, cookies);
        }
    }

    /** Erkennt das native QIS-Login-Formular (Felder "asdf"/"fdsa"). Die Hochschule hat den
     *  föderierten Shibboleth/SAML-Login (Felder "j_username"/"j_password" + SAML-Redirect-Dance)
     *  inzwischen abgeschaltet -- "state=user&type=0" liefert seither direkt die Portalseite mit
     *  diesem nativen Formular, ohne Redirect zu einem externen IdP. */
    private boolean isLoginPage(Document document) {
        return document.selectFirst("input[name=asdf]") != null;
    }

    // MARK: Anmeldung

    private void login(String username, String password) throws IOException, QISException {
        HttpUrl loginPageUrl = HttpUrl.parse(BASE).newBuilder()
                .addQueryParameter("state", "user")
                .addQueryParameter("type", "0")
                .build();
        Response loginPageResponse = execute(new Request.Builder().url(loginPageUrl).get().build());
        String landedUrl = loginPageResponse.request().url().toString();
        Document loginDoc = Jsoup.parse(loginPageResponse.body().string(), landedUrl);
        loginPageResponse.close();

        if (!isLoginPage(loginDoc)) {
            throw new QISException("QIS hat nicht zur erwarteten Login-Seite weitergeleitet.");
        }

        HttpUrl qisLoginUrl = HttpUrl.parse(BASE).newBuilder()
                .addQueryParameter("state", "user")
                .addQueryParameter("type", "1")
                .addQueryParameter("category", "auth.login")
                .addQueryParameter("startpage", "portal.vm")
                .addQueryParameter("breadCrumbSource", "portal")
                .build();
        FormBody qisLoginBody = new FormBody.Builder()
                .add("asdf", username)
                .add("fdsa", password)
                .add("submit", "Anmelden")
                .build();
        Response finalResponse = execute(new Request.Builder().url(qisLoginUrl).post(qisLoginBody).build());
        String finalUrl = finalResponse.request().url().toString();
        String finalHtml = finalResponse.body().string();
        finalResponse.close();
        if (!finalUrl.contains("menu.browse") && !finalHtml.contains("Abmelden")) {
            throw new QISException("Zugangsdaten wurden nicht akzeptiert.");
        }
    }

    // MARK: Notenspiegel-Navigation (qis-unlocked.py: TEIL 3)

    /** Ermittelt alle tatsächlich im Studiengangs-Baum des Nutzers vorhandenen (Abschluss, Fach)-
     *  Kombinationen und lädt für jede davon ihre eigene Notenliste. Es wird nie geraten oder
     *  hartkodiert, welcher Abschluss/Studiengang "der richtige" ist -- hat der Nutzer nur eine
     *  Kombination, liefert diese Methode genau ein Element; hat er mehrere (z. B. zwei
     *  Abschlüsse, oder ein Abschluss mit zwei Fachrichtungen), liefert sie alle, damit die
     *  Auswahl darunter dem Nutzer überlassen werden kann (siehe GradeSettingsStore). */
    private List<GradeTable> fetchGradeTables() throws IOException, QISException {
        Document menuDoc = getDocument(MENU_URL);
        if (isLoginPage(menuDoc)) {
            throw new QISException("Sitzung abgelaufen.");
        }

        String notenspiegelHref = findHref(menuDoc, "state=notenspiegelStudent.*next=tree\\.vm");
        if (notenspiegelHref == null) {
            throw new QISException("Notenspiegel-Link konnte nicht gefunden werden.");
        }
        Document treeDoc = getDocument(notenspiegelHref);

        // Oberste Baumebene: eine Kategorie pro Abschluss-Typ (z. B. "Abschluss Bachelor of
        // Science", "Abschluss Bachelor of Engineering"). Ein frisch eingeklappter Baum zeigt hier
        // normalerweise genau einen Kandidaten pro tatsächlich vorhandenem Abschluss -- bei
        // Studierenden mit mehreren Abschlüssen (Doppelstudium/Zweitstudium) können es aber auch
        // mehrere sein. Alle werden einzeln expandiert, keiner wird vorab verworfen.
        List<String> topLevelHrefs = findAllHrefs(treeDoc, "struct=auswahlBaum.*expand=0");
        if (topLevelHrefs.isEmpty()) {
            throw new QISException("Studiengangs-Baum konnte nicht aufgeklappt werden.");
        }

        List<String> listHrefs = new ArrayList<>();
        for (String topHref : topLevelHrefs) {
            Document expandedDoc = getDocument(topHref);

            // Die eigentliche Studiengangs-Auswahl (und damit der PO-Versions-Tiebreak pro Fach)
            // wird erst hier verfügbar, sobald diese Seite die einzelnen Studiengänge dieses
            // Abschlusses auflistet. Innerhalb desselben Fachs gewinnt die neueste PO-Version
            // (Schwerpunktwechsel innerhalb derselben Prüfungsordnung); unterschiedliche Fächer
            // bleiben dagegen beide erhalten, statt dass eines dem anderen "zum Opfer fällt".
            listHrefs.addAll(findNewestPerFach(expandedDoc, "next=list\\.vm"));
        }
        if (listHrefs.isEmpty()) {
            throw new QISException("Notenliste konnte nicht gefunden werden.");
        }

        List<GradeTable> tables = new ArrayList<>();
        for (String listHref : listHrefs) {
            Document gradesDoc = getDocument(listHref);
            StudentInfo studentInfo = parseStudentInfo(gradesDoc);
            tables.add(parseGradeTable(gradesDoc, studentInfo.abschluss, studentInfo.fach));
        }
        return tables;
    }

    private static final class StudentInfo {
        final String abschluss;
        final String fach;

        StudentInfo(String abschluss, String fach) {
            this.abschluss = abschluss;
            this.fach = fach;
        }
    }

    /** Liest "(angestrebter) Abschluss" und "Fach" aus der Tabelle "Stammdaten des Studierenden",
     *  die oberhalb der Notentabelle auf derselben bereits geladenen Seite steht. */
    private StudentInfo parseStudentInfo(Document document) {
        String abschluss = null;
        String fach = null;
        for (Element row : document.select("tr")) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th == null || td == null) {
                continue;
            }
            String header = th.text().trim();
            String value = td.text().trim();
            if (header.toLowerCase(Locale.GERMAN).contains("abschluss")) {
                abschluss = value;
            } else if (header.equals("Fach")) {
                fach = value;
            }
        }
        return new StudentInfo(abschluss, fach);
    }

    private String findHref(Document document, String pattern) {
        Pattern regex = Pattern.compile(pattern);
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (regex.matcher(href).find()) {
                return link.absUrl("href");
            }
        }
        return null;
    }

    /** Sammelt alle Hrefs, deren URL auf pattern passt, in Dokumentreihenfolge (ohne Duplikate). */
    private List<String> findAllHrefs(Document document, String pattern) {
        Pattern regex = Pattern.compile(pattern);
        Set<String> seen = new HashSet<>();
        List<String> hrefs = new ArrayList<>();
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (!regex.matcher(href).find()) {
                continue;
            }
            String absHref = link.absUrl("href");
            if (seen.add(absHref)) {
                hrefs.add(absHref);
            }
        }
        return hrefs;
    }

    /**
     * Gruppiert die Studiengangs-Links nach Fach (Linktext ohne den "(PO-Version JJJJ)"-Zusatz)
     * und behält pro Fach nur den Link mit der neuesten PO-Version (bevorzugt bei Gleichstand den
     * zuletzt aufgeführten) -- z. B. bei einem Schwerpunktwechsel innerhalb derselben
     * Prüfungsordnung wie "Informatik" zu "Informatik Schwerpunkt KI", beide unter PO 2024. QIS
     * listet ältere Einschreibungen im Baum zuerst auf, daher würde ein blindes "ersten Treffer
     * nehmen" stillschweigend eine veraltete, unvollständige Modulliste laden statt der aktuellen.
     * Unterschiedliche Fächer (unterschiedlicher Linktext nach Entfernen der PO-Version) werden
     * dagegen nie gegeneinander ausgespielt -- beide bleiben als eigene Kombination erhalten.
     */
    private List<String> findNewestPerFach(Document document, String pattern) {
        Pattern regex = Pattern.compile(pattern);
        Pattern poRegex = Pattern.compile("PO-Version\\s*(\\d{4})");

        List<String> order = new ArrayList<>();
        Map<String, int[]> bestYearByFach = new HashMap<>();
        Map<String, String> bestHrefByFach = new HashMap<>();
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (!regex.matcher(href).find()) {
                continue;
            }

            String text = link.text();
            String fachKey = QISLabels.fachName(text);
            Matcher poMatcher = poRegex.matcher(text);
            int year = poMatcher.find() ? Integer.parseInt(poMatcher.group(1)) : 0;

            int[] existingYear = bestYearByFach.get(fachKey);
            if (existingYear == null) {
                bestYearByFach.put(fachKey, new int[]{year});
                bestHrefByFach.put(fachKey, link.absUrl("href"));
                order.add(fachKey);
            } else if (year >= existingYear[0]) {
                existingYear[0] = year;
                bestHrefByFach.put(fachKey, link.absUrl("href"));
            }
        }

        List<String> result = new ArrayList<>();
        for (String fachKey : order) {
            result.add(bestHrefByFach.get(fachKey));
        }
        return result;
    }

    /**
     * Führt alle passenden {@code <table>}-Elemente auf der Seite zusammen, nicht nur die erste.
     * qis-unlocked.py (und unser erster Port davon) stoppte bei der ersten Tabelle, deren Text
     * "Prüfungstext"/"Note" enthielt. Wenn QIS mehr als eine solche Tabelle rendert (z. B. pro
     * Studiengang oder pro Modulabschnitt), wurde jedes Modul in den späteren Tabellen
     * stillschweigend verworfen.
     */
    private GradeTable parseGradeTable(Document document, String abschluss, String fach) throws QISException {
        List<String> header = null;
        List<List<String>> rows = new ArrayList<>();

        for (Element table : document.select("table")) {
            String text = table.text();
            // Nur "Prüfungstext" (nicht "|| Note"). Die Legenden-Tabellen am Seitenende
            // ("Notengebung", "Notenverbesserung") enthalten "Note" als Teilstring und wurden
            // fälschlich als Zeilen übernommen, sobald jede passende Tabelle (nicht nur die
            // erste) durchsucht wird.
            if (!text.contains("Prüfungstext")) {
                continue;
            }

            Elements allRows = table.select("tr");
            if (allRows.isEmpty()) {
                continue;
            }

            List<String> tableHeader = flatCells(allRows.get(0));
            if (!hasContent(tableHeader)) {
                continue;
            }

            int columnCount = header != null ? header.size() : tableHeader.size();
            if (header == null) {
                header = tableHeader;
            }

            Map<Integer, Integer> rowspanCarry = new HashMap<>();
            for (int i = 1; i < allRows.size(); i++) {
                List<String> values = gridCells(allRows.get(i), columnCount, rowspanCarry);
                if (hasContent(values)) {
                    rows.add(values);
                }
            }
        }

        if (header == null) {
            throw new QISException("Es wurde keine Notentabelle gefunden.");
        }
        return new GradeTable(header, rows, abschluss, fach);
    }

    /**
     * Colspan-bewusste Extraktion für eine einzelne Zeile ohne zeilenübergreifenden Kontext.
     * Wird nur für die Kopfzeile verwendet, deren Zellen nie in Folgezeilen hineinragen.
     */
    private List<String> flatCells(Element row) {
        List<String> values = new ArrayList<>();
        for (Element cell : row.select("> th, > td")) {
            String text = cell.text().trim();
            int span = parseSpan(cell, "colspan");
            values.add(text);
            for (int i = 1; i < span; i++) {
                values.add("");
            }
        }
        return values;
    }

    /**
     * Platziert die Zellen einer Datenzeile in einem Grid fester Breite unter Berücksichtigung
     * von `colspan` und `rowspan` aus vorherigen Zeilen (verfolgt über `rowspanCarry`). Echte
     * QIS-Zeilen teilen sich über rowspan oft eine "Prüfungstext"-Zelle über mehrere
     * Versuchszeilen hinweg. Ohne diese Behandlung fehlt der Zeile für den 2. oder 3. Versuch
     * die erste Zelle komplett, sodass jeder folgende Wert (einschließlich der Note) um eine
     * Spalte nach links verschoben wird und im falschen Feld landet.
     */
    private List<String> gridCells(Element row, int columnCount, Map<Integer, Integer> rowspanCarry) {
        Elements directCells = row.select("> th, > td");
        List<String> values = new ArrayList<>(Collections.nCopies(columnCount, ""));
        int cellIndex = 0;
        int column = 0;

        while (column < columnCount) {
            Integer remaining = rowspanCarry.get(column);
            if (remaining != null && remaining > 0) {
                rowspanCarry.put(column, remaining - 1);
                column++;
                continue;
            }
            if (cellIndex >= directCells.size()) {
                break;
            }
            Element cell = directCells.get(cellIndex);
            cellIndex++;
            String text = cell.text().trim();
            int colspan = parseSpan(cell, "colspan");
            int rowspan = parseSpan(cell, "rowspan");
            for (int offset = 0; offset < colspan && column + offset < columnCount; offset++) {
                values.set(column + offset, offset == 0 ? text : "");
                if (rowspan > 1) {
                    rowspanCarry.put(column + offset, rowspan - 1);
                }
            }
            column += colspan;
        }

        // Sicherheitsnetz: Wenn jede Spalte durch veraltete oder unerwartete Rowspan-Buchführung
        // verschluckt wurde, obwohl diese Zeile eigene Zellen hat, ist unsere Carry-Verfolgung
        // von der echten Tabelle abgedriftet (z. B. ein größerer Rowspan weiter oben als
        // angenommen). Diese Abweichung korrigiert sich nicht von selbst, bis der falsche
        // Carry-Zähler ausläuft, wodurch alle Zeilen dazwischen stillschweigend verloren gehen.
        // Das Zurücksetzen des Carrys und das flache Platzieren der Zellen dieser Zeile verliert
        // die Spaltenausrichtung nur für diese eine Zeile, was weit besser ist, als sie (und alle
        // folgenden Zeilen) komplett zu verlieren.
        if (cellIndex == 0 && !directCells.isEmpty()) {
            rowspanCarry.clear();
            values = flatCells(row);
            while (values.size() < columnCount) {
                values.add("");
            }
            if (values.size() > columnCount) {
                values = new ArrayList<>(values.subList(0, columnCount));
            }
        }

        return values;
    }

    private int parseSpan(Element cell, String attribute) {
        String value = cell.attr(attribute);
        if (value.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean hasContent(List<String> values) {
        for (String value : values) {
            if (!value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // MARK: HTTP-Hilfsfunktionen

    private Document getDocument(String url) throws IOException {
        Response response = execute(new Request.Builder().url(url).get().build());
        String finalUrl = response.request().url().toString();
        Document document = Jsoup.parse(response.body().string(), finalUrl);
        response.close();
        return document;
    }

    private Response execute(Request request) throws IOException {
        return httpClient.newCall(request).execute();
    }
}
