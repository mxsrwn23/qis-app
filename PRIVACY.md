# Datenschutzerklärung für QIS+ Noten

**Stand: 8. August 2026**

*English summary below.*

## 1. Verantwortlicher

Verantwortlicher im Sinne der Datenschutz-Grundverordnung (DSGVO) ist:

Max Sauerwein
E-Mail: sauerweinmax@gmail.com

QIS+ Noten ist ein privates Open-Source-Projekt und steht in keiner Verbindung zur Hochschule Trier.

## 2. Kurzfassung

**QIS+ Noten überträgt keine personenbezogenen Daten an den Entwickler oder an Dritte.** Es gibt keinen eigenen Server, keine Analyse-, Tracking- oder Werbedienste. Damit die App dich automatisch anmelden und deinen Notenspiegel anzeigen kann, speichert sie deine Zugangsdaten, eine Kopie deines zuletzt abgerufenen Notenspiegels sowie deine Anmelde-Session **verschlüsselt bzw. lokal auf deinem Gerät**. Netzwerkverbindungen bestehen ausschließlich direkt zwischen deinem Gerät und dem Prüfungsportal der Hochschule Trier (`qis.hochschule-trier.de`) und deren Anmeldedienst (Shibboleth-IdP) — nie zu Servern des Entwicklers, da solche nicht existieren.

## 3. Was QIS+ Noten tut

QIS+ Noten sind native Apps für iOS, macOS und Android, die sich mit deinen Hochschul-Zugangsdaten per SSO (Shibboleth/SAML) beim Prüfungsportal der Hochschule Trier anmelden und deinen Notenspiegel als übersichtliche Karten-Liste anzeigen — als Alternative zur QIS-eigenen Weboberfläche.

Die App kommuniziert ausschließlich mit `qis.hochschule-trier.de` und dem zugehörigen Shibboleth-Anmeldedienst der Hochschule. Abrufe finden nur durch aktive Nutzung statt (App-Öffnung, Pull-to-refresh) — es gibt keine Hintergrund- oder Timer-basierten Abrufe.

## 4. Welche Daten verarbeitet werden

### 4.1 Zugangsdaten (Benutzername und Passwort)

Damit du dich nicht bei jedem App-Start erneut anmelden musst, werden dein Hochschul-Benutzername und -Passwort lokal auf deinem Gerät gespeichert:

- iOS/macOS: im Schlüsselbund (Keychain), mit `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` — der Eintrag wird dadurch nicht in Geräte- oder iCloud-Backups aufgenommen.
- Android: in `EncryptedSharedPreferences` (AES-256, hardwaregestützt über den Android Keystore, sofern vom Gerät unterstützt).

Deine Zugangsdaten werden **ausschließlich** zur Anmeldung bei `qis.hochschule-trier.de` und dem zugehörigen Shibboleth-Anmeldedienst der Hochschule Trier verwendet und niemals an den Entwickler oder sonstige Dritte übertragen.

### 4.2 Notenspiegel (lokaler Cache)

Um das Prüfungsportal nicht bei jedem App-Start neu kontaktieren zu müssen, speichert die App eine Kopie deines zuletzt erfolgreich abgerufenen Notenspiegels lokal (iOS/macOS: `UserDefaults`; Android: `SharedPreferences`), zusammen mit einem Zeitstempel. Dieser Cache:

- verlässt dein Gerät nicht,
- wird beim Abmelden automatisch gelöscht,
- ist nur für die App selbst zugänglich (App-Sandbox).

### 4.3 Anmelde-Session (Cookies)

Nach erfolgreicher Anmeldung speichert die App die von der Hochschule ausgestellten Session-Cookies verschlüsselt (Keychain / `EncryptedSharedPreferences`, an deinen Benutzernamen gebunden), damit nicht jeder Abruf den vollständigen SSO-Login-Vorgang erneut durchlaufen muss. Diese Cookies werden ausschließlich an `qis.hochschule-trier.de` gesendet.

### 4.4 Einstellungen

