package dev.maxsauerwein.qis.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Gemeinsame Hilfsfunktionen, um die rohen QIS-Stammdaten-Strings ("Abschluss", "Fach") in
 *  kurze, menschenlesbare Labels zu übersetzen. Wird sowohl für die automatisch erkannte
 *  Studiengangs-Kopfzeile als auch für die Auswahlmöglichkeiten im Profil verwendet, damit beide
 *  Stellen exakt dieselbe Normalisierung anwenden. */
public final class QISLabels {

    private QISLabels() {
    }

    private static final Map<String, String> DEGREE_ABBREVIATIONS = new HashMap<>();
    static {
        DEGREE_ABBREVIATIONS.put("Bachelor of Science", "B.Sc.");
        DEGREE_ABBREVIATIONS.put("Master of Science", "M.Sc.");
        DEGREE_ABBREVIATIONS.put("Bachelor of Arts", "B.A.");
        DEGREE_ABBREVIATIONS.put("Master of Arts", "M.A.");
        DEGREE_ABBREVIATIONS.put("Bachelor of Engineering", "B.Eng.");
        DEGREE_ABBREVIATIONS.put("Master of Engineering", "M.Eng.");
    }

    public static String degreeAbbreviation(String abschluss) {
        String normalized = abschluss.trim();
        String abbreviation = DEGREE_ABBREVIATIONS.get(normalized);
        return abbreviation != null ? abbreviation : normalized;
    }

    /** Ausgeschriebener Abschluss-Name (z. B. "Bachelor of Science"), für Stellen mit genug Platz
     *  wie die Profil-Verwaltung -- im Gegensatz zur Kopfzeile, die aus Platzgründen die
     *  Abkürzung verwendet. */
    public static String degreeFullName(String abschluss) {
        return abschluss.trim();
    }

    public static String degreeType(String abschluss) {
        return abschluss.toLowerCase(Locale.GERMAN).contains("master") ? "master" : "bachelor";
    }

    /** Entfernt den "(PO-Version JJJJ)"-Zusatz aus dem rohen Fach-Text, für die Anzeige. */
    public static String fachName(String fach) {
        return fach.replaceAll("\\s*\\(PO-Version\\s*\\d{4}\\)", "").trim();
    }
}
