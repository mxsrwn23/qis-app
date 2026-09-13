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
 *  angezeigt wurde.
 *
 *  Führt zusätzlich eine zweite, komplett getrennte Baseline (notifiedKey) für
 *  Push-Benachrichtigungen aus dem Hintergrund-Worker. Ohne diese zweite Baseline würde jedes
 *  Öffnen der App (welches die UI-Baseline oben konsumiert) den Hintergrund-Abruf um die Chance
 *  bringen, dieselbe neue Note noch per Benachrichtigung zu melden. Entspricht
 *  SeenGradesStore.swift auf iOS (seenKey/notifiedKey). */
public final class SeenGradesStore {

    private static final String PREFS_NAME = "qis_seen_grades";
    private static final String KEY_GRADED_MODULES = "graded_modules";
    private static final String KEY_NOTIFIED_MODULES = "notified_modules";

    private final Context context;
    private final SharedPreferences prefs;

    public SeenGradesStore(Context context) {
        this.context = context.getApplicationContext();
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
            result.removeAll(readSet(KEY_GRADED_MODULES));
            return result;
        } finally {
            saveSet(KEY_GRADED_MODULES, currentGraded);
        }
    }

    /** Wie newlyGradedModuleKeys, aber gegen die eigene Benachrichtigungs-Baseline und ohne die
     *  UI-Baseline zu verändern. Für den Hintergrund-Worker gedacht. */
    public Set<String> newlyGradedForNotification(GradeTable table) {
        Set<String> currentGraded = gradedModuleKeys(table);
        try {
            if (!prefs.contains(KEY_NOTIFIED_MODULES)) {
                return new HashSet<>();
            }
            Set<String> result = new HashSet<>(currentGraded);
            result.removeAll(readSet(KEY_NOTIFIED_MODULES));
            return result;
        } finally {
            saveSet(KEY_NOTIFIED_MODULES, currentGraded);
        }
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    /** Setzt nur die Benachrichtigungs-Baseline auf leer (statt sie wie clear() zu entfernen),
     *  damit ein Testlauf garantiert alle aktuell benoteten Module als "neu" erkennt -- ein
     *  fehlender Key wird sonst als "allererster Aufruf" behandelt und meldet bewusst nichts. */
    public void debugForceEmptyNotifiedBaseline() {
        saveSet(KEY_NOTIFIED_MODULES, new HashSet<>());
    }

    private Set<String> gradedModuleKeys(GradeTable table) {
        GradeSettingsStore.Settings settings = new GradeSettingsStore(context).load();
        List<ModuleCardData> cards = GradeCardBuilder.buildCards(table, settings);
        Set<String> keys = new HashSet<>();
        for (ModuleCardData card : cards) {
            if (!card.grade.trim().isEmpty()) {
                keys.add(card.moduleName);
            }
        }
        return keys;
    }

    private Set<String> readSet(String key) {
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            Set<String> set = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                set.add(array.getString(i));
            }
            return set;
        } catch (JSONException e) {
            return new HashSet<>();
        }
    }

    private void saveSet(String key, Set<String> keys) {
        prefs.edit().putString(key, new JSONArray(keys).toString()).apply();
    }
}
