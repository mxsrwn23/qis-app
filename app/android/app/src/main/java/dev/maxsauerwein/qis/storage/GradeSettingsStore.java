package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Spiegelt die DEFAULT_SETTINGS der Extension (utils/settings.js), soweit sie noch für das
 *  kartenbasierte mobile Layout gelten. hiddenColumns wurde zu visibleAttemptFields, da es keine
 *  Tabelle mehr gibt. */
public final class GradeSettingsStore {

    public enum AverageMode { ALL, LAST, BEST }

    public static final String[] ATTEMPT_FIELDS = {"semester", "note", "versuch", "datum"};

    public static final class Settings {
        public AverageMode averageMode = AverageMode.ALL;
        public boolean hideStudienleistungen = false;
        public Set<String> visibleAttemptFields = new HashSet<>(Arrays.asList(ATTEMPT_FIELDS));
        public Map<String, String> customColors = new HashMap<>();
        public String studiengang = "";
        public int semester = 0;
        /** 0 bedeutet "automatisch". Fällt auf effectiveTargetEcts() basierend auf degreeType
         *  zurück, bis der Nutzer einen expliziten Wert einträgt. */
        public int targetEcts = 0;
        /** "bachelor" / "master" / "" (unbekannt), wird zusammen mit studiengang gesetzt, wenn
         *  automatisch aus der Stammdaten-Tabelle erkannt. Steuert den Bachelor-/Master-Standardwert
         *  für effectiveTargetEcts(). */
        public String degreeType = "";

        public int effectiveTargetEcts() {
            if (targetEcts > 0) {
                return targetEcts;
            }
            return "master".equals(degreeType) ? 120 : 180;
        }
    }

    private static final String PREFS_NAME = "qis_grade_settings";
    private static final String KEY_AVERAGE_MODE = "average_mode";
    private static final String KEY_HIDE_STUDIENLEISTUNGEN = "hide_studienleistungen";
    private static final String KEY_VISIBLE_FIELDS = "visible_attempt_fields";
    private static final String KEY_CUSTOM_COLORS = "custom_colors";
    private static final String KEY_STUDIENGANG = "studiengang";
    private static final String KEY_SEMESTER = "semester";
    private static final String KEY_TARGET_ECTS = "target_ects";
    private static final String KEY_DEGREE_TYPE = "degree_type";

    private final SharedPreferences prefs;

    public GradeSettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Settings load() {
        Settings settings = new Settings();
        String modeName = prefs.getString(KEY_AVERAGE_MODE, AverageMode.ALL.name());
        try {
            settings.averageMode = AverageMode.valueOf(modeName);
        } catch (IllegalArgumentException ignored) {
            settings.averageMode = AverageMode.ALL;
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
        settings.studiengang = prefs.getString(KEY_STUDIENGANG, "");
        settings.semester = prefs.getInt(KEY_SEMESTER, 0);
        settings.targetEcts = prefs.getInt(KEY_TARGET_ECTS, 0);
        settings.degreeType = prefs.getString(KEY_DEGREE_TYPE, "");
        return settings;
    }

    public void save(Settings settings) {
        JSONObject colorsJson = new JSONObject(settings.customColors);
        prefs.edit()
                .putString(KEY_AVERAGE_MODE, settings.averageMode.name())
                .putBoolean(KEY_HIDE_STUDIENLEISTUNGEN, settings.hideStudienleistungen)
                .putStringSet(KEY_VISIBLE_FIELDS, settings.visibleAttemptFields)
                .putString(KEY_CUSTOM_COLORS, colorsJson.toString())
                .putString(KEY_STUDIENGANG, settings.studiengang)
                .putInt(KEY_SEMESTER, settings.semester)
                .putInt(KEY_TARGET_ECTS, settings.targetEcts)
                .putString(KEY_DEGREE_TYPE, settings.degreeType)
                .apply();
    }
}
