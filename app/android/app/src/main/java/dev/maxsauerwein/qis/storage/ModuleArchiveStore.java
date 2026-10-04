package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Speichert die vom Nutzer ausgeblendeten Module lokal für die aktuelle Anmeldung. */
public final class ModuleArchiveStore {

    private static final String PREFS_NAME = "qis_module_archive";
    private static final String KEY_ARCHIVED_MODULE_KEYS = "archived_module_keys";

    private final SharedPreferences prefs;

    public ModuleArchiveStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> load() {
        return new HashSet<>(prefs.getStringSet(KEY_ARCHIVED_MODULE_KEYS, Collections.emptySet()));
    }

    public void save(Set<String> moduleKeys) {
        prefs.edit().putStringSet(KEY_ARCHIVED_MODULE_KEYS, moduleKeys).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY_ARCHIVED_MODULE_KEYS).apply();
    }
}
