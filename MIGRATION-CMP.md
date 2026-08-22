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
│   │   └── desktopMain/  (JVM, optional später — ersetzt GTK4/Linux)
│   └── build.gradle.kts
├── archive/
│   └── linux-gtk4/       (GTK4-Prototyp — verworfen, als Referenz behalten)
└── android/             (bleibt als Referenz bis compose-app/ läuft, dann löschen)
```

## Key-Decisions (bestätigt)

1. **Auth-Modell: Multi-User** (1b) — `userId` auf alle Daten-Tabellen, sauber & zukunftssicher. Daten-Migration für bestehende Daten siehe unten.
2. **Linux/GTK4: Verschieben** (2) — nach `archive/linux-gtk4/`, nicht löschen.
3. **Desktop-Target: Erst nach Web** (3a) — M10 ist Bonus.
4. **RRULE: Erst testen** (4a) — lib-recur auf Wasm prüfen, dann entscheiden.
5. **Migration-Strategie: Neues `compose-app/`** (5a) — altes `android/` bleibt Referenz.
6. **Übergangs-Auth: Ja** (6a) — Backend akzeptiert Static-Token + DB-Token parallel.

## Auth-Modell: Multi-User

**Schema:**
- `users`: `id (TEXT PK), username (TEXT UNIQUE NOT NULL), password_hash (TEXT NOT NULL), created_at (INTEGER NOT NULL)`
- `tokens`: `id (TEXT PK), user_id (TEXT NOT NULL FK→users), token (TEXT UNIQUE NOT NULL), created_at (INTEGER NOT NULL), last_used_at (INTEGER)`
  - Ein Account kann mehrere Tokens haben (mehrere Geräte + Web)
- **Alle 7 Daten-Tabellen bekommen `userId`-Spalte:** todos, habits, habit_logs, habit_history, folders, notes, chat_messages

**Endpunkte:**
- `POST /auth/register` {username, password} → erstellt Account + Token, liefert Token
- `POST /auth/login` {username, password} → liefert Token
- `POST /auth/migrate-legacy` {username, password} → registriert Account + überträgt alle Legacy-Daten auf neuen User, liefert Token
- `/sync` validiert Token → schränkt alle Queries auf `userId` des Token-Inhabers ein

**Web:** Login-Screen (Username/Passwort) → Token in localStorage → für `/sync`
**Android:** Settings → Login (Username/Passwort) → Token speichern (ersetzt manuelle Token-Eingabe)

## Daten-Migration (bestehende Daten nicht verlieren!)

**Problem:** Bestehende Todos/Habits/Notes/Chats auf Server + Handy haben keine `userId`.

**Strategie:**
1. Backend legt bei `_migrate_schema` die `userId`-Spalte an (idempotent, default `NULL`).
2. Beim ersten Start nach Migration: **Legacy-User anlegen** (id=`legacy-user`, username=`legacy`, password_hash=`!LEGACY_NO_LOGIN` — nur via Static-Secret erreichbar, nicht via Login).
3. `_migrate_schema` setzt `userId = 'legacy-user'` für alle Zeilen mit `userId IS NULL`.
4. Wenn du dich registrierst (z.B. username=`chris`), rufst du `/auth/migrate-legacy` auf → alle Daten mit `userId='legacy-user'` bekommen deine neue `userId`.
5. **Danach:** Static-Secret in `.env` auf leer setzen → Legacy-Login deaktiviert.

**Android (lokal):** Die Handy-DB bekommt ebenfalls `userId`-Spalten (Room Migration v9→v10).
Beim ersten Sync nach der Migration nutzt die alte App weiterhin den Static-Token (→ Legacy-User auf dem Server).
Alle lokalen Zeilen bekommen beim Push die `userId` vom Server zugewiesen (oder lokal auf `legacy-user` gesetzt).
Nach Android-Login-Umstellung (M7/M8) → Token-basierter Sync mit richtigem `userId`.

**Sicherheit:**
- Vor der Migration Server-DB sichern: `cp todonotes.db todonotes.db.bak-pre-auth`
- Auf dem Handy: bestehender DB-Backup-Mechanismus (kopiert DB vor Migration)
- Migration ist idempotent → kann mehrfach laufen ohne Datenverlust

## Backend-Änderungen

1. **Auth-Tabellen:** `users` + `tokens` in `db.py`
2. **`userId`-Spalte** auf alle 7 Daten-Tabellen + idempotente Migration (`_migrate_schema`)
3. **Legacy-User + Daten-Zuordnung** beim ersten Start
4. **Auth-Endpunkte:** `/auth/register`, `/auth/login`, `/auth/migrate-legacy` (passlib/bcrypt für Hashing)
5. **Token-Validierung:** `/sync` prüft Token gegen `tokens`-Tabelle, schränkt Queries auf `userId` ein
6. **Übergangs-Auth:** Static-Secret aus `.env` weiterhin gültig (mappt auf Legacy-User)
7. **Statische Files:** FastAPI serviert kompilierte Wasm-App unter `/` (index.html, .wasm, .js) — gefüllt in M9
8. **CORS:** Web-App läuft auf gleicher Origin → kein CORS-Problem mehr
9. **Docker:** ein Container für API + statische Files

## Migration: Phasen

### Phase M0 — Backup & Plan ✅
- [x] Git-Backup (Tag + Branch)
- [x] Plan schreiben
- [x] Key-Decisions bestätigt (Multi-User, Linux verschieben, etc.)
- [ ] Linux nach `archive/linux-gtk4/` verschieben

### Phase M1 — Backend: Multi-User-Auth + userId-Migration ✅
- [x] `users` + `tokens` Tabellen in `db.py`
- [x] `userId`-Spalte auf alle 7 Daten-Tabellen (idempotent via `_migrate_schema`)
- [x] Legacy-User anlegen + bestehende Daten ihm zuordnen (`userId = 'legacy-user'`)
- [x] `/auth/register`, `/auth/login`, `/auth/migrate-legacy` Endpunkte (passlib/bcrypt)
- [x] `/sync` validiert Token gegen DB → filtert nach `userId`
- [x] **Übergangs-Auth:** Static-Secret weiterhin gültig (mappt auf Legacy-User)
- [x] Statische-Files-Routing (FastAPI `StaticFiles`) — leer, gefüllt in M9
- [x] **Daten-Sicherung:** Server-DB backup vor Migration (`todonotes.db.bak-pre-auth`)
- [x] **Test:** Unit-Tests grün (register/login/sync/migrate-legacy/isolation/idempotent)
- [x] **Test:** Docker neu gebaut, Migration durchgelaufen, Legacy-Daten zugewiesen
- [x] **Test:** Alte Android-App syncet weiter (Static-Token → Legacy-User, curl bestätigt)

### Phase M2 — KMP-Projekt aufsetzen ✅
- [x] Neues `compose-app/` KMP-Projekt (commonMain/androidMain/wasmJsMain/desktopMain)
- [x] Gradle-Config: Kotlin Multiplatform + Compose Multiplatform Plugin
- [x] Android-Target: baut (`assembleDebug`), MainActivity + Manifest
- [x] Wasm-Target: baut (`wasmJsBrowserDistribution`), index.html + CanvasBasedWindow
- [x] Desktop-Target: baut (`desktopJar`), JVM-Window
- [x] `expect/actual` für `Platform` (Android/Wasm/Desktop)
- [x] `App()` Composable in commonMain (Skeleton mit Material 3)
- [x] **Test:** Alle 3 Targets bauen grün
- **Hinweis:** Navigation + Material-Icons noch nicht dabei (kommen in M7, brauchen CMP-kompatible Versionen)

### Phase M3 — Logik nach commonMain ✅
- [x] `HabitEngine.kt` → commonMain (kotlinx-datetime statt java.util.Calendar)
- [x] `NoteTextBody.kt` + `NoteBody.kt` + `NoteBodyJson.kt` → commonMain (0 Android-Imports)
- [x] `SyncDTOs.kt` → commonMain (kotlinx.serialization ist MP)
- [x] `RecurrenceEngine.kt` → expect/actual (JVM: lib-recur, Wasm: RecurrenceCalculator-Fallback)
- [x] `RecurrenceCalculator.kt` → commonMain (kotlinx-datetime)
- [x] Entities (Todo, Habit, HabitLog, HabitHistoryEntry, Note, Folder, ChatMessage) → commonMain (ohne Room-Annotationen, kommt in M4)
- [x] `kotlinx-datetime` Dependency hinzugefügt
- [x] `lib-recur` als JVM-only Dependency (androidMain + desktopMain)
- [x] `commonTest` source set mit kotlin-test
- [x] 39 Unit-Tests in commonTest (HabitEngine 10, NoteTextBody 18, NoteBodyJson 11) — alle grün
- [x] Alle 3 Targets bauen (Android, Desktop, Wasm)
- [x] **Bug-Fix:** `resetWeekday` Calendar-Nummerierung (1=SO, 2=MO, 7=SA) korrekt behandelt
- [x] **Wasm-Fix:** `StringBuilder.deleteCharAt` → `deleteAt` (Wasm-kompatibel)

### Phase M4 — DB: Room KMP ✅
- [x] Room 3.0 Dependencies (room3-runtime, room3-compiler, sqlite-bundled, sqlite-web)
- [x] KSP 2.3.11 + AGP 8.10.0 (KSP brauchte min AGP 8.10)
- [x] Entities + DAOs in commonMain mit `androidx.room3.*` Annotationen
- [x] `@TypeConverter` → `@ColumnTypeConverter` (Room 3 Naming-Änderung)
- [x] `userId`-Spalte auf alle 7 Entities (Default `"legacy-user"`)
- [x] Migration v9→v10: `ALTER TABLE ... ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'` auf alle Tabellen
- [x] Alle 9 Migrationen (v1→v10) von `SupportSQLiteDatabase` → `SQLiteConnection` + `execSQL` Extension
- [x] `migrate()` ist jetzt `suspend fun` (Room 3 Coroutine-API)
- [x] `@Database(version=10)` + `@ConstructedBy(TodoNotesDatabaseConstructor::class)`
- [x] expect/actual `getDatabaseBuilder()`: Android (Context+Pfad), Desktop (JVM-Pfad), Wasm (inMemory, persistente OPFS-DB kommt M9)
- [x] `buildDatabase()` in commonMain: Builder + Migrationen + `.build()`
- [x] KSP generiert `TodoNotesDatabase_Impl.kt` für alle 3 Targets
- [x] Schema `10.json` exportiert + committet
- [x] Alle 3 Targets bauen grün (Android, Desktop, Wasm)
- [x] 39 commonTest-Tests noch grün
- [x] **Test:** Android APK installiert, baut + funktioniert

