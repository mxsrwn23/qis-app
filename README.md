<div align="center">
  <img src="icon-128.png" width="96" height="96" alt="QIS+ Logo">
  
  <h1>QIS+ Noten <sup>Beta</sup></h1>

  <p><strong>Der QIS-Notenspiegel der Hochschule Trier, als native App.</strong></p>

  <p>
    Native Clients für iOS, macOS und Android, die sich per SSO bei QIS anmelden
    und den Notenspiegel als übersichtliche, farbcodierte Karten-Liste anzeigen –
    statt der unübersichtlichen QIS-Tabelle.
  </p>

  <p>
    <img src="https://img.shields.io/badge/iOS-17%2B-blue" alt="iOS 17+">
    <img src="https://img.shields.io/badge/macOS-14%2B-blue" alt="macOS 14+">
    <img src="https://img.shields.io/badge/Android-8.0%2B-green" alt="Android 8.0+ (API 26)">
    <a href="https://testflight.apple.com/join/d7K5aMV9"><img src="https://img.shields.io/badge/TestFlight-Beta-0D96F6?logo=apple&logoColor=white" alt="TestFlight Beta"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT%20(Non--Commercial)-green" alt="License: Modified MIT (Non-Commercial)"></a>
  </p>

  <p><strong>🧪 Jetzt in der Beta:</strong> <a href="https://testflight.apple.com/join/d7K5aMV9">iOS-Beta über TestFlight testen</a> · <a href="#beta-testen">Details</a></p>

  <p><em>Inoffizielles, privates Projekt – keine offizielle Verbindung zur Hochschule Trier.</em></p>
</div>

---

## Inhalt

