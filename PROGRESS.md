# TodoNotes – Progress

**Phase: Notizen-App (Block F) – F1–F6 erledigt, F7 (Bilder) als nächstes**

## Builds
- **0.1.0** (Debug): MVP – Todos erstellen/erledigen, einfache Liste.
- **0.2.0** (Debug): Block A1–A3. Todo bearbeiten (Tap → Edit-Dialog), Swipe-to-Delete (Soft Delete), Recurrence-Picker im Create-Dialog (Grundtypen + Intervall).
- **0.3.0** (Debug): Block A4–A5. Erweiterter RRULE-Editor (Samsung-Style: vertikale Radio-Liste, Wochentag-Chips, 3 Monatsmodi, Laufzeit-Sektion). RFC 5545 via `dmfs/lib-recur 0.17.1` (RecurrenceEngine mit COUNT/BYDAY/BYMONTHDAY/BYSETPOS).
- **0.3.1** (Debug): Option-C-Bugfix. Nächstes Vorkommen ab `fromDue` (dueAt), nicht `now`. Sonntag fällig, Sonntag erledigt → nächstes Vorkommen Dienstag. Unit-tests grün.
- **0.4.0** (Debug): Block B1–B7. Neuer Tab „Gewohnheiten" (4 Tabs: Aufgaben · Gewohnheiten · Notizen · Verlauf). Habit + HabitLog Entities (DB v2), HabitEngine mit Periodenstart/Reset-Logik (5 Unit-Tests), HabitRepository/ViewModel, HabitCadencePicker (vereinfacht: n mal pro Tag/Woche/Monat/Jahr/n Tage, inline-n-Feld), HabitsScreen mit HabitCard (0/n + Progress-Balken + +). RecurrenceEditor der Todos auf inline-n umgestellt.
- **0.5.0** (Debug): Block B8–B11. Zeitraum-Labels ("n mal pro X"), Header "Zeitraum", Wochentag-Auswahl entfernt (Reset-Tag = Startdatum). Habit-Verlauf pro Periode: HabitHistoryEntry (DB v5), automatischer History-Eintrag bei Periodenwechsel, Haken "In Verlauf eintragen". "Periode abschließen" pro Gewohnheit im ⋮-Menü. Verlauf-Tab mit Sektionen Gewohnheiten + Aufgaben. Bugfixes: byWeekdays NOT-NULL (Feld entfernt), CASCADE beim upsert (→ @Update). TopAppBar bei Gewohnheiten entfernt.
- **0.5.1** (Debug): **Migrations-Framework** — `fallbackToDestructiveMigration()` ENTFERNT, echte Room-Migrationen (`Migrations.kt`, v1→v5 dokumentiert) registriert. `exportSchema=true`, Schema-JSON nach `app/schemas/` (5.json committet). Backup-Sicherheitsnetz: vor jedem Öffnen `todonotes.db` → `todonotes.db.bak-v<version>` kopieren (Best-Effort). Ab v6 aufwärts: jede Schema-Änderung bekommt eine echte `Migration(N,N+1)` — KEIN Datenverlust mehr.
- **0.6.1** (Debug): **Block D2 — Android-Sync-Client + Profil/Settings-UI.** Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization. SyncManager (sammelt alle lokalen Zeilen → POST /sync → spielt Server-Änderungen ein, REPLACE). SyncWorker (WorkManager periodisch 15min, nur mit Netzwerk). SyncPrefs (serverUrl/token/lastSyncedAt/clientId in SharedPreferences). TopAppBar mit Profil-Icon → ProfileSheet (ModalBottomSheet) → SettingsScreen mit 4 Sektionen: Verbindung (Server-URL, Token mit zeigen/verbergen, Speichern/Testen/Sync, Status), Benachrichtigungen (POST_NOTIFICATIONS, Akku-Ausnahme, exakte Alarme), Erscheinungsbild (Design-Stub), Info. Cleartext-HTTP im Debug-Build (`src/debug/AndroidManifest.xml usesCleartextTraffic=true`) für LAN-Tests gegen 192.168.x.x. Repo auf GitHub **public** (Account `theoretischer`). **Live validiert**: App sync-ed 5 habits + 1 todo + 3 habit_history (inkl. soft-deletes) erfolgreich gegen echten Server.
- **0.7.0** (Debug): **Block F1–F6 — Notiz-App (Datenmodell bis Drag/Reorder).** F1: `notes` + `folders` Tabellen (DB v6, `MIGRATION_5_6`, `6.json`), Backend um beide Tabellen erweitert. F2: `NoteDao`/`FolderDao`, Repositories, `NotesViewModel` (Ordner-Traversal + Breadcrumb). F3: Rich-Text-Modell (`NoteTextBody`, JSON-Blöcke: Paragraph/ListBlock/Checkbox). F4: Notes-Tab mit Ordner- & Notiz-Übersicht, Breadcrumb, FAB `+`-Menü (Neue Notiz/Ordner). F5: Vollbild-Editor mit Inline-Formatierung (Fett/Kursiv/Unterstrichen) + Listen (Überfällige, Checkbox-Toggle). F6: **1D-Reorder** (Long-Press + vertikal ziehen sortiert Notizen/Ordner, Live-Vorschau ohne Ghost) + **Draufziehen verschiebt in Ordner** + `position`-Spalte (DB v7, `MIGRATION_6_7`, `7.json`). FAB-Fix über alle Tabs. Unit-getestet, auf Gerät validiert.