**Raum 3 Breaking Changes bewältigt:**
- `androidx.room` → `androidx.room3` (neues Package)
- `SupportSQLiteDatabase` → `SQLiteConnection` (neue Driver-API)
- `@TypeConverter` → `@ColumnTypeConverter`
- `migrate(db: SupportSQLiteDatabase)` → `suspend migrate(connection: SQLiteConnection)`
- `db.execSQL(sql)` → `connection.execSQL(sql)` (Extension-Funktion)
- KAPT → KSP-only (Room 3 ist Kotlin-only)

### Phase M5 — Networking: Ktor statt Retrofit
- [ ] Ktor-Client (multiplatform) Dependencies
- [ ] `SyncManager` umschreiben: Retrofit → Ktor (kann nach commonMain)
- [ ] Auth-Integration: Login/Token statt Static-Secret
- [ ] `expect` für HttpClient-Config falls nötig
- [ ] **Test:** Android: Sync funktioniert gegen Backend (mit Login)

### Phase M6 — RRULE auf Web
- [ ] Prüfen ob `lib-recur` (JVM-Library) auf Wasm verfügbar ist
- [ ] Falls nicht: `expect class RecurrenceEngine` mit:
    - androidMain: lib-recur (wie heute)
    - wasmJsMain: eigene RRULE-Implementierung für FREQ/INTERVAL/BYDAY/COUNT/UNTIL
