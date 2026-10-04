# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

QIS+ Noten — native clients (iOS/macOS via SwiftUI, Android via classic XML Views)
that log in to the Hochschule Trier QIS portal via SAML SSO and render the
`Notenspiegel` (grade overview) as a color-coded card list instead of the raw
QIS table. Two independent platform apps share the same login/scrape logic,
ported in parallel rather than via shared code — see `README.md` for the full
feature list, project structure, and privacy notes.

No backend, no analytics, no telemetry: both apps talk directly to
`qis.hochschule-trier.de`. Credentials are stored locally only (iOS/macOS:
Keychain via `KeychainStore.swift`; Android: `EncryptedSharedPreferences` via
`CredentialStore.java`).

## Repo layout

```
app/apple/      SwiftUI, shared codebase for iOS + macOS (one Xcode target, two platforms)
  project.yml   XcodeGen spec — regenerate QIS.xcodeproj after editing this
  QIS/
    Models/     GradeTable, GradeSettings, Credentials, DegreeOption, ...
    Services/
      Networking/  QISClient.swift (login + scrape), BackgroundGradeRefresher, NotificationService
      Stores/      Keychain, GradeCache, SessionCookieStore, ModuleArchiveStore, SeenGradesStore
      Utilities/   GradeAnalysis, GradeCardBuilder, GradeStyling, QISLabels
    Views/      RootView, LoginView, GradesView, SettingsView, DegreeSetupView, ...

app/android/    Java, classic XML layouts + Material Components (deliberately no Compose)
  app/src/main/java/dev/maxsauerwein/qis/
    MainActivity.java, SettingsActivity.java, DegreeSetupActivity.java, DisplayOptionsActivity.java
    network/QISClient.java       mirrors the Swift client's login + scrape flow
    storage/                     CredentialStore, GradeCacheStore, GradeSettingsStore,
                                  ModuleArchiveStore, SeenGradesStore, SessionCookieStore
    util/                        GradeAnalysis, GradeCardBuilder, GradeStyling, QISLabels
    work/                        GradeRefreshWorker + Scheduler (background refresh)
```

There is no server component, no shared library between the two apps, and no
test suite in either target — changes must be verified by building/running
the app.

## Build & run

### iOS / macOS (`app/apple`)

```bash
cd app/apple
xcodegen generate   # required after ANY change to project.yml
open QIS.xcodeproj
```

Schemes: `QIS-iOS` (simulator/device), `QIS-macOS`. Build from the CLI with:

```bash
xcodebuild -project QIS.xcodeproj -scheme QIS-iOS \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
xcodebuild -project QIS.xcodeproj -scheme QIS-macOS -destination 'platform=macOS' build
```

If you change colors/accent/icons and don't see them reflected, you likely
forgot to re-run `xcodegen generate` before building.

### Android (`app/android`)

```bash
cd app/android
./gradlew assembleDebug
```

Gradle 8.11.1 requires JDK ≤ 23. If the build fails with "Unsupported class
file major version", build with:

```bash
JAVA_HOME=$(brew --prefix openjdk@17) ./gradlew assembleDebug
```

Output APK: `app/android/app/build/outputs/apk/debug/`.

## Key shared behavior — keep both platforms in sync

`QISClient.swift` and `QISClient.java` independently implement the *same*
three-step SAML SSO login + grade-scrape flow, ported from the reference
script `qis-unlocked.py` (Python, `requests` + `BeautifulSoup`). When fixing a
bug or changing scrape/login logic, check whether the equivalent change is
needed in the other platform's `QISClient`.

Non-obvious invariants both clients rely on:

- **Newest PO-Version wins.** When a student has multiple Studiengang entries
  (e.g. a changed Schwerpunkt within the same Prüfungsordnung), always pick
  the most recently listed `PO-Version` entry — at both the course-tree
  navigation step and the grade-table step.
- **Grid-aware table parsing.** The grade table must be parsed like a real
  browser renders it (respecting `colspan`/`rowspan`), not row-by-row —
  QIS splits cells across multiple Versuche using `rowspan`.
- **Stammdaten come free.** Studiengang/Abschluss are read from the same
  already-fetched `list.vm` page as the grade table — don't issue an extra
  request for them.
- **Session reuse vs. full login.** Pull-to-refresh and normal navigation
  should reuse a stored session cookie first and only fall back to a full
  SAML login when the session is actually invalid/expired (redirect back to
  the IdP, or expected data missing) — not on generic network errors. Forcing
  a full login on every refresh causes unnecessary/visible SSO prompts.
- **Ephemeral cookie storage.** The iOS/macOS client deliberately uses
  `URLSessionConfiguration.ephemeral`'s in-memory cookie storage during the
  login flow rather than a separately assigned `HTTPCookieStorage()` —
  the latter breaks cookie propagation through the QIS→SP→IdP redirect chain.

## Conventions

- Android intentionally does not use Jetpack Compose — stick to XML layouts +
  Material Components for new Android UI.
- Swift comments and German-language user-facing strings/error messages are
  the existing style on the Apple side (`QISError.errorDescription`, etc.);
  match it rather than switching to English strings.
- No test suite exists; validate changes by building and running the app
  (simulator/device for iOS, emulator/device for Android) against the real
  QIS login flow, or ask the user to verify if you cannot run the app
  yourself.
- `.claude/settings.local.json` files under `app/apple/QIS/` and
  `app/android/app/src/main/res/` are local permission allowlists (gitignored
  at the root level via `.claude/settings.local.json`) — don't treat them as
  project configuration to maintain.
