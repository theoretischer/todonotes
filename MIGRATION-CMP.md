# Migrationsplan: Compose Multiplatform (CMP)

**Status: PLAN — noch nicht mit Implementierung begonnen**
**Backup: Tag `backup-pre-cmp` + Branch `backup-pre-cmp-migration` (Stand `bdb89a8`)**

## Ziel

Eine Codebase (Kotlin + Compose Multiplatform) → drei Targets:
- **Android** (mit Fullscreen-Notifications/Alarms via expect/actual)
- **Web** (Wasm, im Browser, vom Backend ausgeliefert)
- **Desktop** (JVM, optional später — ersetzt GTK4/Linux)

Backend wird erweitert: Auth (Accounts) + statische Auslieferung der Web-App.

## Warum

- GTK4/libadwaita ist für Rich-Text/Chat/Swipe zu komplex und wird nie hübsch
- Zwei UI-Codebases (Compose + React/GTK) = jedes Feature zweimal bauen
- CMP erlaubt **wirklich gleiche UI** aus einer Codebase
- Android ist bereits in Compose geschrieben → Migration, kein Rewrite
- Compose for Web (Wasm) ist seit Sept 2025 Beta, Material 3 + Navigation stabil

## Architektur (Ziel)

```
todonotes/
├── backend/              (FastAPI + SQLite + Auth + statische Web-Files)
├── compose-app/          (KMP-Projekt — EINE Codebase)
│   ├── src/
│   │   ├── commonMain/   (geteilte UI + Logik: Screens, Repos, Sync, DB)
│   │   ├── androidMain/  (Notifications, Alarms, WorkManager, Android-Room)
│   │   ├── wasmJsMain/   (Web-Entry-Point, Browser-Spezifika)
│   │   └── desktopMain/  (JVM, optional — ersetzt GTK4)
│   └── build.gradle.kts
└── linux/                (eingefroren — GTK4-Prototyp, wird durch Desktop-Target ersetzt)
```

## Auth-Modell (Entscheidung nötig!)

**Aktueller Stand:** Ein Shared-Secret-Token in `.env`, alle Clients nutzen es.

**Vorschlag: Account-basiert, Single-User-Deployment**

- `users`-Tabelle: `id, username, password_hash, created_at`
- `POST /auth/register` {username, password} → erstellt Account + Token
- `POST /auth/login` {username, password} → liefert Token
- `tokens`-Tabelle: `id, user_id, token, created_at, last_used_at`
  (ein Account kann mehrere Tokens haben — für mehrere Geräte)
- **Web:** Login-Screen (Username/Passwort) → Token in localStorage → für `/sync` nutzen
- **Android:** Settings → Login (Username/Passwort) → Token speichern (ersetzt manuelle Token-Eingabe)
- **Daten:** NICHT nach user_id gescoped (Single-User, ein Account = alle Daten).
  Falls später Multi-User gewünscht: user_id auf alle Tabellen, dann migrieren.
  → Vermeidet jetzt eine massive Migration aller Tabellen.

**Alternative (falls Multi-User gewünscht):**
- user_id auf alle Daten-Tabellen (todos, habits, notes, folders, chat_messages, …)
- Alle Queries nach user_id gefiltert
- Mehr Aufwand, aber sauberer für mehrere Nutzer

→ **Bitte entscheiden: Single-User (einfach) oder Multi-User (sauber)?**

## Backend-Änderungen

1. **Auth-Endpunkte:** `/auth/register`, `/auth/login` (liefern Token)
2. **Token-Validierung:** `/sync` prüft Token gegen `tokens`-Tabelle (statt Static-Secret)
3. **Statische Files:** FastAPI serviert kompilierte Wasm-App unter `/` (index.html, .wasm, .js)
4. **CORS:** Web-App läuft auf gleicher Origin → kein CORS-Problem mehr
5. **Docker:** ein Container für API + statische Files

## Migration: Phasen

### Phase M0 — Backup & Plan ✅ (dieser Schritt)
- [x] Git-Backup (Tag + Branch)
- [x] Plan schreiben
- [ ] Bestätigung der Key-Decisions (Auth-Modell, Linux-Verbleib)

### Phase M1 — Backend: Auth + Web-Serving
- [ ] `users` + `tokens` Tabellen in `db.py`
- [ ] `/auth/register`, `/auth/login` Endpunkte (passlib/bcrypt für Hashing)
- [ ] `/sync` validiert Token gegen DB (statt Static-Secret)
- [ ] Statische-Files-Routing (FastAPI `StaticFiles`)
- [ ] Docker: Web-Build wird ins Image kopiert
- [ ] **Test:** Manuell mit curl registrieren/login/syncen
- **Achtung:** Bestehende Android-App nutzt noch Static-Token → Übergangsphase:
  Backend akzeptiert BEIDE (Static-Secret ODER DB-Token), bis Android umgestellt ist.

### Phase M2 — KMP-Projekt aufsetzen
- [ ] Neues `compose-app/` KMP-Projekt (commonMain/androidMain/wasmJsMain)
- [ ] Gradle-Config: Kotlin Multiplatform + Compose Multiplatform Plugin
- [ ] Android-Target: bestehende Android-App als `androidMain` einbinden
- [ ] **Test:** Android-App baut + startet (noch nichts verschoben, nur Struktur)
- **Strategie:** Inkrementell — erst Struktur, dann Datei-für-Datei verschieben.
  Nach JEDEM Schritt bauen + auf Gerät testen.

