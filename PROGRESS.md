# TodoNotes – Progress

**Phase: Backend/Sync (Block D) – Server steht, Android-Client folgt**

## Builds
- **0.1.0** (Debug): MVP – Todos erstellen/erledigen, einfache Liste.
- **0.2.0** (Debug): Block A1–A3. Todo bearbeiten (Tap → Edit-Dialog), Swipe-to-Delete (Soft Delete), Recurrence-Picker im Create-Dialog (Grundtypen + Intervall).
- **0.3.0** (Debug): Block A4–A5. Erweiterter RRULE-Editor (Samsung-Style: vertikale Radio-Liste, Wochentag-Chips, 3 Monatsmodi, Laufzeit-Sektion). RFC 5545 via `dmfs/lib-recur 0.17.1` (RecurrenceEngine mit COUNT/BYDAY/BYMONTHDAY/BYSETPOS).
- **0.3.1** (Debug): Option-C-Bugfix. Nächstes Vorkommen ab `fromDue` (dueAt), nicht `now`. Sonntag fällig, Sonntag erledigt → nächstes Vorkommen Dienstag. Unit-tests grün.
- **0.4.0** (Debug): Block B1–B7. Neuer Tab „Gewohnheiten" (4 Tabs: Aufgaben · Gewohnheiten · Notizen · Verlauf). Habit + HabitLog Entities (DB v2), HabitEngine mit Periodenstart/Reset-Logik (5 Unit-Tests), HabitRepository/ViewModel, HabitCadencePicker (vereinfacht: n mal pro Tag/Woche/Monat/Jahr/n Tage, inline-n-Feld), HabitsScreen mit HabitCard (0/n + Progress-Balken + +). RecurrenceEditor der Todos auf inline-n umgestellt.
- **0.5.0** (Debug): Block B8–B11. Zeitraum-Labels ("n mal pro X"), Header "Zeitraum", Wochentag-Auswahl entfernt (Reset-Tag = Startdatum). Habit-Verlauf pro Periode: HabitHistoryEntry (DB v5), automatischer History-Eintrag bei Periodenwechsel, Haken "In Verlauf eintragen". "Periode abschließen" pro Gewohnheit im ⋮-Menü. Verlauf-Tab mit Sektionen Gewohnheiten + Aufgaben. Bugfixes: byWeekdays NOT-NULL (Feld entfernt), CASCADE beim upsert (→ @Update). TopAppBar bei Gewohnheiten entfernt.
- **0.5.1** (Debug): **Migrations-Framework** — `fallbackToDestructiveMigration()` ENTFERNT, echte Room-Migrationen (`Migrations.kt`, v1→v5 dokumentiert) registriert. `exportSchema=true`, Schema-JSON nach `app/schemas/` (5.json committet). Backup-Sicherheitsnetz: vor jedem Öffnen `todonotes.db` → `todonotes.db.bak-v<version>` kopieren (Best-Effort). Ab v6 aufwärts: jede Schema-Änderung bekommt eine echte `Migration(N,N+1)` — KEIN Datenverlust mehr.
- **0.6.1** (Debug): **Block D2 — Android-Sync-Client + Profil/Settings-UI.** Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization. SyncManager (sammelt alle lokalen Zeilen → POST /sync → spielt Server-Änderungen ein, REPLACE). SyncWorker (WorkManager periodisch 15min, nur mit Netzwerk). SyncPrefs (serverUrl/token/lastSyncedAt/clientId in SharedPreferences). TopAppBar mit Profil-Icon → ProfileSheet (ModalBottomSheet) → SettingsScreen mit 4 Sektionen: Verbindung (Server-URL, Token mit zeigen/verbergen, Speichern/Testen/Sync, Status), Benachrichtigungen (POST_NOTIFICATIONS, Akku-Ausnahme, exakte Alarme), Erscheinungsbild (Design-Stub), Info. Cleartext-HTTP im Debug-Build (`src/debug/AndroidManifest.xml usesCleartextTraffic=true`) für LAN-Tests gegen 192.168.x.x. Repo auf GitHub **public** (Account `theoretischer`). **Live validiert**: App sync-ed 5 habits + 1 todo + 3 habit_history (inkl. soft-deletes) erfolgreich gegen echten Server.

