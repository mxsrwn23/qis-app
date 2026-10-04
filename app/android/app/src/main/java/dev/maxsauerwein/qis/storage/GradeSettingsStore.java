package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.maxsauerwein.qis.model.DegreeOption;
import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.util.QISLabels;

/** Spiegelt die DEFAULT_SETTINGS der Extension (utils/settings.js), soweit sie noch für das
 *  kartenbasierte mobile Layout gelten. hiddenColumns wurde zu visibleAttemptFields, da es keine
 *  Tabelle mehr gibt. */
public final class GradeSettingsStore {

    public enum AverageMode { ALL, LAST, BEST }

    public static final String[] ATTEMPT_FIELDS = {"semester", "note", "versuch", "datum"};

    public static final class Settings {
        public AverageMode averageMode = AverageMode.BEST;
        public boolean hideStudienleistungen = false;
        public Set<String> visibleAttemptFields = new HashSet<>(Arrays.asList(ATTEMPT_FIELDS));
        public Map<String, String> customColors = new HashMap<>();
        public int semester = 0;
        /** 0 bedeutet "automatisch". Fällt auf effectiveTargetEcts() basierend auf degreeType()
         *  zurück, bis der Nutzer einen expliziten Wert einträgt. */
        public int targetEcts = 0;

        /** Alle beim letzten Login tatsächlich im QIS-Studiengangs-Baum des Nutzers gefundenen
         *  (Abschluss, Fach)-Kombinationen. Niemals hartkodiert -- einzige Quelle für
         *  Auswahlmöglichkeiten in der Profil-Verwaltung und beim Ersteinrichtungs-Dialog. */
        public List<DegreeOption> availableDegreeOptions = new ArrayList<>();
        /** Roh-"Abschluss"-Wert der bestätigten Auswahl (z. B. "Bachelor of Science"). Leer,
         *  solange bei mehreren verfügbaren Abschluss-Typen noch keine Auswahl getroffen wurde. */
        public String selectedAbschluss = "";
        /** Roh-"Fach"-Wert (inkl. PO-Version) der bestätigten Auswahl. Leer, solange bei mehreren
         *  Fachrichtungen innerhalb des gewählten Abschlusses noch keine Auswahl getroffen wurde. */
        public String selectedFach = "";

        /** Name-Wert von GradeCardBuilder.Filter, hier nur als String gehalten, damit storage
         *  nicht von util abhängen muss. */
        public String filter = "ALL";
        /** Name-Wert von GradeCardBuilder.Sort, siehe filter. */
        public String sort = "NONE";

        /** Anzeige-Label aus der bestätigten Auswahl, z. B. "B.Sc. Informatik". Bewusst nicht frei
         *  eintippbar: der Nutzer darf nur sehen und wählen, was QIS tatsächlich für ihn meldet. */
        public String studiengang() {
            StringBuilder builder = new StringBuilder();
            if (!selectedAbschluss.isEmpty()) {
                builder.append(QISLabels.degreeAbbreviation(selectedAbschluss));
            }
            if (!selectedFach.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(QISLabels.fachName(selectedFach));
            }
            return builder.toString();
        }

        /** "bachelor" / "master" / "" (unbekannt), aus selectedAbschluss abgeleitet. Steuert den
         *  Bachelor-/Master-Standardwert für effectiveTargetEcts(). */
        public String degreeType() {
            return selectedAbschluss.isEmpty() ? "" : QISLabels.degreeType(selectedAbschluss);
        }

        public int effectiveTargetEcts() {
            if (targetEcts > 0) {
                return targetEcts;
            }
            return "master".equals(degreeType()) ? 120 : 180;
        }

        /** Alle unterschiedlichen Abschluss-Typen, die availableDegreeOptions enthält, in der
         *  Reihenfolge ihres ersten Auftretens. */
        public List<String> distinctAbschlussOptions() {
            Set<String> seen = new LinkedHashSet<>();
            for (DegreeOption option : availableDegreeOptions) {
                seen.add(option.abschluss);
            }
            return new ArrayList<>(seen);
        }

        /** Alle Fachrichtungen innerhalb eines bestimmten Abschluss-Typs. */
        public List<String> fachOptions(String abschluss) {
            List<String> result = new ArrayList<>();
            if (abschluss == null || abschluss.isEmpty()) {
                return result;
            }
            Set<String> seen = new LinkedHashSet<>();
            for (DegreeOption option : availableDegreeOptions) {
                if (option.abschluss.equals(abschluss) && seen.add(option.fach)) {
                    result.add(option.fach);
                }
            }
            return result;
        }

