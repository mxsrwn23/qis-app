package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import dev.maxsauerwein.qis.model.GradeTable;

/** Persistiert den zuletzt erfolgreich abgerufenen Notenspiegel lokal, damit die App das
 *  QIS-Portal nicht bei jedem Start oder Pull-to-refresh neu kontaktieren muss. */
public final class GradeCacheStore {
    /** Ab diesem Alter gilt der Cache als veraltet und ein passiver Abruf (App-Start) holt frisch. */
    public static final long MIN_CACHE_AGE_MILLIS = 15 * 60 * 1000L;
    /** Harte Untergrenze zwischen zwei Abrufversuchen, auch bei bewusstem Force-Refresh. */
    public static final long MIN_RETRY_INTERVAL_MILLIS = 30 * 1000L;

    private static final String PREFS_NAME = "qis_grade_cache";
    private static final String KEY_TABLE = "table";
    private static final String KEY_LAST_FETCH = "last_fetch";
    private static final String KEY_LAST_ATTEMPT = "last_attempt";

    private final SharedPreferences prefs;

    public GradeCacheStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<GradeTable> loadTables() {
        String json = prefs.getString(KEY_TABLE, null);
        if (json == null) {
            return null;
        }
        try {
            JSONArray tablesJson = new JSONArray(json);
            List<GradeTable> tables = new ArrayList<>();
            for (int t = 0; t < tablesJson.length(); t++) {
                JSONObject obj = tablesJson.getJSONObject(t);
                List<String> header = toStringList(obj.getJSONArray("header"));
                JSONArray rowsJson = obj.getJSONArray("rows");
                List<List<String>> rows = new ArrayList<>();
                for (int i = 0; i < rowsJson.length(); i++) {
                    rows.add(toStringList(rowsJson.getJSONArray(i)));
                }
                String abschluss = obj.isNull("abschluss") ? null : obj.getString("abschluss");
                String fach = obj.isNull("fach") ? null : obj.getString("fach");
                tables.add(new GradeTable(header, rows, abschluss, fach));
            }
            return tables;
        } catch (JSONException e) {
            return null;
        }
    }

    public void saveTables(List<GradeTable> tables) {
        try {
            JSONArray tablesJson = new JSONArray();
            for (GradeTable table : tables) {
                JSONObject obj = new JSONObject();
                obj.put("header", new JSONArray(table.header));
                JSONArray rowsJson = new JSONArray();
                for (List<String> row : table.rows) {
                    rowsJson.put(new JSONArray(row));
                }
                obj.put("rows", rowsJson);
                obj.put("abschluss", table.abschluss);
                obj.put("fach", table.fach);
                tablesJson.put(obj);
            }
            prefs.edit()
                    .putString(KEY_TABLE, tablesJson.toString())
                    .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                    .apply();
        } catch (JSONException ignored) {
            // sollte bei den hier verwendeten einfachen Typen nicht auftreten
        }
    }

    private static List<String> toStringList(JSONArray array) throws JSONException {
        List<String> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            list.add(array.isNull(i) ? "" : array.getString(i));
        }
        return list;
    }

    public boolean isFresh() {
        long lastFetch = prefs.getLong(KEY_LAST_FETCH, 0);
        return lastFetch > 0 && System.currentTimeMillis() - lastFetch < MIN_CACHE_AGE_MILLIS;
    }

    public boolean canAttempt() {
        long lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0);
        return lastAttempt == 0 || System.currentTimeMillis() - lastAttempt >= MIN_RETRY_INTERVAL_MILLIS;
    }

    public void recordAttempt() {
        prefs.edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