## Erledigte Blöcke
- [x] **Block A – Todos**: erstellen/erledigen/bearbeiten/swipe-delete, Recurrence (RFC 5545), Option-C-Bugfix
- [x] **Block B – Gewohnheiten**: Cadence (n mal pro Tag/Woche/Monat/Jahr/n Tage), Goal-Count-Tracking, Perioden-Reset, Verlauf pro Periode, "Periode abschließen" im ⋮-Menü
- [x] **Migrations-Framework**: echte Room-Migrationen statt destructive, Schema-JSON, Backup-Sicherheitsnetz
- [x] **Block D1 – Sync-Server**: Docker + FastAPI + SQLite, `/sync` mit Last-Write-Wins, Token-Auth, alle 4 Tabellen, lokal validiert
- [x] **Block D2 – Android-Sync-Client**: Retrofit + OkHttp + kotlinx.serialization, SyncManager (sammelt lokal → POST → merged server), SyncWorker (WorkManager alle 15min), SyncPrefs (URL/Token/last_synced_at/clientId), Profil-Icon oben rechts + ProfileSheet + SettingsScreen (Verbindung, Benachrichtigungen, Erscheinungsbild, Info). Cleartext-HTTP im Debug-Build für LAN-Tests. **Live validiert**: App hat 5 habits + 1 todo + 3 habit_history (inkl. soft-deletes) gegen echten Server synchronisiert. Repo auf GitHub public.

