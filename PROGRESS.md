# TodoNotes – Progress

**Stand: App ist LIVE und vollständig synchron** — Android-App + Web-App über
`https://todo.christopherh.de`, Echtzeit-Sync in ~50–100ms.

**Session 2026-11-20:** Sync-FIX bestätigt, Bug-Liste (Nr. 1–11) abgearbeitet,
M11-Verlaufsfix (+ Migrations-Feuerwehr v13→14), M12 Links klickbar,
M13 Quick-Add-Bar (FAB ersetzt, IME weich), M14 UI-Rework mit eigenem
Farbsystem (Dynamic Color aus), Deploy-Script 60s→35-60s (yarn-Fix).
88 Tests grün.

88 Tests grün.

**Session 2026-11-23:** Bug-Session 2 — Bug 12 (OLED nachts dank
FLAG_KEEP_SCREEN_ON im Vollbild-Alarm), Bug 13 (Notification-Style
geriet beim Anlegen der nächsten Occurrence unter — Wiederholung
fiel auf Vollbild zurück), Bug 14+14b (`[]`-Datenverlust:
migrateFromBlocks hielt `[]` für leeres Block-JSON, und der Fix
trennte zuerst den Legacy-[]-Leer-Marker nicht sauber ab —
jetzt: exakt `[]`/`{}` = Leer-Marker, decodbare Blöcke = JSON,
sonst Plaintext). Revert M14c (Flächen-Tausch — sah vorher besser
aus). M14d: Notiz-Titel in die TopBar wie Chat-Notizen
(Konsistenz). M14e: Titelzeilen-/Label-Konsistenz über alle
Tabs (ScreenTitle überall, blaue CAPS-Labels einheitlich,
Breadcrumb in die Liste, HOME klickbar). M14f: Eckenradius-
System (16dp Karten, 28dp Dialoge, 8dp Abstand) inkl. drei
Fixes (Wrapper-Hintergrund kassierte Rundung, rote Lösch-
Box, Todo-Karten surface statt background). M14g: gefüllte
Eingabefelder überall, runde Checkboxen, Dialog/Sheet-
Farben = surface. M14h/i: Editor-BottomBar, DatePicker-
Farben (auch im Kalender-Grid!), Recurrence-Editor als
Stepper + Segment-Chips. M14j: Todo-Listen-Abstand 8dp +
HabitCadencePicker auf dasselbe Chip-System. M14k–p: Feinschliff-
Serie (Progress-Dot, Chat-Link, Switch/Avatar, Gefahrenzone,
Tracker-Karte, Datum-Labels, Feld-Farben, Stepper-Zeilen,
Intervall-Anzeige + Engine-Tests für interval>1). Bug-Hunting 3:
Editor-Hitbox, Cursor-Sichtbarkeit (3 Versuche: selbst messen +
animiert scrollen), „Diese Woche“-Grenze + Tests, Ripple-Clipping,
scrollende Titel, neues App-Icon v2 (Android Adaptive + Web-
Favicon). **Feature-Liste für den ernsthaften Alltags-Einsatz
KOMPLETT abgehakt.** Danach: Alarm-Screen im M14-Stil + M16 „Teilen
mit der App“ (ACTION_SEND → Prefill/Notiz-Anhäng, finish statt
moveTaskToBack, Share-Append-Datenverlust-Bug auf Textebene
gefixt, BottomBar in der Session versteckt). 109 Tests grün.

## Aktueller Stand

- **Android** (natürlich installiert): Todos, Habits, Notizen (Rich-Text +
  Reorder + Ordner), Chat-Notizen, Verlauf, Einstellungen, Alarme mit
  Benachrichtigungs-Stil pro Todo (Vollbild / Benachrichtigung / Stumm)
- **Web** (Wasm, vom Backend ausgeliefert): gleiche Features, persistente
  lokale DB (OPFS), Server-URL wird automatisch von der Seiten-Adresse
  übernommen
- **Backend**: FastAPI + SQLite in Docker, Multi-User-Auth (Setup-Gate,
  Admin-Panel, Avatar), SSE für Echtzeit-Push
- **Desktop** (JVM): baut und läuft, Feinschliff fehlt (→ M10)
- Alte native Android-App (Retrofit/WorkManager) und GTK4-Linux-Client: durch
  CMP ersetzt, Quellcode liegt archiviert im Repo (`android/` Altbestand)

## Deployment (Produktiv)

| Was | Wie |
|---|---|
| Backend | Server: `git pull && docker compose up -d --build` (in `backend/`) |
| Web-App | Lokal: `./deploy-web.sh` → committen → pushen → Server wie oben |
| Domain | `todo.christopherh.de` via Nginx Proxy Manager (HTTPS, HSTS, HTTP/2, Force-SSL) |
| Lokale Entwicklung | Backend `:8001`, Web `:8090` (`/tmp/opfs_server.py`), `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` |

Details/Architektur: **`MIGRATION-CMP.md`** (technische Referenz) und
**`WORKFLOW.md`** (Arbeitsablauf).

**Aktuelle offene Punkte:** siehe unten „Offene Punkte (Stand nach
Bug-Fix-Session)".

## Erledigt (Kurzfassung)

Vollständige Historie in `MIGRATION-CMP.md` (M1–M9) und Git-Historie.

- Block A–H der Original-App — ursprünglich native Android, im
  CMP-Projekt neu aufgebaut
- CMP-Migration M1–M9 (Backend-Auth, KMP, Room KMP, Ktor, RRULE-Web,
  komplette UI in commonMain, Wasm + OPFS)
- M8 Alarme + Benachrichtigungs-Stile, Android-Back-Handler (Ordner/Settings)
- Echtzeit-Sync: Auto-Sync + SSE, Ping-Pong-Vermeidung, LWW + Clock-Skew,
  Sync-Perf (~50–80ms gemessen)
- Produktions-Deploy: Docker, Domain + HTTPS via NPM, Web-Dist im Repo
  (`deploy-web.sh`), COOP/COEP-Header, Self-Healing bei falscher Server-URL
- Sync-A+ Notiz-Sync bombenfest (reaktiver Editor, safe flush)
- Sync-FIX Full Up/Down-Sync + Anti-Resurrektion — vom Nutzer bestätigt
- Bug-Fix-Session: 10 Bugs/Features (Editor-Lese-/Edit-Modi, Re-Arm,
  2:00-Uhr, Checkbox-Datenverlust, Instant-Swipe, Tap-Position …)

## Sync-A+ – Notiz-Sync bombenfest (Erledigt)

**Problem:** Notiz-Sync war datenverlustgefährdet. Konkrete Bugs:
1. Editor war "blind" für Sync-Änderungen → beim Verlassen wurde die
   DB-Version überschrieben (evtl. gelöschte Notiz = Datenverlust).