## Erledigte Blöcke
- [x] **Block A – Todos**: erstellen/erledigen/bearbeiten/swipe-delete, Recurrence (RFC 5545), Option-C-Bugfix
- [x] **Block B – Gewohnheiten**: Cadence (n mal pro Tag/Woche/Monat/Jahr/n Tage), Goal-Count-Tracking, Perioden-Reset, Verlauf pro Periode, "Periode abschließen" im ⋮-Menü
- [x] **Migrations-Framework**: echte Room-Migrationen statt destructive, Schema-JSON, Backup-Sicherheitsnetz
- [x] **Block D1 – Sync-Server**: Docker + FastAPI + SQLite, `/sync` mit Last-Write-Wins, Token-Auth, alle 4 Tabellen, lokal validiert
- [x] **Block D2 – Android-Sync-Client**: Retrofit + OkHttp + kotlinx.serialization, SyncManager (sammelt lokal → POST → merged server), SyncWorker (WorkManager alle 15min), SyncPrefs (URL/Token/last_synced_at/clientId), Profil-Icon oben rechts + ProfileSheet + SettingsScreen (Verbindung, Benachrichtigungen, Erscheinungsbild, Info). Cleartext-HTTP im Debug-Build für LAN-Tests. **Live validiert**: App hat 5 habits + 1 todo + 3 habit_history (inkl. soft-deletes) gegen echten Server synchronisiert. Repo auf GitHub public.
- [x] **Block F1–F2 – Notizen: Datenmodell/DAO/Repo/VM**
- [x] **Block F3 – Rich-Text-Modell** (`NoteTextBody`, JSON-Blöcke, Listen/Checkbox)
- [x] **Block F4 – Notes-Tab**: Ordner- & Notiz-Übersicht, Breadcrumb, FAB `+`-Menü
- [x] **Block F5 – Notiz-Editor**: Vollbild, Inline-Formatierung (Fett/Kursiv/Unterstrichen), Listen (Präfix-Autovervoll, Checkbox-Toggle)
- [x] **Block F6 – Reihenfolge sortieren + in-Ordner-Verschieben**: 1D-Reorder per Long-Press-Drag (Live-Vorschau ohne Ghost, `position`-Spalte DB v7), Notiz auf Ordner ziehen = verschieben, FAB-Fix über alle Tabs