## Offen
- [ ] **Block D2b – Sync-Trigger verbessern**: Sync bei Datenänderung (sofort nach Speichern/Loggen), beim App-Start, Pull-to-Refresh — damit nicht manuell oder 15min-WM getriggert werden muss
- [ ] **Block D3 – Deploy finalisieren**: Reverse-Proxy (Caddy) für `todo.christopherh.de` + HTTPS, dann App auf HTTPS-URL umstellen
- [ ] **Block E – Linux-Client**: GTK4, synchronisiert gegen denselben Server (Tower + Laptop)
- [ ] **Block F – Notizen** (Samsung-Notes-Style, eigener Tab mit echter Notiz-App, keine Todo-Sonderform):
  - **F1 – Datenmodell & Migration (DB v6)**
    - Neue Tabelle `notes`: `id (UUID)`, `folderId (UUID nullable)`, `title (string)`, `bodyJson (TEXT — serialisierter Rich-Text-Baum, siehe F3)`, `createdAt`, `updatedAt`, `deletedAt (soft delete für Sync)`.
    - Neue Tabelle `folders`: `id (UUID)`, `parentId (UUID nullable — für Ordner-in-Ordner)`, `name (string)`, `createdAt`, `updatedAt`, `deletedAt`.
    - Bilder werden **nicht** als Base64 in `bodyJson` gespeichert (bläht DB/Sync auf), sondern als Dateien im App-Internal-Storage (`files/notes/<noteId>/<imageId>.png`) und im `bodyJson` nur als Referenz `{type:image, imageId, width, height}`. Sync transportiert Bilder separat (später F9).
    - Migration `MIGRATION_5_6` (echte Room-Migration, Schema `6.json`).
    - Backend: `notes` + `folders` Tabellen + DTOs + `/sync` erweitern (LWW wie bisher).
  - **F2 – DAOs, Repository, ViewModel**
    - `NoteDao` (CRUD, observe by folder, observeAll für Sync), `FolderDao` (CRUD, observe tree).
    - `NoteRepository`, `FolderRepository`, `NotesViewModel` (State: aktuelle Ordner-Hierarchie + Notiz-Liste im aktuellen Ordner, Traversal-State).
  - **F3 – Rich-Text-Modell (eigenes kleines Format, kein externer Editor)**
    - Datenmodell: Notiz-Body = geordnete Liste von `Block`-Elementen, jeder Block einer von:
      - `Paragraph(segments: List<Segment>)` — Segment = `{text, style}` mit style = bold/italic/underline/none, fontSize (pt), color (argb).
      - `ListBlock(items: List<ListItem>, listType)` — listType = `ORDERED (1. 2. 3.)` | `BULLET (•)` | `ARROW (→)` | `CHECKBOX (☐/☑)`. ListItem = `{segments, checked: bool}`.
      - `ImageBlock(imageId, width, height, caption?)`.
    - Serialisierung als JSON via kotlinx.serialization (kompakt, sync-freundlich).
  - **F4 – Notes-Tab: Ordner- & Notiz-Übersicht**
    - Hauptansicht: Liste der Ordner + Notizen im aktuellen Ordner. Breadcrumb-Pfad oben (Wurzel › Ordner › Unterordner).
    - FAB `+`: Menü „Neue Notiz" / „Neuer Ordner" (Ordner-Name per Dialog).
    - Tippen auf Ordner → navigiert rein. Tippen auf Notiz → öffnet Editor (F5).
    - Long-press auf Ordner/Notiz → Multi-Select + Drag (F6) + Löschen + Verschieben.
  - **F5 – Notiz-Editor (Text + Formatierung)**
    - Vollbild-Editor. Erste Zeile = Titel (auto, groß fett), Rest = Body.
    - Format-Toolbar unten (kontextsensitiv bei Selektion): Bold, Italic, Underline, Schriftgröße (Spinner/Slider), Schriftfarbe (Color-Picker), Listentyp-Umschalter (geparst aus aktueller Zeile).
    - Listen: neue Zeile in einer Liste → automatisch nächstes Präfix (`1.`, `•`, `→`, `☐`); `☐` tappen toggelt `☑` und graut Zeile durch.
    - Speichern bei Back/Verlassen (auto-save, `updatedAt` aktualisieren).
  - **F6 – Reihenfolge sortieren (1D-Drag)**
    - Umsetzung als **eindimensionales Reorder** statt 2D-Drag&Drop: Long-Press auf eine Zeile und dann hoch/runter ziehen tauscht die Reihenfolge von Notizen (unter Notizen) bzw. Ordnern (unter Ordnern). Die anderen Zeilen werden dabei live „zur Seite geschoben" — ohne Ghost-Overlay.
    - Datenmodell: neue Spalte `position` in `notes` + `folders` (DB v7, `MIGRATION_6_7`). Sortierung in Room jetzt `ORDER BY position ASC` (statt bisher `updatedAt DESC` / `name ASC`).
    - Beim Tausch wird die komplette Ordner-Liste neu normalisiert (Indizes × 10), damit der Reorder auch bei Alt-Daten (alle `position` 0) greift und über App-Neustart persistent bleibt.
    - Gesten-Platzierung: Long-Press-Drag sitzt am innersten Content (`contentModifier` von `SwipeToDeleteRow`), damit er gegen das horizontale Swipe-Delete gewinnt. Schwellwert = halbe Zeilenhöhe für natürliches Ziehen.
    - **In-Ordner-Verschieben:** Eine Notiz, die man beim Ziehen über einem Ordner loslässt, wird in diesen Ordner verschoben (Hit-Test gegen die globalen Ordner-Bounds beim Drop). Hoch/runter in der Leere bleibt reines Reorder.
    - Reine Reorder-Logik in `ReorderLogic.kt` (`reorderStep`), Unit-getestet.
    - FAB-Fix: MainActivity–Content wird jetzt unten mit Scaffold-Padding belegt, damit die FABs aller Tabs nicht mehr hinter der Navigationsleiste verschwinden.
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
- [ ] **Block G – Design-Politur**: Samsung-Reminder-Look-and-Feel

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
**Block F1 – Notizen: Datenmodell & Migration (DB v6).** Neue Tabellen `notes` + `folders`, echte Room-Migration `MIGRATION_5_6`, Backend-Sync um die beiden Tabellen erweitern. Danach F2 (DAO/Repo/VM) → F3 (Rich-Text-Modell) → F4 (Tab-Übersicht) → F5 (Editor) → F6 (Drag&Drop) → F7 (Bilder) → F8 (Stylus) → F9 (Bild-Sync) → F10 (Sync) → F11 (Polish).
