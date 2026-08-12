package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.maxsauerwein.qis.model.GradeTable;
import dev.maxsauerwein.qis.model.ModuleCardData;
import dev.maxsauerwein.qis.util.GradeCardBuilder;

/** Erkennt Module, deren Note seit dem letzten bekannten Stand von "offen" auf "benotet"
 *  gewechselt ist, damit die Notenliste sie mit einem "Neu"-Badge markieren und automatisch
 *  aufklappen kann. Der Vergleich läuft gegen den zuletzt gespeicherten Stand, der bei jedem
 *  Aufruf aktualisiert wird -- das Badge erscheint also nur einmalig, bis die neue Note einmal
 *  angezeigt wurde. */
public final class SeenGradesStore {

    private static final String PREFS_NAME = "qis_seen_grades";
    private static final String KEY_GRADED_MODULES = "graded_modules";

    private final SharedPreferences prefs;

    public SeenGradesStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> newlyGradedModuleKeys(GradeTable table) {
        Set<String> currentGraded = gradedModuleKeys(table);
        try {
            if (!prefs.contains(KEY_GRADED_MODULES)) {
                // Kein vorheriger Stand vorhanden (erster Aufruf überhaupt): Baseline nur
                // anlegen, nichts als neu markieren.
                return new HashSet<>();
            }
            Set<String> result = new HashSet<>(currentGraded);
            result.removeAll(readSet());
            return result;
        } finally {
            saveSet(currentGraded);
        }
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    private Set<String> gradedModuleKeys(GradeTable table) {
        List<ModuleCardData> cards = GradeCardBuilder.buildCards(table, new GradeSettingsStore.Settings());
        Set<String> keys = new HashSet<>();
        for (ModuleCardData card : cards) {
            if (!card.grade.trim().isEmpty()) {
                keys.add(card.moduleName);
            }
        }
        return keys;
    }

    private Set<String> readSet() {
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_GRADED_MODULES, "[]"));
            Set<String> set = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                set.add(array.getString(i));
            }
            return set;
        } catch (JSONException e) {
            return new HashSet<>();
        }
    }

    private void saveSet(Set<String> keys) {
        prefs.edit().putString(KEY_GRADED_MODULES, new JSONArray(keys).toString()).apply();
    }
}
