package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Speichert die Module, die der Nutzer vom Notenschnitt ausschließt. */
public final class AverageInclusionStore {

    private static final String PREFS_NAME = "qis_average_inclusion";
    private static final String KEY_EXCLUDED_MODULE_KEYS = "excluded_module_keys";

    private final SharedPreferences prefs;

    public AverageInclusionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> loadExcludedModuleKeys() {
        return new HashSet<>(prefs.getStringSet(KEY_EXCLUDED_MODULE_KEYS, Collections.emptySet()));
    }

    public void saveExcludedModuleKeys(Set<String> moduleKeys) {
        prefs.edit().putStringSet(KEY_EXCLUDED_MODULE_KEYS, moduleKeys).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY_EXCLUDED_MODULE_KEYS).apply();
    }
}