- [ ] **Test:** Android: Recurrence funktioniert unverändert

### Phase M7 — UI nach commonMain
- [ ] Screens (TodosScreen, HabitsScreen, NotesScreen, ChatScreen, …) → commonMain
- [ ] Login-Screen → commonMain
- [ ] `expect` für plattformspezifische UI-Bits:
    - Insets/Permissions (Android) vs. Browser (Web)
    - `BackHandler` (Android) vs. Browser-History (Web)
- [ ] Theme/Farben → commonMain
- [ ] **Test:** Android: Alle 4 Tabs + Editor + Chat + Login funktionieren

### Phase M8 — Notifications (expect/actual)
- [ ] `expect class AlarmScheduler` → androidMain (heutiger Code 1:1), wasmJsMain (noop/Browser-Notification)
- [ ] `expect class NotificationHelper` → androidMain (wie heute), wasmJsMain (noop)
- [ ] `expect class WorkManagerSync` → androidMain (WorkManager), wasmJsMain (setTimeout/kein Auto-Sync)
- [ ] **Test:** Android: Fullscreen-Alarm funktioniert wie vorher

### Phase M9 — Web-Target
- [ ] `wasmJsMain` Entry-Point (HTML-Template + main())
- [ ] Build: `./gradlew :compose-app:wasmJsBrowserDistribution` → Wasm + JS + HTML
- [ ] Wasm-Output ins Backend-Static-Verzeichnis kopieren (oder Docker-Build)
- [ ] Docker: Web-Build wird ins Image kopiert
- [ ] **Test:** Im Browser öffnen → Login → Sync → alle Features