        /** Ob der Nutzer aus mehreren Abschluss-Typen wählen muss, aber noch keine Wahl getroffen
         *  hat. */
        public boolean needsAbschlussSelection() {
            return distinctAbschlussOptions().size() > 1 && selectedAbschluss.isEmpty();
        }

        /** Ob der Nutzer (nach feststehendem Abschluss) aus mehreren Fachrichtungen wählen muss,
         *  aber noch keine Wahl getroffen hat. */
        public boolean needsFachSelection() {
            if (selectedAbschluss.isEmpty()) {
                return false;
            }
            return fachOptions(selectedAbschluss).size() > 1 && selectedFach.isEmpty();
        }

        /** Ob vor der Notenanzeige noch ein Ersteinrichtungs-Dialog nötig ist. */
        public boolean needsDegreeSetup() {
            return needsAbschlussSelection() || needsFachSelection();
        }

        /** Die Tabelle aus tables, die der bestätigten (oder einzig möglichen) Abschluss-/Fach-
         *  Wahl entspricht. Liefert null, solange needsDegreeSetup() noch zutrifft. */
        public GradeTable activeTable(List<GradeTable> tables) {
            if (tables.size() == 1) {
                return tables.get(0);
            }
            if (selectedAbschluss.isEmpty() || selectedFach.isEmpty()) {
                return null;
            }
            for (GradeTable table : tables) {
                if (selectedAbschluss.equals(table.abschluss) && selectedFach.equals(table.fach)) {
                    return table;
                }
            }
            return null;
        }
    }

    private static final String PREFS_NAME = "qis_grade_settings";
    private static final String KEY_AVERAGE_MODE = "average_mode";
    private static final String KEY_HIDE_STUDIENLEISTUNGEN = "hide_studienleistungen";
    private static final String KEY_VISIBLE_FIELDS = "visible_attempt_fields";
    private static final String KEY_CUSTOM_COLORS = "custom_colors";
    private static final String KEY_SEMESTER = "semester";
    private static final String KEY_TARGET_ECTS = "target_ects";
    private static final String KEY_AVAILABLE_DEGREE_OPTIONS = "available_degree_options";
    private static final String KEY_SELECTED_ABSCHLUSS = "selected_abschluss";
    private static final String KEY_SELECTED_FACH = "selected_fach";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_SORT = "sort";

    private final SharedPreferences prefs;

    public GradeSettingsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Settings load() {
        Settings settings = new Settings();
        String modeName = prefs.getString(KEY_AVERAGE_MODE, AverageMode.BEST.name());
        try {
            settings.averageMode = AverageMode.valueOf(modeName);
        } catch (IllegalArgumentException ignored) {
            settings.averageMode = AverageMode.BEST;
        }
        settings.hideStudienleistungen = prefs.getBoolean(KEY_HIDE_STUDIENLEISTUNGEN, false);
        settings.visibleAttemptFields = new HashSet<>(
                prefs.getStringSet(KEY_VISIBLE_FIELDS, new HashSet<>(Arrays.asList(ATTEMPT_FIELDS))));

        String colorsJson = prefs.getString(KEY_CUSTOM_COLORS, null);
        if (colorsJson != null) {
            try {
                JSONObject obj = new JSONObject(colorsJson);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    settings.customColors.put(key, obj.optString(key));
                }
            } catch (JSONException ignored) {
                // beschädigt oder nicht lesbar, Rückfall auf Standardwerte
            }
        }
        settings.semester = prefs.getInt(KEY_SEMESTER, 0);
        settings.targetEcts = prefs.getInt(KEY_TARGET_ECTS, 0);

