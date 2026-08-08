package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import dev.maxsauerwein.qis.model.Credentials;

public final class CredentialStore {
    private static final String FILE_NAME = "qis_credentials";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private final SharedPreferences prefs;

    public CredentialStore(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Konnte sicheren Speicher nicht initialisieren", e);
        }
    }

    public void save(Credentials credentials) {
        prefs.edit()
                .putString(KEY_USERNAME, credentials.username)
                .putString(KEY_PASSWORD, credentials.password)
                .apply();
    }

    public Credentials load() {
        String username = prefs.getString(KEY_USERNAME, null);
        String password = prefs.getString(KEY_PASSWORD, null);
        if (username == null || password == null) {
            return null;
        }
        return new Credentials(username, password);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