### Phase M3 — Logik nach commonMain
- [ ] `HabitEngine.kt` (0 Android-Imports) → commonMain
- [ ] `NoteTextBody.kt` (0 Android-Imports) → commonMain
- [ ] `SyncDTOs.kt` (0 Android-Imports, kotlinx.serialization ist MP) → commonMain
- [ ] `RecurrenceEngine.kt` (`android.util.Log` → expect/actual Logger) → commonMain
- [ ] Entities (Todo, Habit, …) → commonMain (Room KMP)
- [ ] **Test:** Android baut + funktioniert, Unit-Tests grün

### Phase M4 — DB: Room KMP
- [ ] Room KMP Dependencies (room-runtime multiplatform)
- [ ] Entities + DAOs nach commonMain
- [ ] `expect class DatabaseBuilder` → `actual` pro Plattform (Android: Room.databaseBuilder, Wasm: SQLite-OPFS)
- [ ] Migrationen bleiben shared (sind plattformneutral)
- [ ] **Test:** Android: DB funktioniert, Migrationen grün

### Phase M5 — Networking: Ktor statt Retrofit
- [ ] Ktor-Client (multiplatform) Dependencies
- [ ] `SyncManager` umschreiben: Retrofit → Ktor (kann nach commonMain)
- [ ] `expect` für HttpClient-Config falls nötig
- [ ] **Test:** Android: Sync funktioniert gegen Backend

### Phase M6 — RRULE auf Web
- [ ] Prüfen ob `lib-recur` (JVM-Library) auf Wasm verfügbar ist
- [ ] Falls nicht: `expect class RecurrenceEngine` mit:
    - androidMain: lib-recur (wie heute)
    - wasmJsMain: eigene RRULE-Implementierung für FREQ/INTERVAL/BYDAY/COUNT/UNTIL
      (oder Backend berechnet nächstes Vorkommen)
- [ ] **Test:** Android: Recurrence funktioniert unverändert

### Phase M7 — UI nach commonMain
- [ ] Screens (TodosScreen, HabitsScreen, NotesScreen, ChatScreen, …) → commonMain
- [ ] `expect` für plattformspezifische UI-Bits:
    - Insets/Permissions (Android) vs. Browser (Web)
    - `BackHandler` (Android) vs. Browser-History (Web)
- [ ] Theme/Farben → commonMain
- [ ] **Test:** Android: Alle 4 Tabs + Editor + Chat funktionieren

### Phase M8 — Notifications (expect/actual)
- [ ] `expect class AlarmScheduler` → androidMain (heutiger Code 1:1), wasmJsMain (noop/Browser-Notification)
- [ ] `expect class NotificationHelper` → androidMain (wie heute), wasmJsMain (noop)
- [ ] `expect class WorkManagerSync` → androidMain (WorkManager), wasmJsMain (setTimeout/kein Auto-Sync)
- [ ] **Test:** Android: Fullscreen-Alarm funktioniert wie vorher

### Phase M9 — Web-Target
- [ ] `wasmJsMain` Entry-Point (HTML-Template + main())
- [ ] Build: `./gradlew wasmJsBrowserDistribution` → Wasm + JS + HTML
- [ ] Wasm-Output ins Backend-Static-Verzeichnis kopieren (oder Docker-Build)
- [ ] **Test:** Im Browser öffnen → Login → Sync → alle Features

### Phase M10 — Desktop-Target (optional)
- [ ] `desktopMain` Entry-Point (JVM)
- [ ] Ersetzt GTK4/Linux — gleiche Compose-UI, nativ auf Linux/Mac/Windows
- [ ] **Test:** Auf Tower/Laptop laufen

## Key-Decisions (bitte bestätigen)

1. **Auth-Modell:** Single-User (ein Account, keine user_id auf Tabellen) oder Multi-User?
   → Empfehlung: **Single-User** (einfacher, reicht für dich, später erweiterbar)

2. **Linux/GTK4:** Einfrieren (lassen, nicht weiterentwickeln) oder löschen?
   → Empfehlung: **Einfrieren**, löschen wenn Desktop-Target (M10) läuft

3. **Desktop-Target (M10):** Jetzt planen oder erst nach Web?
   → Empfehlung: **Erst nach Web** (M9), Desktop ist Bonus

4. **RRULE:** Falls lib-recur nicht auf Wasm läuft — eigene Implementierung oder Backend-berechnet?
   → Empfehlung: **Erst testen**, dann entscheiden

5. **Migration-Strategie:** In-place (bestehendes android/ konvertieren) oder neues compose-app/ + schrittweise verschieben?
   → Empfehlung: **Neues compose-app/**, altes android/ bleibt Referenz bis alles läuft, dann löschen

6. **Übergangs-Auth:** Backend akzeptiert während Migration sowohl Static-Token als auch DB-Token?
   → Empfehlung: **Ja**, bricht nichts während Android noch nicht umgestellt ist

## Risiken

- **lib-recur auf Wasm:** JVM-Library, könnte fehlen → expect/actual Fallback
- **Room KMP auf Wasm:** SQLite-OPFS, neu aber funktionsfähig (GitHub-Beispiele existieren)
- **Migration kann Android kaputt machen** → Backup, inkrementell, nach jedem Schritt testen
- **Wasm ist Beta** → Core-APIs (M3, Nav, LazyColumn) stabil genug für diese App
- **Aufwand:** ~7-10 Tage Fokus, aber 80% des Codes bleibt erhalten

## Test-Strategie

Nach JEDEM Schritt:
1. Android: `./gradlew assembleDebug` + install + tap-through
2. Unit-Tests: `./gradlew test`
3. Migration-Tests: `./gradlew connectedAndroidTest` (vor Deploy)
4. Später: Web-Build + Browser-Test

## Commit-Strategie

- Eigener Branch: `cmp-migration` (merge erst wenn Android wieder voll funktioniert)
- Pro Phase ein Commit (oder mehrere bei großen Phasen)
- PROGRESS.md nach jeder Phase aktualisieren