## Offen
- [ ] **Block D2b – Sync-Trigger verbessern**: Sync bei Datenänderung (sofort nach Speichern/Loggen), beim App-Start, Pull-to-Refresh — damit nicht manuell oder 15min-WM getriggert werden muss
- [ ] **Block D3 – Deploy finalisieren**: Reverse-Proxy (Caddy) für `todo.christopherh.de` + HTTPS, dann App auf HTTPS-URL umstellen
- [~] **Block E – Linux-Client** (EINGEFROREN): GTK4-Prototyp läuft (E1 + E5a/E5b teilweise), aber Ansatz verworfen → CMP-Migration ersetzt ihn. Siehe `MIGRATION-CMP.md`.
- [ ] **Block F – Notizen** (Samsung-Notes-Style, eigener Tab mit echter Notiz-App, keine Todo-Sonderform):
  - ✅ **erledigt – F1–F6**: Datenmodell/Migration (DB v6/v7), DAOs/Repo/VM, Rich-Text-Modell, Notes-Tab (Ordner/Breadcrumb/FAB), Editor (Inline-Formatierung + Listen), 1D-Reorder + in-Ordner-Verschieben. Details siehe „Erledigte Blöcke" oben.
  - **F7 – Bilder einfügen**
    - Im Editor: Toolbar-Button „Bild" → Photo-Picker (Android `PickVisualMedia`) oder Kamera (optional später).
    - Bild wird dekodiert, ggf. downgesampelt (max ~1600px Kantenlänge), PNG gespeichert unter `files/notes/<noteId>/<uuid>.png`, `ImageBlock` im Body eingefügt.
    - Rendering im Editor via `AsyncImage` (Coil), Skalierung an Notizbreite.
  - **F8 – Stift/Stylus-Support (Zeichnen auf Notiz)**
    - Pro Notiz optionale „Zeichnung" (Drawing-Layer): eigener Canvas, Pfade als Liste von `Stroke(points, color, width)` im Notiz-Body als `DrawingBlock` gespeichert.
    - Stift-Toolbar: Farbe (Palette), Strichstärke (3-4 Presets), Radiergummi, Rückgängig.
    - `S-Pen`/generischer Stylus: `PointerEvent` mit `PointerType.Stylus` erkennen, Pressure → Strichstärke (optional), Palm-Rejection (nur Stylus-Eingabe zeichnen, wenn Stylus aktiv).
    - Zeichnung als PNG rendern + als `ImageBlock` für Sync speichern ODER Pfade direkt im Body (besser für Editierbarkeit) → Entscheidung bei Implementierung.
  - **F9 – Bild-Sync** (nach F7/F8, wenn Text-Sync steht)
    - Server-Endpoint `/sync/images` (multipart) ODER Bilder als Base64-Chunks im `/sync` (einfacher, aber größer). Entscheidung: eher separates `/images/{noteId}/{imageId}` GET + `POST /sync/images` mit multipart, referenziert im `bodyJson`.
    - Client: beim Sync pro Notiz prüfen, welche `imageId`s lokal fehlen → GET nachladen; lokale neuen → hochladen.
  - **F10 – Sync-Anbindung**
    - `notes` + `folders` in `SyncManager` aufnehmen (wie Todos/Habits: sammeln → POST → merge).
    - `last_synced_at` gilt weiterhin global für alle Tabellen.
    - Konflikt-Strategie: LWW über `updatedAt` (wie bisher). Body-Konflikte (gleichzeitige Edits) → letzter Schreiber gewinnt, akzeptiert für Ein-Nutzer.
  - **F11 – Polish**
    - Samsung-Notes-Look: weiße „Papier"-Notiz-Liste, dezente Trennlinien, Titel-Fett, Vorschau-Zeilen (erste ~2 Body-Zeilen als Preview in der Listenkarte).
    - Leerer Zustand: „Keine Notizen — tippe + um eine zu erstellen".
    - Suche (optional, später): filtert Titel + Body-Text.
  - **F12 – FAB-Polish** (klein, „while you're at it")
    - Notizen-FAB: „Neu"-Text entfernen → nur Plus-Icon (wie die anderen 3 Tabs, `FloatingActionButton` statt `ExtendedFloatingActionButton`).
    - FAB-Abstand vereinheitlichen: aktuell ist der Abstand FAB→BottomNavigationBar viel zu groß (doppeltes Padding: `Box.padding(bottom=…)` hebt bereits über die NavBar, plus `navigationBarsPadding()` auf dem FAB = doppelt). Fix: `navigationBarsPadding()` von allen 3 FABs (Todos/Habits/Notes) entfernen → FAB sitzt 16dp über der NavBar = 16dp vom rechten Rand (symmetrisch).
- [ ] **Block G – Design-Politur**: Samsung-Reminder-Look-and-Feel
- [x] **Block H – Chat-Dateien (WhatsApp-Style Tracking-Notizen)** ✅ FERTIG — H1-H5 + H-Quote + IME-Inset-Fix. Siehe Commits 930d909, 27af5b1, 5ef0228.
  - **H1 – Datenmodell & Migration (DB v8)**
    - Neue Entität `ChatMessage` (id, noteId, text, createdAt, updatedAt, deletedAt, position) — eigene Tabelle, NICHT im note.bodyJson. Grund: jede Nachricht braucht ihr eigenes unveränderliches `createdAt` (bleibt beim Bearbeiten gleich), `updatedAt` für LWW-Sync.
    - `Note` um Spalte `type` erweitern (`"NOTE"` default vs `"CHAT"`) — steuert, ob beim Tap der Text-Editor (F5) oder der Chat-Screen (H3) öffnet.
    - Echte Room-Migration `MIGRATION_7_8`: `chat_messages`-Tabelle + `notes.type`-Spalte, `8.json` Schema exportieren.
    - Migrationstest (`MigrationTest.kt`) ergänzen.
  - **H2 – Backend & Sync für Chat-Nachrichten**
    - `chat_messages`-Tabelle im Backend-Schema (`db.py`).
    - `ChatMessageDTO` (Kotlin + Python), in `ChangesBundle` aufnehmen.
    - `_SYNC_TABLES`-Eintrag (change_field = `updatedAt`, LWW wie notes).
    - SyncManager: `ChatMessage` sammeln → POST → merge (analog zu notes/folders).
  - **H3 – ChatScreen UI (WhatsApp-Style)**
    - Vollbild-Screen (`ChatScreen.kt`), eigenes ViewModel (`ChatViewModel`).
    - `LazyColumn` mit Nachrichten: älteste oben, neueste unten.
    - Beim Öffnen + nach Senden auto-scroll ganz nach unten (bei den neuesten).
    - Jede Nachricht: Text + Datum/Uhrzeit-Label (z.B. „14:32 · 02.08.").
    - Eingabefeld unten + Senden-Button (PaperPlane-Icon). Enter/Tippen → Nachricht absetzen → `ChatMessage(createdAt=now)` ans Ende, Feld leeren.
    - `imePadding()` damit die Tastatur die Liste nicht verdeckt.
  - **H4 – Integration in Notizen-Tab**
    - FAB-Menü in NotesScreen: 3. Eintrag „Chat" (oder passender Name, z.B. „Tagebuch"/„Log") neben „Neue Notiz"/„Neuer Ordner".
    - `NoteRow`: Chat-Notiz anderes Icon (z.B. `Icons.Filled.Chat`/`QuestionAnswer`) als normale Notiz.
    - Tap auf Chat-Note → `ChatScreen` statt `NoteEditorScreen` (Routing in MainActivity via `note.type`).
    - Reorder/Move/Swipe-Delete gelten für Chat-Notizen genauso (sie sind ja Notes mit `type=CHAT`).
  - **H5 – Bearbeiten & Löschen von Nachrichten**
    - Nachricht bearbeiten: Tap/Langdruck → Inline-Edit oder Dialog → nur `text` + `updatedAt` ändern, `createdAt` bleibt (Datum/Uhrzeit bleibt unverändert).
    - Löschen: Swipe-to-Delete (`SwipeToDeleteRow` wie bei Notizen/Habits) → Soft-Delete (`deletedAt`), bleibt in der Liste bis Refresh, sync-fähig.
    - Optional: eigene Nachricht löschen vs ganze Chat-Datei löschen (Letzteres läuft über die Notiz-Liste wie bei allen Notes).

## Wichtige Entscheidungen
- **Option C**: nächstes Vorkommen ab `fromDue` (dueAt), nicht `now`
- **4 Tabs**: Aufgaben · Gewohnheiten · Notizen · Verlauf
- **Habit-Cadence simplified**: keine RFC 5545, nur DAY/WEEK/MONTH/YEAR/NDAYS + goalCount
- **Migrations**: ab v5 nur noch echte `Migration(N,N+1)`, nie wieder destructive
- **Sync**: ein `/sync`-Call für alle Tabellen, Last-Write-Wins über `updated_at`, mehrere Clients (Tower + Laptop + Handy)
- **Backend-Stack**: Docker + Python/FastAPI + rohes sqlite3 + Pydantic (kein ORM)
- **Auth**: ein Bearer-Token (Shared Secret in `.env`)
- **Git**: Monorepo auf GitHub (private), Account `theoretischer`, Commit-Email `75859777+theoretischer@users.noreply.github.com` (für Contribution-Graph)

## Nächster Schritt

**🔔 Compose Multiplatform (CMP) Migration — Phase M5 fertig**

**Status:**
- M0 ✅ Backup + Linux verschoben
- M1 ✅ Backend Multi-User-Auth (users/tokens, userId auf alle Tabellen, Legacy-Migration, /auth/* Endpunkte)
- M2 ✅ KMP-Skeleton (commonMain/androidMain/wasmJsMain/desktopMain), alle 3 Targets bauen
- M3 ✅ Logik nach commonMain (HabitEngine, NoteTextBody, SyncDTOs, Entities, RecurrenceEngine expect/actual), 39 Tests grün
- M4 ✅ Room KMP (Room 3.0, Entities+DAOs+Migrations in commonMain, expect/actual DatabaseBuilder, Migration v9→v10)
- M5 ✅ Networking (Ktor 3.5.2 statt Retrofit, SyncManager+AuthManager in commonMain, multiplatform-settings)
- M6 ✅ RRULE auf Web (RecurrenceCalculator erweitert, 14 Parity-Tests vs lib-recur alle grün)
- M7a ✅ Fundament (Repositories + AppContainer in commonMain, AlarmScheduler expect/actual)
- M7b ✅ Theme + TodoNotesApp-Gerüst + TodosScreen (RecurrenceEditor, TodoEditDialog, SwipeToDeleteRow, material-icons)

Backend läuft neu in Docker, alte Android-App funktioniert weiter (Static-Token → Legacy-User).
Plan: `MIGRATION-CMP.md`.

**Nächster Schritt: M7c — HabitsScreen + HistoryScreen"**