Nicht-sensible Anzeigeeinstellungen (z. B. Studiengang, Semester, Ziel-ECTS, sichtbare Spalten, Farbschema) werden lokal gespeichert, damit sie beim nächsten App-Start erhalten bleiben. Sie enthalten keine Zugangsdaten oder Notendaten.

## 5. Rechtsgrundlage

Die Verarbeitung erfolgt auf Grundlage von Art. 6 Abs. 1 lit. b DSGVO (Erfüllung der von dir gewünschten Funktion — automatische Anmeldung und Anzeige deines Notenspiegels) sowie Art. 6 Abs. 1 lit. a DSGVO (deine Einwilligung durch aktive Eingabe deiner Zugangsdaten in der App). Du kannst deine Zugangsdaten und alle lokal gespeicherten Daten jederzeit über die Abmelden-Funktion in der App oder durch Deinstallation der App vollständig löschen.

## 6. Keine Weitergabe, keine Drittanbieter

- Kein eigener Server des Entwicklers (es existiert keiner) — Netzwerkverbindungen bestehen ausschließlich direkt zwischen deinem Gerät und `qis.hochschule-trier.de` bzw. dem Shibboleth-Anmeldedienst der Hochschule.
- Keine Analyse-, Tracking-, Crash-Reporting- oder Werbedienste.
- Keine Weitergabe an Dritte.
- Keine App-Berechtigungen über den für die Netzwerkverbindung notwendigen Internetzugriff hinaus.

Für den Abruf des Prüfungsportals selbst gilt die [Datenschutzerklärung der Hochschule Trier](https://www.hochschule-trier.de/datenschutz).

## 7. Speicherdauer

Zugangsdaten, Notenspiegel-Cache und Session-Cookies verbleiben auf deinem Gerät, bis du dich in der App abmeldest (dabei werden alle drei automatisch gelöscht) oder die App deinstallierst. Anzeigeeinstellungen (siehe 4.4) bleiben unabhängig davon erhalten, bis du die App deinstallierst.

## 8. Deine Rechte

Nach der DSGVO stehen dir folgende Rechte zu:

- Auskunft (Art. 15 DSGVO)
- Berichtigung (Art. 16 DSGVO)
- Löschung (Art. 17 DSGVO) — in der Praxis durch Abmelden in der App oder Deinstallation sofort selbst umsetzbar
- Einschränkung der Verarbeitung (Art. 18 DSGVO)
- Datenübertragbarkeit (Art. 20 DSGVO)
- Widerspruch (Art. 21 DSGVO)
- Widerruf einer Einwilligung (Art. 7 Abs. 3 DSGVO)
- Beschwerde bei einer Datenschutz-Aufsichtsbehörde (Art. 77 DSGVO)

Wende dich dazu an die oben genannte Kontaktadresse.

## 9. Änderungen dieser Datenschutzerklärung

Diese Datenschutzerklärung kann angepasst werden, wenn sich der Funktionsumfang von QIS+ Noten ändert. Die jeweils aktuelle Fassung findest du unter:
https://github.com/mxsrwn23/qis-app/blob/main/PRIVACY.md

---

## English Summary

**QIS+ Noten does not transmit personal data to the developer or any third party.**

- The app logs in to Trier University of Applied Sciences' exam portal (`qis.hochschule-trier.de`) via SSO (Shibboleth/SAML) using your own credentials, and displays your grades as a card list.
- To enable auto-login and avoid unnecessary server load, the app stores your **credentials** (Keychain / `EncryptedSharedPreferences`, encrypted), a **cache of your last-fetched grades** (local UserDefaults/SharedPreferences), and your **session cookies** (encrypted, tied to your username) locally on your device.
- Network connections go exclusively and directly to `qis.hochschule-trier.de` and the university's Shibboleth login service — there is no developer-operated server, no analytics, no tracking, no ads, and no background/timer-based fetching (data is only fetched on active app use).
- Logging out in the app deletes all locally stored credentials, grade cache, and session data immediately; uninstalling the app removes everything else.
- Controller / contact: Max Sauerwein, sauerweinmax@gmail.com. QIS+ Noten is a private open-source project and is not affiliated with Trier University of Applied Sciences.
