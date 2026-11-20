# TodoNotes – Progress

**Stand: App ist LIVE und vollständig synchron** — Android-App + Web-App über
`https://todo.christopherh.de`, Echtzeit-Sync in ~50–100ms.

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
      fälligkeit neu reinkommen (Lücke aus Bug 2)

Später (visuell — Nutzer: „UI-Rework kommt, aktuelles Design ist
funktional aber hässlich"; ALLES Optische wird dort gesammelt):
- [ ] **UI-Rework**: Design-Politur, Notiz-Listen-Look, FAB-Polish,
      responsive Layouts

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
- Bug-Fix-Session: 10 Bugs/Features (s.o.) — vom Nutzer bestätigt

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
