package dev.maxsauerwein.qis.network;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.maxsauerwein.qis.model.GradeTable;
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
        void onSuccess(GradeTable gradeTable);
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public QISClient() {
        httpClient = new OkHttpClient.Builder()
                .cookieJar(new InMemoryCookieJar())
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
    }

    public void fetchGrades(String username, String password, Callback callback) {
        executor.execute(() -> {
            try {
                login(username, password);
                GradeTable gradeTable = fetchGradeTable();
                mainHandler.post(() -> callback.onSuccess(gradeTable));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // MARK: Anmeldung (qis-unlocked.py: qis_full_login)

    private void login(String username, String password) throws IOException, QISException {
        HttpUrl loginPageUrl = HttpUrl.parse(BASE).newBuilder()
                .addQueryParameter("state", "user")
                .addQueryParameter("type", "0")
                .build();
        Response loginPageResponse = execute(new Request.Builder().url(loginPageUrl).get().build());
        String landedUrl = loginPageResponse.request().url().toString();
        Document loginDoc = Jsoup.parse(loginPageResponse.body().string(), landedUrl);
        loginPageResponse.close();

        if (!landedUrl.contains("execution=e1s1")) {
            throw new QISException("QIS hat nicht zur erwarteten Login-Seite weitergeleitet.");
        }

        Element csrfInput = loginDoc.selectFirst("input[name=csrf_token]");
        if (csrfInput == null) {
            throw new QISException("CSRF-Token konnte nicht gefunden werden.");
        }
        String csrfToken = csrfInput.attr("value");

        FormBody idpBody = new FormBody.Builder()
                .add("csrf_token", csrfToken)
                .add("j_username", username)
                .add("j_password", password)
                .add("_eventId_proceed", "")
                .build();
        Response idpResponse = execute(new Request.Builder().url(landedUrl).post(idpBody).build());
        String idpUrl = idpResponse.request().url().toString();
        Document idpDoc = Jsoup.parse(idpResponse.body().string(), idpUrl);
        idpResponse.close();

        Element relayStateInput = idpDoc.selectFirst("input[name=RelayState]");
        Element samlResponseInput = idpDoc.selectFirst("input[name=SAMLResponse]");
        Element samlForm = idpDoc.selectFirst("form");
        if (relayStateInput == null || samlResponseInput == null || samlForm == null) {
            throw new QISException("Zugangsdaten wurden nicht akzeptiert.");
        }
        String samlActionUrl = samlForm.absUrl("action");

        FormBody samlBody = new FormBody.Builder()
                .add("RelayState", relayStateInput.attr("value"))
                .add("SAMLResponse", samlResponseInput.attr("value"))
                .build();
        execute(new Request.Builder().url(samlActionUrl).post(samlBody).build()).close();

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
            throw new QISException("QIS-Anmeldung ist fehlgeschlagen.");
        }
    }

    // MARK: Notenspiegel-Navigation (qis-unlocked.py: TEIL 3)

    private GradeTable fetchGradeTable() throws IOException, QISException {
        Document menuDoc = getDocument(MENU_URL);

        String notenspiegelHref = findHref(menuDoc, "state=notenspiegelStudent.*next=tree\\.vm");
        if (notenspiegelHref == null) {
            throw new QISException("Notenspiegel-Link konnte nicht gefunden werden.");
        }
        Document treeDoc = getDocument(notenspiegelHref);

        String expandHref = findNewestStudiengangHref(treeDoc, "struct=auswahlBaum.*expand=0");
        if (expandHref == null) {
            throw new QISException("Studiengangs-Baum konnte nicht aufgeklappt werden.");
        }
        Document expandedDoc = getDocument(expandHref);

        // Der Baum einer frischen Session ist meist eingeklappt, daher findet der vorige Schritt
        // immer nur einen "expand=0"-Kandidaten (die übergeordnete Kategorie, z. B. "Abschluss
        // Bachelor of Science"), dessen Ziel wieder eine Baumseite ist. Die eigentliche
        // Studiengangs-Auswahl (und damit der PO-Versions-Tiebreak) wird erst hier verfügbar,
        // sobald diese Seite die einzelnen Studiengänge auflistet.
        String listHref = findNewestStudiengangHref(expandedDoc, "next=list\\.vm");
        if (listHref == null) {
            throw new QISException("Notenliste konnte nicht gefunden werden.");
        }
        Document gradesDoc = getDocument(listHref);

        StudentInfo studentInfo = parseStudentInfo(gradesDoc);
        return parseGradeTable(gradesDoc, studentInfo.abschluss, studentInfo.fach);
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

    /**
     * Wählt den Studiengangs-Link mit der neuesten "PO-Version JJJJ" im Text aus und bevorzugt
     * bei gleichem Jahr den zuletzt aufgeführten Link (z. B. bei einem Schwerpunktwechsel
     * innerhalb derselben Prüfungsordnung wie "Informatik" zu "Informatik Schwerpunkt KI",
     * beide unter PO 2024). QIS listet ältere Einschreibungen im Baum zuerst auf. Dem ersten
     * "expand=0"-Link blind zu folgen lud daher stillschweigend eine veraltete, unvollständige
     * Modulliste statt der aktuellen.
     */
    private String findNewestStudiengangHref(Document document, String pattern) {
        Pattern regex = Pattern.compile(pattern);
        Pattern poRegex = Pattern.compile("PO-Version\\s*(\\d{4})");

        String bestHref = null;
        int bestYear = -1;
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (!regex.matcher(href).find()) {
                continue;
            }
            if (bestHref == null) {
                bestHref = link.absUrl("href");
            }
            Matcher poMatcher = poRegex.matcher(link.text());
            if (poMatcher.find()) {
                int year = Integer.parseInt(poMatcher.group(1));
                if (year >= bestYear) {
                    bestYear = year;
                    bestHref = link.absUrl("href");
                }
            }
        }
        return bestHref;
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
