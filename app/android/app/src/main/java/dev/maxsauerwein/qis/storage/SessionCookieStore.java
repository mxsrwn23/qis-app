package dev.maxsauerwein.qis.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

/** Persistiert die QIS-Session-Cookies (JSESSIONID, _shibsession_…) verschlüsselt auf dem Gerät,
 *  damit Folgeabrufe die bestehende Session wiederverwenden können, statt den vollen SAML-Login
 *  erneut durchzuführen. Nie auf einem Server abgelegt, nur lokal. */
public final class SessionCookieStore {
    private static final String FILE_NAME = "qis_session";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_COOKIES = "cookies";

    private final SharedPreferences prefs;

    public SessionCookieStore(Context context) {
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

    public void save(String username, List<Cookie> cookies) {
        if (cookies.isEmpty()) {
            return;
        }
        Set<String> serialized = new HashSet<>();
        for (Cookie cookie : cookies) {
            serialized.add(cookie.toString());
        }
        prefs.edit()
                .putString(KEY_USERNAME, username)
                .putStringSet(KEY_COOKIES, serialized)
                .apply();
    }

    /** Liefert die gespeicherten Cookies nur, wenn sie zum angefragten Benutzernamen gehören.
     *  Verhindert, dass beim Testen geänderter Zugangsdaten (Einstellungen) versehentlich die
     *  Session der alten Zugangsdaten wiederverwendet wird. */
    public List<Cookie> load(String username, HttpUrl url) {
        String storedUsername = prefs.getString(KEY_USERNAME, null);
        if (storedUsername == null || !storedUsername.equals(username)) {
            return null;
        }
        Set<String> serialized = prefs.getStringSet(KEY_COOKIES, null);
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        List<Cookie> cookies = new ArrayList<>();
        for (String entry : serialized) {
            Cookie cookie = Cookie.parse(url, entry);
            if (cookie != null) {
                cookies.add(cookie);
            }
        }
        return cookies.isEmpty() ? null : cookies;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