- [Features](#features)
- [Beta testen](#beta-testen)
- [Farblegende (Standardfarben)](#farblegende-standardfarben)
- [Installation](#installation)
- [Datenschutz](#datenschutz)
- [Projektstruktur](#projektstruktur)
- [Für Entwickler:innen](#für-entwicklerinnen)
- [Troubleshooting](#troubleshooting)
- [Hinweis & Disclaimer](#hinweis--disclaimer)

---

## Features

- **Ein Login** – Zugangsdaten werden einmal eingegeben, sicher auf dem Gerät gespeichert (Keychain / `EncryptedSharedPreferences`) und für stille Re-Logins wiederverwendet.
- **Karten statt Tabelle** – Ein Modul pro Karte: Note groß, farbcodierter Status-Chip, ECTS-Chip. Antippen klappt die einzelnen Versuche/Studienleistungen auf, fehlgeschlagene Versuche sind dezent durchgestrichen.
- **KPI-Kopfzeile** – Gesamtschnitt und Summe ECTS immer sichtbar, inklusive dünnem Fortschrittsbalken zu einem Ziel-ECTS-Wert (automatisch 180/120 je nach erkanntem Bachelor-/Master-Abschluss, manuell überschreibbar).
- **Automatische Studiengang-Erkennung** – Studiengang und Abschluss werden aus den QIS-Stammdaten gelesen und als dezente Kopfzeile angezeigt (z. B. „B.Sc. Informatik (dual) · 6. Semester"), manuell änderbar in den Einstellungen.
- **Filter & Sortierung** – Schnellfilter „Alle / Bestanden / Offen" plus separate Sortierung nach Note oder Semester.
- **Eigene Farbschemata** – Alle Statusfarben (bestanden/offen/nicht bestanden/angemeldet) lassen sich in der Ansicht anpassen und zurücksetzen.
- **Individuelles Aufräumen** – Studienleistungen ausblenden, einzelne Spalten (Semester/Note/Versuch/Datum) ein-/ausblenden, Notenschnitt wahlweise aus allen Versuchen, nur dem letzten oder nur der besten Note berechnen.
- **Pull-to-Refresh** – Zieht neu und meldet sich dabei erneut bei QIS an, ohne dass unnötige SSO-Logins beim bloßen Navigieren durch die App ausgelöst werden.

---

## Beta testen

Du willst die iOS-App ausprobieren, ohne sie selbst zu bauen? Über TestFlight kannst du der Beta beitreten:

<p align="center">
  <a href="https://testflight.apple.com/join/d7K5aMV9">
    <img src="https://img.shields.io/badge/TestFlight-Beta%20beitreten-0D96F6?logo=apple&logoColor=white" alt="TestFlight Beta beitreten">
  </a>
</p>

1. **TestFlight** aus dem App Store installieren
2. Den Link oben auf dem iPhone/iPad öffnen und **Annehmen** tippen
3. **QIS+ Noten** installieren

> Voraussetzung: iOS 17+. Die Beta-Plätze sind begrenzt (max. 10.000 Tester), Builds laufen nach 90 Tage ab. Feedback und Bugs gerne als [Issue](../../issues) oder direkt über TestFlight ("Feedback senden").

---

## Farblegende (Standardfarben)

| Status | Bedeutung | Farbe |
| :--- | :--- | :--- |
| `BE` | Bestanden | 🟢 Grün |
| `PV` | Prüfungen vorhanden, noch nicht bestanden | 🔵 Blau |
| `NB` / `EN` | (Endgültig) nicht bestanden | 🔴 Rot |
| `AN` | Angemeldet, Ergebnis steht aus | 🟠 Orange |

Alle vier Farben sind an das QIS+-Logo angelehnt (Teal, Karmesinrot, Grün, Orange) und im Einstellungen-Menü frei anpassbar.

---

## Installation

Es gibt (noch) keine Store-Veröffentlichung – beide Apps werden lokal aus dem Quellcode gebaut.

### iOS / macOS (`app/apple`)

Voraussetzungen: Xcode, [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
cd app/apple
xcodegen generate
open QIS.xcodeproj
```

In Xcode das Scheme **QIS-iOS** (Simulator/Gerät) oder **QIS-macOS** wählen und starten (`⌘R`).

### Android (`app/android`)

Voraussetzungen: Android Studio oder JDK 17 für die CLI.

```bash
cd app/android
./gradlew assembleDebug
```

Alternativ das Projekt direkt in Android Studio öffnen und über **Run** starten. Die fertige APK liegt danach unter `app/build/outputs/apk/debug/`.

---

## Datenschutz

Die Apps erheben und übertragen **keine Daten an Dritte**. Zugangsdaten werden ausschließlich lokal und verschlüsselt auf dem Gerät gespeichert (iOS/macOS: Keychain, Android: `EncryptedSharedPreferences`) und nur für die direkte SSO-Anmeldung bei `qis.hochschule-trier.de` verwendet. Es gibt kein Tracking, keine Analytics und keine Server-Komponente – die Apps sprechen ausschließlich direkt mit dem QIS-Portal der Hochschule.

Vollständige Datenschutzerklärung: [PRIVACY.md](PRIVACY.md)

---

## Projektstruktur

```text
qis-app/
├── app/
│   ├── apple/                          # SwiftUI, gemeinsame Codebase für iOS & macOS
│   │   ├── project.yml                 # XcodeGen-Spezifikation
│   │   └── QIS/
│   │       ├── QISApp.swift            # App-Einstieg
│   │       ├── Models/                 # GradeTable, GradeSettings, Credentials, ...
│   │       ├── Services/               # QISClient (Login+Scrape), Keychain, Styling
│   │       └── Views/                  # RootView, LoginView, GradesView, ...
│   └── android/                        # Java, klassische XML-Views (kein Compose)
│       └── app/src/main/
│           ├── java/dev/maxsauerwein/qis/
│           │   ├── MainActivity.java   # Login/Notenspiegel/Fehler-Zustände
│           │   ├── network/QISClient.java
│           │   ├── storage/            # CredentialStore, GradeSettingsStore
│           │   └── util/               # GradeCardBuilder, GradeStyling
│           └── res/                    # Layouts, Strings, Themes
└── README.md
```

---

## Für Entwickler:innen

- **Gemeinsamer Login-/Scrape-Flow** – Beide Clients (`QISClient.swift` / `QISClient.java`) portieren dieselbe dreistufige SAML-SSO-Anmeldung plus Notenspiegel-Navigation (`tree.vm` → Studiengangs-Baum expandieren → `list.vm`).
- **Neueste PO-Version gewinnt** – Bei mehreren Studiengang-Einträgen (z. B. Wechsel des Schwerpunkts innerhalb derselben Prüfungsordnung) wird immer der zuletzt gelistete/neueste `PO-Version`-Eintrag gewählt, an beiden Navigationsschritten.
- **Colspan-/Rowspan-bewusstes Tabellen-Parsing** – Die Notentabelle wird wie ein echter Browser als Grid interpretiert, nicht zeilenweise naiv, da QIS Zellen über mehrere Versuche hinweg mit `rowspan` teilt.
- **Stammdaten-Erkennung ohne Zusatzrequest** – Studiengang/Abschluss werden aus derselben bereits geladenen `list.vm`-Seite gelesen wie die Notentabelle.
- **Kein Jetpack Compose** – Die Android-App nutzt bewusst klassische XML-Views mit Material Components, kein Compose.
- **Referenzskript** – `../qis-unlocked.py` (Python, `requests` + `BeautifulSoup`) im übergeordneten Ordner ist der ursprüngliche Login-Flow, aus dem beide Apps portiert wurden.

---

## Troubleshooting

| Problem | Lösung |
| :--- | :--- |
| **„CSRF-Token konnte nicht gefunden werden"** | QIS-Login-Seite hat sich geändert oder die Anmeldung ist nicht bei `execution=e1s1` gelandet – erneut versuchen. |
| **„QIS hat nicht zur erwarteten Login-Seite weitergeleitet"** | Meist ein einmaliger SSO-Timing-Fehler; über „Erneut versuchen" behoben. Tritt das dauerhaft auf, Internetverbindung/QIS-Erreichbarkeit prüfen. |
| **Module fehlen im Notenspiegel** | Sicherstellen, dass der neueste Studiengang-Eintrag (aktuelle PO-Version) ausgewählt wird – das übernimmt die App automatisch. |
| **Android-Build schlägt mit „Unsupported class file major version" fehl** | Gradle 8.11.1 benötigt JDK ≤ 23; mit `JAVA_HOME=$(brew --prefix openjdk@17) ./gradlew ...` bauen. |
| **iOS: Farben/Akzentfarbe ändern sich nicht** | Nach Änderungen an `project.yml` erneut `xcodegen generate` ausführen, bevor in Xcode gebaut wird. |

---

## Hinweis & Disclaimer

Diese Software ist ein inoffizielles Open-Source-Projekt und steht in keinerlei Verbindung zur Hochschule Trier. Die Entwicklung erfolgt privat und ohne Gewähr. Es werden keine Zugangsdaten oder personenbezogenen Daten auf externen Servern gespeichert.

Veröffentlicht unter der [Modified MIT License (Non-Commercial)](LICENSE).