2. `flush()` nutzte `getById` (inkl. `deletedAt`) → Body in gelöschte Notiz.
3. `collectLocalChanges()` schickte ALLE Notizen jedes Mal (Race-Conditions).
4. `@Upsert` generiert auf Wasm `EntityUpsertAdapter`, der bei PK-Konflikt
   INSERT → Exception → UPDATE macht → **Transaktionsleck**
   ("cannot start a transaction within a transaction") → Sync auf Web
   komplett tot. Notiz-Erstellung/Updates kamen nie an.

**Lösung (Ansatz A+, evolutionär — kein OT/CRDT):**

### Reaktiver Editor (Live-Mitsehen)
- `NoteDao.observeNote(id): Flow<Note?>` — Editor beobachtet Notiz.
- `NoteEditorViewModel` sammelt `observeNote(id)`. Kommt Sync-Update rein:
  - Editor nicht dirty → Body/Titel übernehmen → **Live-Mitsehen am Handy!**
  - Editor dirty → skip (Nutzer gewinnt, LWW Stufe 1).
  - Notiz gelöscht → `state.deleted = true` → Editor schließt sich.
- `NoteEditorState`: `deleted` + `remoteUpdate` Felder.
- `NoteEditorScreen`: `LaunchedEffect(state.deleted)` → Editor schließt.
  `LaunchedEffect(state.remoteUpdate)` → Body neu parsen, ABER nur wenn
  Nutzer nicht aktiv tippt (kein Cursor-Sprung).
- Auto-Save 500ms (vorher 1s) für Live-Gefühl.

### Safe Flush (kein Datenverlust)
- `NoteDao.updateBody(id, title, bodyJson, now)` — sicheres Update mit
  `WHERE deletedAt IS NULL` → gelöschte Notizen werden NICHT überschrieben.
- `NoteRepository.updateNote()` nutzt `updateBody` (kein `note.copy()`).
- `flush()` bricht ab wenn `state.deleted` (kein Überschreiben).

### Atomare Sync-Upserts (Wasm-Transaktionsleck-Fix)
- `@Upsert` ersetzt durch atomare Single-Statement-Queries:
  - notes/todos/folders/chat_messages: `@Insert(onConflict=REPLACE)`
    (`INSERT OR REPLACE` — atomar, keine FK/CASCADE → sicher).
  - habit_logs/habit_history: `@Insert(onConflict=REPLACE)` (keine Childs).
  - habits: `INSERT(IGNORE)` + `UPDATE` — kein DELETE, kein CASCADE auf
    habit_logs/history (REPLACE würde CASCADE auslösen → Datenverlust).

### Effizienter Sync-Push
- `collectLocalChanges` schickt nur Zeilen mit `updatedAt > lastSyncedAt`
  (todos/habits/notes/folders/chat_messages). Append-only (habit_logs/
  history) bleiben alle (klein, Server skippt Duplikate).
- `getSince(since)` Queries in TodoDao, HabitDao, NoteDao, FolderDao,
  ChatMessageDao.

### Garantien (nach A+):
- **Kein Datenverlust durch Überschreiben gelöschter Notizen.**
- **Live-Mitsehen** am Handy (Body poppt auf, wenn PC speichert).
- **Editor schließt bei gelöschter Notiz** statt zu überschreiben.
- **Sync auf Web funktioniert** (kein Transaktionsleck mehr).
- **Sync-Push effizienter** — nur geänderte Notizen.

### Nicht umgesetzt (bewusst):
- Kein OT (Google-Docs-Style) — overkill, braucht WebSocket + komplexe
  Transformationslogik.
- Kein CRDT (Yjs/Automerge) — JS-Bibliotheken, Interop-Komplexität,
  Document-Größen-Wachstum.
- Keine echten Dateien statt DB — verliert Multi-User/atomare Queries,
  löst das LWW-Problem nicht.
- Feld-Level-LWW (Stufe 2) — später optional, wenn real auftretend.

## Security: Java4Shell CVE-2026-114938 (Erledigt)

**Lücke:** Base64-Decoder in OpenJDK 17.0.6+ nutzt AVX-512 VBMI Intrinsics
auf x86-64. Malformed Base64-Input → 62-byte Heap-Overflow → RCE (pre-auth).
CPU (AMD Zen 4) hat `avx512vbmi` → anfällig.

**Betroffen (nur Build-Toolchain, nicht Runtime):**
- Gradle nutzt java-17.0.21 (24.10.2026, vor Patch 17.11.2026)
- Desktop-Distribution nutzt System-Java 27 (vor Patch)
- Backend (Python) NICHT betroffen
- Android-APK / Wasm-App NICHT betroffen (keine JVM-Runtime)

**Fix:** `-XX:UseAVX=2` deaktiviert AVX-512, behält AVX2.
- `gradle.properties` (compose-app + android): `org.gradle.jvmargs += -XX:UseAVX=2`
- `build.gradle.kts`: TODO M10 jpackage bundled JRE patchen
- **Server: nichts zu tun** — Backend ist reines Python:3.12-slim Docker-Image,
  kein Java im Container. Wasm-App läuft im Browser. (Commit `e251ede`)

**Empfehlung:** JDK updaten auf ≥17.0.22 (patched) sobald verfügbar, dann
`-XX:UseAVX=2` wieder entfernen.

## Sync bombensicher — Full Up/Down-Sync (Erledigt ✓ vom Nutzer bestätigt)

**Problem:** Todos/Habits/Folders/Chat verschwanden nach Web-Reload und
tauchten scheinbar random wieder (auch alte Wipe-Daten). Notizen
funktionierten (reaktiver Editor).

**Ursache:** `newSyncedAt = server_now + 1`. Server setzte angewendete
Rows auf `updatedAt = server_now`, gab aber `newSyncedAt = server_now + 1`.
`_collect_server_changes` liefert nur Rows mit `change_col > last_synced_at`.
Ein Client mit `lastSyncedAt >= server_now` (z.B. Ersteller nach Reload)
bekam die Row NICHT mehr — `server_now > server_now+1 = false`. Row war
unsichtbar bis ein anderes Gerät sie editierte. Das `+1` sollte
Re-Delivery an den Ersteller verhindern, brach aber die Zustellung an
jeden Client mit `lastSyncedAt >= server_now`.

**Warum Notizen funktionierten:** Der reaktive Editor beobachtet
`observeNote(id)` direkt und reloadet aus der lokalen DB. Listen-Flows
der Todos/Habits rely auf `lastSyncedAt`-basierte Zustellung.

**Fix (Full Sync, bulletproof):**
1. **DOWN:** Server liefert IMMER alle Rows des Users (full down-sync).
   `_collect_all_server_changes(user_id)`. Selbstheilend: verlorene
   lokale Rows werden bei jedem Sync wiederhergestellt.
2. **UP:** Client pusht IMMER alle Rows (full up-sync, `getAllOnce` statt
   `getSince`). Server LWW-skippt unveränderte (`existing.updatedAt >=
   incoming → skip`).
3. **`newSyncedAt = server_now`** (KEIN +1). `lastSyncedAt` nur noch für
   `wipe_epoch`-Check.