### Phase M10 — Desktop-Target (optional, später)
- [ ] `desktopMain` Entry-Point (JVM)
- [ ] Ersetzt GTK4/Linux — gleiche Compose-UI, nativ auf Linux/Mac/Windows
- [ ] **Test:** Auf Tower/Laptop laufen

## Risiken

- **lib-recur auf Wasm:** JVM-Library, könnte fehlen → expect/actual Fallback
- **Room KMP auf Wasm:** SQLite-OPFS, neu aber funktionsfähig (GitHub-Beispiele existieren)
- **Migration kann Android kaputt machen** → Backup, inkrementell, nach jedem Schritt testen
- **Wasm ist Beta** → Core-APIs (M3, Nav, LazyColumn) stabil genug für diese App
- **Aufwand:** ~7-10 Tage Fokus, aber 80% des Codes bleibt erhalten
- **Multi-User Migration:** userId auf alle Tabellen + Daten-Zuordnung → sorgfältig testen

## Test-Strategie

Nach JEDEM Schritt:
1. Android: `./gradlew assembleDebug` + install + tap-through
2. Unit-Tests: `./gradlew test`
3. Migration-Tests: `./gradlew connectedAndroidTest` (vor Deploy)
4. Später: Web-Build + Browser-Test
5. Nach M1: curl-Tests gegen Backend (Auth + Sync)

## Commit-Strategie

- Eigener Branch: `cmp-migration` (merge erst wenn Android wieder voll funktioniert)
- Pro Phase ein Commit (oder mehrere bei großen Phasen)
- PROGRESS.md nach jeder Phase aktualisieren