        String degreeOptionsJson = prefs.getString(KEY_AVAILABLE_DEGREE_OPTIONS, null);
        if (degreeOptionsJson != null) {
            try {
                JSONArray array = new JSONArray(degreeOptionsJson);
                List<DegreeOption> options = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    options.add(new DegreeOption(obj.getString("abschluss"), obj.getString("fach")));
                }
                settings.availableDegreeOptions = options;
            } catch (JSONException ignored) {
                // beschädigt oder nicht lesbar, Rückfall auf leere Liste
            }
        }
        settings.selectedAbschluss = prefs.getString(KEY_SELECTED_ABSCHLUSS, "");
        settings.selectedFach = prefs.getString(KEY_SELECTED_FACH, "");
        settings.filter = prefs.getString(KEY_FILTER, "ALL");
        settings.sort = prefs.getString(KEY_SORT, "NONE");
        return settings;
    }

    public void save(Settings settings) {
        JSONObject colorsJson = new JSONObject(settings.customColors);
        JSONArray degreeOptionsJson = new JSONArray();
        for (DegreeOption option : settings.availableDegreeOptions) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("abschluss", option.abschluss);
                obj.put("fach", option.fach);
            } catch (JSONException ignored) {
                // sollte bei den hier verwendeten einfachen Typen nicht auftreten
            }
            degreeOptionsJson.put(obj);
        }
        prefs.edit()
                .putString(KEY_AVERAGE_MODE, settings.averageMode.name())
                .putBoolean(KEY_HIDE_STUDIENLEISTUNGEN, settings.hideStudienleistungen)
                .putStringSet(KEY_VISIBLE_FIELDS, settings.visibleAttemptFields)
                .putString(KEY_CUSTOM_COLORS, colorsJson.toString())
                .putInt(KEY_SEMESTER, settings.semester)
                .putInt(KEY_TARGET_ECTS, settings.targetEcts)
                .putString(KEY_AVAILABLE_DEGREE_OPTIONS, degreeOptionsJson.toString())
                .putString(KEY_SELECTED_ABSCHLUSS, settings.selectedAbschluss)
                .putString(KEY_SELECTED_FACH, settings.selectedFach)
                .putString(KEY_FILTER, settings.filter)
                .putString(KEY_SORT, settings.sort)
                .apply();
    }

    /** Lädt die gespeicherten Einstellungen, aktualisiert availableDegreeOptions auf den
     *  tatsächlich beim letzten Login gefundenen Stand und wendet so viel Auto-Erkennung an, wie
     *  sich eindeutig ableiten lässt:
     *  - genau ein Abschluss-Typ -> automatisch übernommen
     *  - genau eine Fachrichtung innerhalb des (automatischen oder gespeicherten) Abschlusses ->
     *    automatisch übernommen
     *  - mehrere Möglichkeiten -> keine Auswahl erzwungen, needsDegreeSetup() wird true, bis der
     *    Nutzer explizit wählt
     *  Eine bereits gespeicherte, unter den aktuellen Optionen aber nicht mehr gültige Auswahl
     *  (z. B. nach Exmatrikulation/Studiengangswechsel) wird zurückgesetzt statt stillschweigend
     *  beibehalten. */
    public static Settings loadApplyingAutoDetection(Context context, List<GradeTable> tables) {
        GradeSettingsStore store = new GradeSettingsStore(context);
        Settings settings = store.load();

        Set<DegreeOption> seen = new LinkedHashSet<>();
        List<DegreeOption> freshOptions = new ArrayList<>();
        for (GradeTable table : tables) {
            if (table.abschluss != null && !table.abschluss.isEmpty()
                    && table.fach != null && !table.fach.isEmpty()) {
                DegreeOption option = new DegreeOption(table.abschluss, table.fach);
                if (seen.add(option)) {
                    freshOptions.add(option);
                }
            }
        }
        if (!freshOptions.isEmpty()) {
            settings.availableDegreeOptions = freshOptions;
        }

        List<String> abschlussOptions = settings.distinctAbschlussOptions();
        if (abschlussOptions.size() == 1) {
            settings.selectedAbschluss = abschlussOptions.get(0);
        } else if (!settings.selectedAbschluss.isEmpty() && !abschlussOptions.contains(settings.selectedAbschluss)) {
            settings.selectedAbschluss = "";
            settings.selectedFach = "";
        }

        if (!settings.selectedAbschluss.isEmpty()) {
            List<String> fachOptions = settings.fachOptions(settings.selectedAbschluss);
            if (fachOptions.size() == 1) {
                settings.selectedFach = fachOptions.get(0);
            } else if (!settings.selectedFach.isEmpty() && !fachOptions.contains(settings.selectedFach)) {
                settings.selectedFach = "";
            }
        } else {
            settings.selectedFach = "";
        }

        store.save(settings);
        return settings;
    }
}