4. **SSE-Notify nur bei `applied_count > 0`** (tatsächlich geänderte
   Rows). Verhindert Ping-Pong UND unnötige Pulls.

`NoteEditorViewModel` sicher: skippt identische Daten. Full down-sync
feuert Flow bei jedem Sync, Editor ignoriert unveränderte Notizen.
Anti-Resurrektion bleibt (einmal gelöscht = gelöscht). (Commit `5d09c70`,
vom Nutzer bestätigt: „alle Dateien sind jetzt in eine Session vereint,
nach direktem Neuladen ist alles immer noch da")

## Bug-Fix-Session (Erledigt ✓ — alles vom Nutzer bestätigt)

Lange Nutzer-Test-Phase → Bugliste durchgearbeitet. Commits `d80236b` … `97947f7`.

### 1. 2:00-Uhr-Bug bei wiederkehrenden Todos (Fix `d80236b`)
**Ursache:** `RecurrenceEngine` (Android/Desktop, lib-recur) baute den
RRULE-Start mit `DateTime(startMillis).toAllDay()` — `DateTime(long)`
nutzt UTC, `toAllDay()` stript die Uhrzeit → Mitternacht UTC = 02:00
CEST. Jede neue Occurrence lief auf 02:00 statt der Original-Zeit.
Wasm (RecurrenceCalculator) war nie betroffen.
**Fix:** `DateTime(TimeZone.getDefault(), startMillis)` — Original-Uhrzeit
bleibt. Regressionstest `time_of_day_is_preserved` (ParityTest) prüft
Uhrzeit-Erhaltung explizit (alter Test verglich nur das Datum → Bug
fiel nie auf).

### 2. Wiederkehrende Todos erinnerten nur 1× (Fix `6d6eca5`)
**Ursache:** `setExactAndAllowWhileIdle` feuert exakt einmal, nächste
Occurrence wurde nur beim ABHAKEN geplant. Nicht abgehakt → nie wieder
Benachrichtigung.
**Fix (Re-Arm-Kette):** `AlarmReceiver` plant beim Feuern direkt die
nächste Occurrence (Todo offen + wiederkehrend → `nextOccurrence` →
`scheduleAlarm`). Request-Code umgestellt auf `todoId.hashCode()`
(vorher `dueAt/1000` — Re-Arm verschiebt das Datum → Cancel hätte den
neuen Alarm nicht gefunden → Geister-Alarme).
**Bekannte Lücken (bewusst offen):** Reboot/Force-Stop löscht Alarme
(bräuchte BOOT_COMPLETED-Receiver); auf Web erstellte Todos bekommen
keine Android-Alarme (Sync schreibt nur DB).

### 3+4. Editor: Pfeiltasten/Markierung + Checkbox-Listener (Fix `07afc05`)
**Bug 3:** Per-Zeilen-TextFields → Compose kann Cursor/Selektion
prinzipbedingt nicht über Feldgrenzen. **Fix:** EIN mehrzeiliges
BasicTextField (natives Verhalten) + `NoteBodyVisualTransformation`
(Symbole nur Anzeige; später durch Lesemodus ersetzt, s.u.).
**Bug 4 (Datenverlust!):** `migrateFromBlocks` hielt jeden mit `[`
beginnenden Body für Block-JSON. Checkbox-Zeile 1 = `[ ] ...` → Decode
scheiterte still → leerer Body → Auto-Save verteilte Datenverlust.
**Fix:** `[ ] `/`[x] ` von JSON-Erkennung ausgenommen + Regressionstest.

### 5. Editor-Architektur: Lesemodus + Edit-Modus (Option B, `e40c5d0`/`2c5b373`)
Nutzer: „hätte es gerne wie es vorher war, einzelne Boxen mit schönen
UI-Elementen — aber Mehrzeilen-Kopieren muss gehen". Zwei Fehlversuche:
- InlineTextContent (Composables im Text): geht in Compose 1.8.2 NICHT —
  Platzhalter-\uFFFC wurde als rohe „OBJ"-Tofu-Box gerendert, nur SpanStyles
  (fett/kursiv) gingen. Basis `BasicTextField` unterstützt KEINE
  Inline-Composables.
- Final: **Lesemodus** = Row-Layout pro Zeile (echte Checkbox klickbar,
  Canvas-Bullet/Pfeil, Nummer, abgehakt gedimmt) via `NoteBodyReadOnly`.
  **Edit-Modus** = EIN BasicTextField (Pfeiltasten/Mehrzeilen-Markierung).
  Tap auf Text → Edit-Modus; „Fertig"-Häkchen/Back → Lesemodus.
  Beide teilen denselben Roh-Text — kein Format-Change, kein Server-Update.

### 6. Doppeltipp Checkbox-Button → direkt `[x]` (`3eac34e`, `7540224`)
Einfach-Tipp: keine Checkbox → `[ ]` einfügen; Checkbox da → entfernen.
Doppeltipp ohne Checkbox → direkt `[x]`. Sequenz-Falle gelöst: 1. Tipp
fügt `[ ]` ein, 2. Tipp (Doppeltipp <400ms) WANDELT das frische `[ ]`
zu `[x]` (statt zu entfernen). Cursor hinter Präfix (nicht davor).

### 7. ⋮-Menü aus Notizen entfernt (`80019eb`)
Redundant (Drag auf Ordner + Reorder per Long-Press). Ordner-Menü
bleibt (Umbenennen gibt's sonst nirgends).

### 8. Instant Swipe-Delete + Scroll-Reparatur (`8964d15`, `6e667ac`)
Horizontal wischen = INSTANT löschen (vorher Long-Press nötig).
Erster Versuch (EIN Erkenner für alles) brach das Scrollen. **Fix:
ZWEI getrennte pointerInput-Erkenner:** horizontal instant
(Swipe-Delete), vertikal am Handy NUR nach Long-Press (Reorder — sonst
könnte die Liste nicht scrollen), Maus sofort.

### 9. Titel-Tap-Markierung (`6e667ac`)
Synchron im onFocusChanged gesetzte Selection wurde von der Tap-Cursor-
Positionierung des TextField (gleicher Frame) überschrieben → nie
sichtbar. **Fix (Frame-Delay-Trick):** Selection EIN FRAME SPÄTER via
`withFrameNanos` nach `requestFocus` → gewinnt. Notiz-Editor (bei jedem
Fokus) + Chat (Tap auf Titel → Edit mit Select-All).

### 10. Tap im Lesemodus → Cursor an Tap-Position (`97947f7`)
`onTextLayout` jeder Zeile → `getOffsetForPosition(pos)` →
`visualToRawOffset` (Inline-Marker ausblenden) → `+ prefixLen` (Präfix
ist Widget, nicht Text) → Edit-Modus mit Cursor genau dort.
Off-Text-Taps → Cursor ans Textende (wie vorher).

### 11. Verlauf: Gelöschte Einträge kamen zurück + duplizierten sich (Fix `siehe Commits`)
**Symptome:** Im Verlauf-Tab gewischte Gewohnheits-Einträge kamen sofort
wieder (und duplizierten sich mit der Zeit).
**Ursache 1:** `habit_logs`/`habit_history` waren im Sync-Protokoll
**append-only** (kein Löschen im Protokoll) — Wisch-Löschen war lokal ein
hartes `DELETE`, der Full-Down-Sync lieferte die Zeilen vom Server zurück.
**Ursache 2:** Perioden-Einträge bekamen **Zufalls-IDs** — jedes Gerät
erkannte den Periodenwechsel separat → zwei Einträge (verschiedene IDs)
für dieselbe Periode → Duplikate für immer.
**Fix (M11):** beide Tabellen auf `updatedAt` (LWW) + `deletedAt`
(Soft-Delete/Tombstone) umgestellt — wie Todos/Notizen/Ordner. Löschen von
History-Einträgen, Undo (+1), Perioden-Reset, Count-Korrektur sind jetzt
Soft-Deletes, die mitgesynct werden (Anti-Resurrektion greift).
Perioden-Einträge bekommen **deterministische IDs**
(`hist-<habitId>-<periodStart>`) → Server dedupliziert Race-Duplikate.
Satisfaction-Rating-Änderungen behalten Zufalls-IDs (echte Einzel-Ereignisse).
**Kompatibilität:** alle Spalten additiv (SQLite `ALTER TABLE ADD COLUMN`,
Server idempotent beim Container-Start) — bestehende Daten unangetastet.
Alte App + neuer Server: neue Felder werden ignoriert (Defaults). Neue App +
alter Server: Felder fallen beim JSON-Parse weg → kein Crash, kein Verlust.
**Server-Tests:** Migrations-Spalten, LWW-Skip, Soft-Delete,
Anti-Resurrektion, Log-Soft-Delete — alle grün (In-Memory-DB-Skript).

### 12. Migrations-Feuerwehr: Room-Schema-Crash (Fix `c8a0f99`)
M11 (v12→13) legte Indizes mit `idx_`-Präfix an — Room validiert
aber `index_<table>_<column>`-Namen → App-Crash beim Start (Handy),
Web-App zeigte alles leer (DB nicht öffnenbar). Reparatur-Migration
v13→14 (DROP IF EXISTS + CREATE, Room-konform, no-op für intakte DBs).
Daten selbst nie berührt — nur Indizes. Vom Nutzer bestätigt.

### 13. Links klickbar (M12, `f88ca3b`)
Neue `Linkify`-Util (`ui/components/Linkify.kt`): erkennt http(s)-URLs,
`LinkAnnotation.Url` → nativ geöffnet (Android Intent / Desktop /
Web window.open via Default-UriHandler). Satzzeichen am URL-Ende
bleiben Text, unbalancierte Klammern auch. Angewandt auf: Todo-Titel
+ Notizen (Liste), Verlauf, Chat-Nachrichten, Notiz-Lesemodus (dort
mit eigenem Tap-Abgleich, weil der Cursor-Tap-Handler die Geste
konsumiert). 6 Linkify-Tests, 88 gesamt grün. Edit-Modus bewusst ohne.

### 14. Quick-Add-Bar (M13, `61b3605`…`de78364`)
Schwebende Box im Aufgaben-Tab über der BottomBar, ersetzt den
FAB (Notizen-FAB bleibt). EIN Textfeld + Häkchen-Button DARIN
(trailingIcon), darunter runde Options-Knöpfe (nur Icons): Datum
(DatePicker→TimePicker, nochmal tippen=entfernen, zeigt gewähltes
Datum), Wiederholung (BottomSheet mit RecurrenceEditor),
Benachrichtigungs-Stil (Dropdown), Verlauf-Toggle, Notizen-Toggle.
Nach Erstellen: Titel/Notzen leer, Optionen bleiben.
**IME-Kollisionen (3 Runden):** (a) NavBar war ime-gepaddet → fuhr
mit hoch; jetzt nur die Box (`9b497d0`). (b) Box startete über der
NavBar + imePadding → doppelter Abstand zur Tastatur; Content-Padding
fällt bei offener Tastatur weg (`4eb323c`). (c) binäre Umschaltung
sprang hinter die animierende Tastatur; jetzt kontinuierliches
`bottom = max(ime, NavBar-Höhe)` → gleicht weich mit (`de78364`).

### 15. UI-Rework: eigenes Farbsystem (M14, `2d9729a`)
`Theme.kt` komplett neu — EIGENES Farbsystem statt Material-Defaults,
Dynamic Color DEAKTIVIERT (es würde unser System mit Wallpaper-
Farben überschreiben):
- Dark: BG `#1c1d21`, Fläche/Nav `#2a2c32`, Eingabe `#34363d`,
  Text `#e8e9ec`, gedämpft `#8b8e97`, Akzent `#8ab4ff`,
  Akzent-BG `#2c3d63`, Löschen `#a8322b`, Linie `#44464e`
- Light: `#f6f6f8`/`#ffffff`/`#ececf0`/`#1c1d21`/`#6c6f78`,
  Akzent `#2f5fc4`/`#dbe5fb`, Löschen `#B3261E`
- Ein blauer Akzent, sonst Grautöne; Rot nur zum Löschen
  (Swipe-Delete-BG via error/errorContainer = `#B3261E`)
Visuell: Quick-Add-Bar als Karte in Nav-Farbe (Radius 24) mit
Eingabe-Feld (Fläche 2, Radius 16) + transparenten Options-Knüpfen
drauf, 12dp Abstand zur BottomBar (`de78364`). Checkbox-Kreis
kleiner (22dp) + gedämpft statt fett/rot. Abschnitts-Header: kleine
GROSSBUCHSTABEN mit Anzahl (`KEIN DATUM · 2`). Neue
`ScreenTitle`-Komponente: große Titelzeile „Aufgaben"/
„Gewohnheiten"/"Verlauf" oben links (Notizen bewusst ohne — dort
ist der Ordner-Breadcrumb der Anker). Avatar mehr Abstand zur
Statusleiste. TodoRow auf Hintergrund statt Kartenfläche.
**Lucide-Icons verworfen:** KMP-Bindings ohne Wasm-Support würden
die Web-App brechen — Material Icons `Outlined` hat denselben dünnen
Outline-Stil.

### 16. Deploy-Performance (M13+M14, `4eb323c`, `3ff6c24`)
Gemessen statt geraten — die 60s pro Deploy verteilten sich:
34s Binaryen-Optimize (Release-Veredelung des Wasm: 50MB→14MB,
single-threaded, echte CPU-Arbeit) + 18s Wasm-Kompilieren + ~8s
ksp/webpack. Früher KAM DAZU jedes Mal komplettes yarn install
(`rm -rf build/wasm` löschte node_modules mit).
- Fix 1 (`4eb323c`): `--no-daemon`/`--rerun-tasks` raus (Altlasten)
- Fix 2 (`3ff6c24`): nur noch sqlite-worker-Kopie per rsync frisch
  halten (Deckt den alten „veralteter Worker"-Bug weiter ab),
  yarn-Cache bleibt intakt — kein yarn-Reinstall mehr
- Heap 2GB→4GB getestet, bringt nichts → zurück auf 2GB
- Ehrliche Rest-Wahrheit: ~35-60s pro Änderung bleiben (Binaryen),
  unveränderte Deploys <1s (Build-Cache)

### 17. Bug-Session 2 (2026-11-23)

**Bug 12 — OLED brannte die Nacht (`39d8ec6`):**
`FLAG_KEEP_SCREEN_ON` in der Vollbild-Alarm-Activity hielt das
Display an, solange das Fenster sichtbar (über Sperrbildschirm
→ die GANZE Nacht, OLED-Burn-in + Akku). Fix: Flag entfernt —
`setTurnScreenOn` weckt beim Alarm, dann greift der normale
Display-Timeout; Power-Taste zeigt die Meldung weiter überm
Sperrbildschirm (`setShowWhenLocked` bleibt).

**Bug 13 — Notification-Style ging bei Wiederholung verloren
(`e901df7`):** `completeTodo()` legt die nächste Occurrence per
`createTodo()` an — ohne `notificationStyle` → jede folgende
Wiederholung fiel auf Default (Vollbild) zurück, obwohl der
Re-Arm selbst den Style korrekt frisch aus der DB liest. Fix:
`todo.notificationStyle` wird beim Anlegen der nächsten
Occurrence durchgereicht. Alle anderen Pfade (Re-Arm,
updateTodoFromForm, Sync-Down, ViewModel) geprüft: ok.

**Bug 14/14b — `[]` löschte ganze Notiz (`585b6b0`, `6475cc0`):**
migrateFromBlocks hielt `[]` für leeres Block-JSON (decode
liefert stille einen LEEREN Body) → Migration → "" → Auto-Save
schrieb den Datenverlust fest. Fix 14: nur migrieren, wenn der
Decode BLÖCKE liefert. Fix 14b (Lücke im ersten Fix): `[]` ist
doppelt belegt — echter Text UND Legacy-Leer-Marker (Default
`bodyJson="[]"` in NoteRepository/SyncDTOs). Jetzt getrennt:
exakt `[]`/`{}` = Leer-Marker → ""; JSON mit decodbaren
Blöcken → migrieren; sonst (`[`, `[]\nText`, `[ ]\nText`)
Plaintext, bleibt unangetastet.

**Revert M14c (`f37e7e7`):** Flächen 1/2-Tausch (hellere Karten)
nach Nutzer-Feedback zurück — altes Farbschema gefiel besser.

### 18. M14d — Notiz-Titel in die TopBar (`1be6016`)

Nutzer-Wunsch: Der Titel einer Notiz soll oben in der TopBar
angezeigt werden wie bei Chat-Notizen — Konsistenz.
Vorher war er ein TextField über dem Body im Content.
Jetzt exakt das Chat-Muster: Lesemodus = halbfetter Text in der
TopBar, Tap → Inline-Edit mit Select-All (Frame-Delay-Mechanik),
Enter (ohne Shift) oder Fokus-Verlust → speichern. Neue
Notiz startet direkt im Titel-Edit. Body/Checkboxen/Format-Bar
unverändert (Lesemodus/Edit-Modus-Toggle bleibt). Aufgeräumt:
transparentTextFieldColors (toter Code) + alte Titel-Select-All-
LaunchedEffects entfernt.

### 19. M14e — Titel-/Label-Konsistenz über alle Tabs (`ccb57b9` → `e5faa41`)

Nutzer-Wunsch: Die blauen CAPS-Labels saßen in Aufgaben/
Notizen/Verlauf an verschiedenen Orten und in verschiedenen
Größen; „HOME“ überlappte die erste Notiz.

- **Notizen:** ScreenTitle(„Notizen“) wie Aufgaben/Verlauf;
  darunter der Pfad als erste blaue CAPS-Zeile. Root heißt
  jetzt „Home“ → HOME klickbar (Wurzel), Vorgänger-Ordner
  klickbar, aktueller Ordner nur Anzeige.
- **Fix 1:** Breadcrumb war eine Overlay-Box mit festem
  Offset → Überlappung. Jetzt ERSTES Listenelement
  (96dp, gleiche Paddings/Typo wie der SectionHeader der
  Aufgaben).
- **Fix 2:** Klickbare Crumbs hatten noch vertical-6dp-
  Padding → HOME saß 6dp tiefer als HEUTE/GEWOHNHEITEN.
  Padding raus — überall bündig.
- **Verlauf:** Sektions-Header labelLarge → labelMedium +
  gleiche Paddings wie Aufgaben.

### 20. M14f — Eckenradius-System (`5d75d56` → `c399e2f`)

Nutzer-Wunsch: Karten einheitlich rund (vorher 12dp/
ListItem+Divider gemischt mit 24dp-Eingaben).

- **System: 16dp Karten, 28dp Dialoge/Sheets (M3-Default,
  unverändert), 8dp Abstand zwischen Karten.**
- **Verlauf:** ListItems jetzt echte Karten (HistoryCard,
  16dp, surface) mit 8dp-Abstand statt Divider.
- **Fix 1:** SwipeOrReorderRow/SwipeToDeleteRow malen einen
  background HINTER die Karte — rechteckig, vollflächig,
  gleiche Farbe wie die Card → füllte die runden Ecken
  auf („90-Grad-Kasten“). Wrapper-Hintergrund jetzt mit
  RoundedCornerShape(16.dp).
- **Fix 2:** Rote Lösch-Box (errorContainer) ebenso.
- **Fix 3:** TodoRow malte sich mit der Hintergrund-Farbe
  → Aufgaben-„Karten“ unsichtbar. Eigene background raus —
  der SwipeToDeleteRow-Wrapper liefert surface + 16dp.

### 21. M14g — Gefüllte Felder, runde Checkboxen, Dialog-Farben (`7555ca6`)

Nutzer-Report: Outline-Felder in Dialogen/Einstellungen/Chat,
Dialog-Farben „die sonst nirgends vorkommen“, eckige Checkboxen
obwohl die Aufgaben runde haben.

- **filledTextFieldColors()** (Theme.kt) = QuickAddBar-Kanon:
  surfaceContainerHigh-Füllung, kein Indikator-Strich. Alle 21
  OutlinedTextField → TextField + 16dp-Shape (Dialoge,
  Einstellungen, Chat, Notiz-Ordner, Tracker, Auth).
- **RoundCheckbox** (neue Komponente) = Aufgaben-Listen-Kanon
  (Icons.Outlined.Circle / Filled.CheckCircle, 22dp): ersetzt die
  eckige M3-Checkbox in TodoEditDialog, HabitEditDialog,
  NoteBodyReadOnly (Notiz-Checklisten).
- AlertDialog + ModalBottomSheet überall containerColor =
  surface (vorher M3-Default surfaceContainerHigh = das
  Grau, das sonst nie als Fläche vorkam).

### 22. M14h/i — Editor-Bar, DatePicker, Recurrence-Editor (`b8fd100` → `ffc0d0f`)

- **Editor-BottomBar**: surfaceContainer → surface wie TopBar.
- **DatePicker-Dialoge** (Todo/QuickAdd/Habit): colors mit
  containerColor = surface — WICHTIG: der DatePicker-
  Composable (Kalender-Grid) holt seine Fläche aus seinen
  EIGENEN colors, nicht aus dem Dialog! Nur der Dialog-Rahmen
  (Abbrechen/Weiter) färbte sich über die Dialog-colors.
- **RecurrenceEditor-Overhaul**: Radio-Zeilen mit springenden
  InlineIntField-Pillen („Jeden [1] Tag“) → **Stepper (− n +)
  fürs Intervall + FilterChip-Segment-Buttons** (Nie/Min./Std./
  Tag/Woche/Monat/Jahr), suffixPlural im RecurFreq-Enum.
  Gleicher Stepper für Laufzeit-Count und Am-n.-des-Monats
  (DayStepper 1..31). Radio-Zeilen 48dp.
- **M14i**: Chips in FlowRow mit natürlicher Breite (Umbruch
  statt weight(1f)-Quetscherei); „Fertig“/„Keine Wiederholung“
  raus aus dem Sheet („Nie“-Chip + Sofort-Wirkung);
  Wochentags-/Im-Monat-/Tage-UI ohne start-Indent;
  Wochentags-Chips AssistChip → FilterChip mit zentriertem
  Label (M D M D F S S).

### 23. M14j — Todo-Abstand + HabitCadencePicker auf Chip-System (`d29d5e3` → `b4e2bf4`)

Zwei Bugs aus dem UI-Review:

- **Bug 1:** Abstand zwischen Aufgaben war 4dp, alle anderen
  Listen (Gewohnheiten/Notizen/Verlauf) 8dp → Todos auf 8dp
  angeglichen.
- **Bug 2:** HabitCadencePicker (Gewohnheit erstellen/bearbeiten)
  hatte noch die Radio-Zeilen mit springenden Inline-Zahlen-
  Pillen. Jetzt dasselbe System wie der Recurrence-Editor:
  Segment-Chips (Tag/Woche/Monat/Jahr/Tage) + IntervalStepper
  (− n +) nur bei „Alle n Tage“ (nur dort gibt es ein
  Intervall — sonst steckt das Ziel im eigenen Feld).
  IntervalStepper im RecurrenceEditor dafür internal gemacht.
  Keine Wochentags-/Monats-Extras hier — Reset-Tag ergibt
  sich aus dem Startdatum.
- **M14j-Fix:** Chip-Reihe als weight(1f)-Row gepackt →
  gequetscht statt umbrechend (SELBER Fehler wie M14i —
  gelernt: Chips IMMER in FlowRow mit natürlicher Breite,
  nie weight(1f)).

### 24. M14k–p — Feinschliff-Serie (`fa211e4` → `27b8fd6`)

Sechs Runden Nutzer-Review-Feedback, jeweils kompiliert + getestet
+ deployed + gepusht:

- **k:** Progress-Stop-Dot weg (`drawStopIndicator = {}` — Compose
  1.8.2 heißt den Parameter so, AndroidX 1.7 `stopIndicator`!);
  Chat-Link #B7D3FF + Unterstreichung (`linkified(underline=)`);
  Switch-Aus-Zustand outline/onSurface; Avatar-Bitmap auf Kreis
  geclippt.
- **l:** Gefahrenzone-Buttons weiß gefüllt + rote Schrift (Karte ist
  errorContainer-rot — Outline-Rot-auf-Rot war unsichtbar).
- **m:** TrackerDetail Zeiträume-Karte + navigationBars-Insets;
  TopBar-Abstand; Datum „Di, 24. Nov · 00:00“ (formatDueShort) +
  „Mo, 23. Nov 2026“ (formatStartLabel) statt ISO; Settings-Felder
  inputFillColor() (ein Ton kräftiger als Karten — identisch war
  unsichtbar); „Neues Passwort“ + „optional“ als supportingText.
- **n:** Habit-Stepper-Zeile; Chat-Icons pur (Check-Button, Trash-Icon,
  Text-Abbrechen); Admin-Liste lädt Profilbilder
  (fetchUserAvatar, LaunchedEffect pro UserRow).
- **o:** „Ziel (− n +)“ + „über (− m +) Tag(e/Woche/…)“ in ZWEI
  Zeilen (eine Row war zu knapp, Text brach mehrzeilig um) —
  „über“ bei ALLEN Einheiten, Einheit folgt dem Chip.
- **p:** Cadence-Labels zeigen Intervall: „2x pro 2 Wochen“ /
  „3x alle 2 Tage“ (vorher log — Anzeige ohne Intervall). Neue
  Engine-Tests verifizieren WEEK interval=2 (Periode wirklich 14
  Tage, Reset bleibt Montag) + MONTH interval=3 — Nutzer-Sorge
  „akzeptiert er 2 Wochen wirklich?“ beantwortet: ja. 94 Tests.

### 25. Bug-Hunting-Runde 3 + neues App-Icon (`726c42f` → `49cc189`)

Die „Produktiv-Liste" — alles, was im Alltag aufgefallen war, in
einer Session weg. **Damit ist die Feature-Liste für den ernsthaften
Alltags-Einsatz komplett abgehakt** (Nutzer: „fühlt sich wie eine
professionelle App an, nicht mehr wie eine Notlösung"):

- **Bug 1 (2 Versuche):** Checkbox-/Format-Button riss den Cursor
  ans Präfix — jetzt Cursor RELATIV um Präfix-Differenz verschieben
  (hinter dem „d" von „hund" bleibt man hinter dem „d"). Zusätzlich:
  Position beim „Fertig"-Check merken + beim nächsten Edit-Wechsel
  wiederherstellen.
- **Bug 2:** Tippen UNTER den geschriebenen Text → jetzt in den
  Edit-Modus (Tap-Fläche = ganze Editor-Column statt nur Text-Block).
- **Bug 3:** Kein Ripple/Hover-Layer mehr auf dem Lese-Block (war am
  PC die eckige Markierung).
- **Bug 5 (3 Versuche!):** Cursor-Sichtbarkeit beim Tippen. Erst
  imePadding (→ Mini-Viewport, scrollte irre weit runter), dann
  raus — Kompose-bringIntoView klemmte im Scaffold-Setup komplett.
  Finale Lösung: SELBST messen (onTextLayout + getCursorRect +
  Feld-Offset via positionInParent + Column-Höhe) und die Column per
  LaunchedEffect animiert scrollen bis die Cursor-Zeile auf ~35 %
  Viewport-Höhe sitzt (Luft für Format-Leiste/Tastatur).
- **Bug 6:** Große Titel („Aufgaben" etc.) scrollen MIT der Liste
  hoch (erstes Listenelement, alle 4 Tabs), nur der Avatar bleibt
  fixiert — Nutzer bevorzugte das paradox „unprofessionellere"
  scrollende Verhalten über TopBar-Masking.
- **Bug 7:** „DIESE WOCHE" zählte ab Montag 7 volle Tage (bis
  übernächster Montag!) — jetzt bis Sonntag 23:59
  (`daysToSunday = 6 - ordinal`, vorher `7 - ordinal`). Neue
  TodoGroupingTest (Berlin-Zeiten gelernt: UTC-23:59 landet in
  Berlin am Folgetag — Tests in System-TZ rechnen!).
- **Bug 8:** Alle Ripple/Hover-Flächen auf/in Karten auf ihre
  Rundung geclippt (SwipeToDelete/OrReorderRow Wrapper + Delete-
  Buttons, TodoRow, Breadcrumbs, Sheet-Menüeinträge, „3×"-Zähler).

**Neues App-Icon** (artwork/app-icon-v2.svg, Nutzer genehmigt):
Checkliste auf dunkler Karte im App-Theme (#1C1D21/#2A2C32/
#8AB4FF-Haken). Als favicon.svg (Website) + Android Adaptive-Icon
(ic_launcher[_round].xml + drawable ic_launcher_foreground/
background). Android-Geometrie nach 2 Fehlversuchen exakt aus der
SVG, komplett um 2 nach OBEN verschoben (sonst hellgrauer Schlitz
am oberen Rand — Samsung maskiert asymmetrisch) + offener Kreis
cx=39 (fluchtet mit der Haken-Visuell-Mitte, sonst linksbündig).

**Arbeits-Lektionen (Nutzer-Feedback):** (1) Aufgaben SEQUENZIELL
abarbeiten, nicht alle gleichzeitig im Kopf planen. (2) deploy-web
nur wenn Web-relevante Änderungen (Android-only = kein Deploy).
(3) Build-Checkpoints alle paar Aufgaben, nicht nach jeder.

97 Tests grün (Stand `49cc189`).

### 26. Alarm-Screen M14-Stil + M16 „Teilen mit der App" (`9923c12` → `a9c5259`)

**Alarm-Screen im M14-Stil:** FullScreenAlarmActivity umgestellt —
Gradient-Overlay raus, Inhalt (Icon-Badge, Titel, Body, Buttons)
zentriert in einer surfaceContainer-KARTE mit 16dp-Rundung wie alle
Karten der App. „Erledigt" = gefüllter Primär-Button mit Haken,
„Schließen" = TextButton statt Outline. Für den adb-Test gab es ein
Debug-Manifest-Overlay (exported=true via tools:replace), nach dem
Test wieder entfernt — Release blieb immer dicht.

**M16 „Teilen mit der App"** (Nutzer-Feature-Wunsch, nach der als
abgeschlossen erklärten Alltags-Liste doch noch entdeckt):
- `ACTION_SEND`-Intent-Filter (text/* — auch text/html etc., Text
  via getCharSequenceExtra). MainActivity empfängt in onCreate +
  onNewIntent (ShareFlowController, handleOnce gegen Doppel-Feuer).
- Geteilter Text landet als **Prefill im Quick-Add** (Aufgaben-Tab).
  Zwei Wege: Haken → Aufgabe direkt erstellt; **Notebook-Knopf**
  (MenuBook, nur Icon) in den Quick-Optionen → **Pick-Modus** im
  Notizen-Tab (blaues Banner „Geteilten Text anhängen" mit Vorschau
  + Abbrechen; Ordner-Klicks navigieren normal).
- Notiz/Chat antippen → Text ans Ende **ohne die Notiz zu öffnen**;
  Chat → neue Nachricht.
- **Endbedingung** (Erstellen/Anhängen/Back): finaler Sync (läuft im
  appScope) + `closeShareActivity()` = Activity.**finish()** — NICHT
  moveTaskToBack (das ließ die Activity am Leben → mit jedem Share
  eine weitere Recents-Instanz + Session überlebte, Nutzer-Report).
  Man landet automatisch wieder in der absendenden App (Share läuft
  in deren Task).
- **State-Trennung gelernt:** shareText (Session, lebt bis End-
  bedingung) vs. sharePrefill (One-Shot, beim Landen im Feld
  konsumiert). 1. Versuch: Konsument nullte den SESSION-Text →
  Pick-Modus leer, Notiz-Klick öffnete statt anzuhängen.

**Datenverlust-Bug beim Share-Append (gefixt):** 1. Versuch decodete
den Notiz-Body als Block-JSON — er liegt aber als PLAIN TEXT in der
DB (Präfixe sind echter Text, Block-JSON ist Legacy!). Decode
scheiterte still → EMPTY_BODY → **ganze Notiz beim Anhängen
gelöscht** (Nutzer-Report; Inhalt ließ sich über die Web-App
retten). Jetzt: `migrateFromBlocks` (Legacy-Fallback) +
`appendSharedTextToText` direkt auf Textebene (detectListType/
stripPrefix): leeres Item `[ ] ` → Text hinter Präfix (Haken-Status
bleibt, auch `[x] `), Item mit Inhalt → neue Zeile mit gleichem
Präfix (Nummerierung zählt hoch), normale Zeile → neue Zeile ohne
Präfix, leere Zeile → füllen. 12 Tests inkl. Regression gegen den
Verlust.

**Share-Session-Feinschliff (Nutzer-Feedback):** „Notiz"-Label unterm
Notebook-Knopf weg; **BottomBar während der Session versteckt** (kein
versehentliches Tab-Springen); QuickAdd-Abstand
max(ime, navigationBars, bottomBar) — bei versteckter BottomBar
rutschte die Box sonst unter die Systemknöpfe; System-Back in der
Session = Abbrechen + finish().

109 Tests grün (Stand `a9c5259`).

## Offene Punkte (Stand nach Bug-Fix-Session)

Funktional:
- [ ] **M10 – Desktop-Feinschliff** (Fenstergröße/-titel, Scroll,
      Speichern beim Schließen) + jpackage bundled JRE mit
      `-XX:UseAVX=2` patchen
- [ ] **F7 – Bilder in Notizen** (Picker, downsamplen, PNG lokal, `ImageBlock`)
- [ ] **F8 – Stift/Zeichnen** (Drawing-Layer, `DrawingBlock`, Palm-Rejection)
- [ ] **F9 – Bild-Sync** (Endpoint + Nachladen fehlender Bilder, nach F7/F8)
- [ ] **Reboot-Re-Arm:** BOOT_COMPLETED-Receiver, der offene
      wiederkehrende Todos neu plant (Lücke aus Bug 2)
- [ ] **Web→Android-Alarme:** nach Sync neu planen, falls Todos mit
      Fälligkeit neu reinkommen (Lücke aus Bug 2)

UI-Rework läuft (M14 erledigt, `2d9729a`+`de78364`) — Rest fein-
schleifen bei Bedarf:
- [ ] Lucide-Icons manuell einbetten (nur die ~15 genutzten, als
      ImageVector) — falls Material Outlined nicht reicht
- [ ] FAB-Polish Notizen, weitere Detail-Politur
- [ ] **M15 – UI-Rework Querkant/Desktop (Web am PC):** Hochkant
      (M14d–p) ist fertig, nur noch Gelegenheits-Tweaks. Am Desktop
      fehlt das ganze responsive Layout: breite Fenster → mehrere
      Spalten/große Flächen statt gestreckter Handy-Screens,
      Hover-Zustände, ggf. Navigation ohne BottomBar (Seitenleiste).
      Erst nach Bug-Hunting-Priorität, Einzellücken sammeln und
      dann als eigene M15-Serie.
- [ ] **Kindle-Client (F) — nur wenn der Kindle da ist:** Compose/Wasm
      wird der Paperwhite-Browser nicht handeln (SSE, Lazy-Columns,
      Animations-Chips, SQLite-Worker/OPFS). Nötig wäre eine komplett
      eigene, stark vereinfachte Web-Oberfläche OHNE Compose — statisch,
      ohne Animationen, eventuell reduzierte Features (nur Listen +
      Checkboxen + Notizen lesen/schreiben?). Server-Sync-Endpunkte sind
      vollständig wiederverwendbar. Erster Schritt: aktuelle Web-App im
      Kindle-Browser testen → dann entscheiden, ob sich der Aufwand
      lohnt. Lokale Theme-Präferenz (dunkel/hell/schwarz-weiß) unabhängig
      davon sinnvoll (nicht pro Account — nur Client-seitig).

## Erledigt (Kurzfassung)

Vollständige Historie in `MIGRATION-CMP.md` (M1–M9) und Git-Historie.

- Block A–H der Original-App — ursprünglich native Android, im
  CMP-Projekt neu aufgebaut
- CMP-Migration M1–M9 (Backend-Auth, KMP, Room KMP, Ktor, RRULE-Web,
  komplette UI in commonMain, Wasm + OPFS)
- M8 Alarme + Benachrichtigungs-Stile, Android-Back-Handler
- Echtzeit-Sync: Auto-Sync + SSE, Ping-Pong-Vermeidung, LWW + Clock-Skew,
  Sync-Perf (~50–80ms)
- Produktions-Deploy: Docker, Domain + HTTPS via NPM, Web-Dist im Repo
  (`deploy-web.sh`), COOP/COEP-Header
- Sync-A+ Notiz-Sync bombenfest (reaktiver Editor, safe flush)
- Sync-FIX Full Up/Down-Sync (s.o.) — vom Nutzer bestätigt
- Bug-Fix-Session: Bugs 1–11 (s.o.) — vom Nutzer bestätigt
- Bug-Session 2: Bug 12 (OLED-Nacht), Bug 13 (Style bei
  Wiederholung), Bug 14/14b ([]-Datenverlust) — bestätigt
- Revert M14c Flächen-Tausch (altes Farbschema gefiel besser)
- M14d: Notiz-Titel in die TopBar (wie Chat) — Inline-Edit
  mit Select-All/Enter/Fokus-Verlust-Speichern
- M14e: Titel-/Label-Konsistenz (ScreenTitle überall, HOME-
  Breadcrumb in der Liste, Verlauf-Header wie Aufgaben)
- M14f: Eckenradius-System (16dp Karten / 28dp Dialoge /
  8dp Abstand) + Verlauf-Karten statt ListItem+Divider
- M14g: gefüllte Eingabefelder (filledTextFieldColors),
  runde Checkboxen (RoundCheckbox), Dialog/Sheet = surface
- M14h/i: Editor-BottomBar surface, DatePicker-Farben,
  Recurrence-Editor: Stepper + Segment-Chips (FlowRow),
  Sheet ohne Fertig/Keine-Wiederholung
- M14j: Todo-Abstand 8dp, HabitCadencePicker =
  RecurrenceEditor-Chip-System (Stepper nur bei NDAYS)
- M14k–p: Feinschliff-Serie — 6 Review-Runden bis `27b8fd6`
  (Progress-Dot, Chat-Link-Kontrast, Switch/Avatar, Gefahrenzone,
  Tracker-Zeiträume-Karte, Datum-Anzeige deutsch, inputFillColor,
  „Ziel n über m Tage“, Intervall-Anzeige, Engine-Tests)
- Bug-Hunting 3 + App-Icon v2 (`49cc189`): Cursor-Merken/relativ-
  Verschieben, Hitbox ganze Column, kein Hover-Layer, Cursor-Sicht-
  barkeit selbst gescrollt, „Diese Woche“ bis Sonntag 23:59 +
  Tests, Ripple auf Rundungen geclippt, Titel scrollen mit,
  Android-Icon nach 3 Iterationen (Samsung-Masking-Tuning)
- M11-Verlaufsfix + Migrations-Feuerwehr (v13→14) — bestätigt
- M12 Links klickbar (Todo/Chat/Verlauf/Lesemodus)
- M13 Quick-Add-Bar (FAB ersetzt, IME 3 Runden fixiert)
- M14 UI-Rework: eigenes Farbsystem, Dynamic Color aus, Titel-
  Anker, Header mit Anzahl, Quick-Add-Karte
- Deploy-Perf: Daemon-Fix + kein yarn-Reinstall mehr
      (Binaryen-Optimize bleibt ehrlich ~35-60s/Änderung)

### Konstraints (Refresh)
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` für Gradle
- `./deploy-web.sh` nach UI-Änderungen (committet `backend/web/`)
- `rm -rf build/wasm` + `--rerun-tasks` bei Worker-Änderungen
- `PaddingValues(start=, top=, end=, bottom=)` nicht `horizontal=`/`vertical=`
- `Icons.Filled.FormatBold` (nicht `AutoMirrored.Filled.*`)
- `windowSoftInputMode="adjustNothing"`, `contentWindowInsets =
  WindowInsets(0,0,0,0)`, `windowInsetsPadding(ime.union(navigationBars))`
  nur auf bottomBar
- German quotes: `„..."` (U+201C zum Schließen), nicht ASCII `"`
- `catch (e: Throwable)` auf Wasm (kotlin.Error wird nicht von
  catch(Exception) gefangen)
- Wasm: `@Insert(REPLACE)` = DELETE+INSERT → Invalidation-Sturm.
  `INSERT(IGNORE)` + `UPDATE` ist besser.
- `@Upsert` auf Wasm = EntityUpsertAdapter → INSERT-then-UPDATE bei
  Konflikt → Transaktionsleck. Nicht verwenden.
- Sync-Server: `SYNC_TOKEN=T14xI6zQmBv7JONHGwiqPCkIOgv1Cwmig6MrydqhMdQ`
- Produktion: `https://todo.christopherh.de` via NPM (HTTPS, HSTS, HTTP/2)
- Lokales Dev-Backend: Port 8001
- Lokale Webapp: `http://localhost:8090` (`python3 /tmp/opfs_server.py`)
- Commit + pushen, Nutzer macht `git pull && docker compose up -d --build`
