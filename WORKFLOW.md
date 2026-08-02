# TodoNotes – Workflow (Build · Test · Git · Deploy)

Diese Datei beschreibt, wie Änderungen an der TodoNotes-App gemacht,
getestet und veröffentlicht werden. Sie richtet sich an andere AI-Chats
bzw. Entwickler, damit der Ablauf reproduzierbar ist.

---

## 1. Projekt-Überblick

```
todo
└── android/     Android-App (Kotlin, Jetpack Compose, Room, Retrofit)
    ├── app/src/main/                 App-Code
    │   ├── java/com/earendil/todonotes/
    │   │   ├── data/                 Entities, DAOs, Repos, Migrations,
    │   │   │   ├── richtext/         Notiz-Body (Plain-Text + Inline-Stil)
    │   │   │   └── sync/             Retrofit-Sync-Client
    │   │   └── ui/                   Compose-Screens (Todos, Habits, Notes, …)
    │   └── res/                      Resourcen (themes.xml, network config)
    ├── app/src/test/                 Unit-Tests (JUnit4)
    ├── app/src/androidTest/          Instrumented Tests (Migration, DB)
    └── build.gradle.kts, gradle/     Build-Konfiguration
└── backend/     Sync-Server (Docker + FastAPI + SQLite)
```

Remote: `https://github.com/theoretischer/todonotes.git` (Branch `main`, public).

---

## 2. Wichtig: Umgebung

```bash
# JDK 17 ist Pflicht für Gradle
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# adb separat (nicht im PATH)
export PATH="$PATH:/opt/android-sdk/platform-tools"
```

---

## 3. Build

```bash
cd android
# Nur kompilieren:
./gradlew compileDebugKotlin
# Debug-APK bauen:
./gradlew assembleDebug
# APK liegt dann unter:
#   android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Unit-Tests (lokal, schnell)

```bash
cd android
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew testDebugUnitTest

# Nur eine Testklasse:
./gradlew testDebugUnitTest --tests "com.earendil.todonotes.data.richtext.NoteTextBodyTest"

# Berichte:
#   android/app/build/reports/tests/testDebugUnitTest/index.html
```

Wichtig: Vor dem Deploy MÜSSEN die Tests grün sein. Bei Fehlern einzeln
ausführen und die HTML-Reports ansehen (`app/build/reports/tests/…/classes/…`).

---

## 5. Migrationstests (auf Gerät)

Die Room-Migrationen dürfen NICHT mit `adb uninstall` getestet werden
(Datenverlust). Stattdessen:

1. SQLite-Backup der alten DB aufs Gerät legen, App installieren, öffnen.
2. Oder: `connectedAndroidTest` läuft auf dem Gerät und testet die
   Migration mit Schema-JSONs (in `androidTest/assets/`).

**Wichtig nach `connectedAndroidTest`:** Der Befehl deinstalliert die App!
Danach IMMER neu installieren:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Migrationen: echte Room-Migrationen in `Migrations.kt` (nie
`fallbackToDestructiveMigration`). Backup-Sicherheitsnetz: vor jedem
DB-Öffnen wird `todonotes.db` → `todonotes.db.bak-v<version>` kopiert.

---

## 6. Auf das Gerät bringen & testen (adb)

```bash
export PATH="$PATH:/opt/android-sdk/platform-tools"

# APK installieren (update, ohne Daten zu löschen):
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# App starten/frisch starten:
adb shell am force-stop com.earendil.todonotes
adb shell am start -W -n com.earendil.todonotes/.MainActivity

# Crash-Log checken:
adb logcat -d | grep -E "FATAL EXCEPTION"

# Prozess prüfen (PID liefert):
adb shell pidof com.earendil.todonotes

# Screenshot machen (auf PC kopieren):
adb exec-out screencap -p > /tmp/screen.png
```

### UI-Elemente finden (für Taps / Screenshots)

```bash
# UI-Hierarchie dumpen (zeigt alle Elemente + Bounds):
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml > /tmp/ui.xml
# Bounds wie "[48,2115][203,2160]" → Mitte = ((48+203)/2, (2115+2160)/2)
```

Tabs in der BottomNavigation (auf dem S24 Ultra, 1080×2340):
- Aufgaben:      Tap ~ (125, 2137)
- Gewohnheiten:  Tap ~ (400, 2137)
- Notizen:       Tap ~ (677, 2137)
- Verlauf:       Tap ~ (953, 2137)

Tippen per Koordinaten:

```bash
adb shell input tap X Y
adb shell input keyevent 4   # Back
```

**Tipp:** Statt selbst zu tippen → den Chat-Flow nutzen, der per
`adb shell input tap` klickt und per Screenshot/`uiautomator` prüft.

---

## 7. Git-Workflow

```bash
cd todonotes   # Repository-Wurzel

git status           # was ist geändert
git diff --stat      # Änderungsübersicht
git add -A
# Commit-Message in Datei schreiben (vermeidet Shell-Escape-Probleme):
cat > /tmp/commitmsg.txt << 'EOF'
Kurze Zusammenfassung (was + warum)

Details in Stichpunkten
EOF
git commit -F /tmp/commitmsg.txt
git push
```

- Commit-Messages: Deutsch, prägnant, „was" + „warum".
- Immer nach dem Push prüfen: `git log --oneline -5`.

---

## 8. Sync-Server (optional)

```bash
cd backend
docker compose up -d --build   # Start
# Endpoint: http://<LAN-IP>:8000/sync
# Token: T14xI6zQmBv7JONHGwiqPCkIOgv1Cwmig6MrydqhMdQ
```

App-Einstellungen → Verbindung → Server-URL + Token setzen.

---

## 9. Typische Fehler & Fallstricke

| Problem | Lösung |
|---|---|
| `Unresolved reference 'dp'` / `'statusBarsPadding'` | Imports `androidx.compose.ui.unit.dp` / `androidx.compose.foundation.layout.*` fehlen |
| `PaddingValues(horizontal=…, top=…)` Compile-Fehler | `start=`/`end=` statt `horizontal=` verwenden, wenn `top=` gesetzt |
| `combinedClickable` braucht OptIn | `@file:OptIn(ExperimentalFoundationApi::class)` |
| `connectedAndroidTest` deinstalliert die App | danach immer `adb install -r …` |
| `FloatingActionButtonMenu` gibt's nicht | `ModalBottomSheet` verwenden |
| Shell-Escaping in Commit-Nachricht | `git commit -F datei.txt` |
| Tests nach Compose-Änderungen | `./gradlew testDebugUnitTest` immer laufen lassen |

---

## 10. Projekt-Stand (Stand: Aug 2026, Commit `24cc123`)

- Todos, Gewohnheiten, Notizen, Verlauf – 4 Tabs, alle funktional.
- Notizen: Editor mit kontinuierlichem Textfluss, visuelle Listen-Prefixe
  (Bullet, Nummer, Checkbox, Pfeil) + Inline-Formatierung (Fett/Kursiv/
  Unterstrichen via `**…**`, `*…*`, `__…__` im Text).
- Sync zum Backend (Retrofit), Dark-Mode-Systemleisten, einheitliche
  Tab-Layouts (keine doppelten Scaffold-Paddings).
- Unit-Tests: 33 grün.
