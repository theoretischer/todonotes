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

## Offene Punkte

Funktional:
- [ ] **M10 – Desktop-Feinschliff**
- [ ] **M10 – Desktop-Feinschliff** (rein funktional: Fenstergröße/-titel,
      Scroll-Verhalten, Speichern beim Schließen — KEIN visuelles Styling)
- [ ] **F7 – Bilder in Notizen** (Picker, downsamplen, PNG lokal, `ImageBlock`)
- [ ] **F8 – Stift/Zeichnen** (Drawing-Layer, `DrawingBlock`, Palm-Rejection)
- [ ] **F9 – Bild-Sync** (Endpoint + Nachladen fehlender Bilder, nach F7/F8)

Später (visuell — Nutzer: „UI-Rework kommt, aktuelles Design ist funktional
aber hässlich"; ALLES Optische wird dort gesammelt):
- [ ] **UI-Rework**: Design-Politur (altes Block G), Notiz-Listen-Look (F11),
      FAB-Polish (F12), responsive Layouts

## Erledigt (Kurzfassung)

Vollständige Historie in `MIGRATION-CMP.md` (M1–M9) und Git-Historie.

- Block A–H der Original-App (Todos, Recurrence, Habits, Notizen, Chat,
  Reorder) — ursprünglich native Android, im CMP-Projekt neu aufgebaut
- CMP-Migration M1–M9 (Backend-Auth, KMP, Room KMP, Ktor, RRULE-Web,
  komplette UI in commonMain, Wasm + OPFS)
- M8 Alarme + Benachrichtigungs-Stile, Android-Back-Handler (Ordner/Settings)
- Echtzeit-Sync: Auto-Sync + SSE, Ping-Pong-Vermeidung, LWW + Clock-Skew,
  CASCADE-Fix (`@Upsert`), Sync-Perf (~50–80ms gemessen)
- Produktions-Deploy: Docker, Domain + HTTPS via NPM, Web-Dist im Repo
  (`deploy-web.sh`), COOP/COEP-Header, Self-Healing bei falscher Server-URL

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
Anti-Resurrektion bleibt (einmal gelöscht = gelöscht). (Commit `5d09c70`)

## SYNC-FIX – Todos/Habits/Folders/Chat syncen nicht zuverlässig (In Progress)

**Status: AKTUELLER BLOCKER.** Notizen syncen 1a (reaktiver Editor), aber
alle anderen Entitäten (Todos, Habits, Folders, Chat-Messages) haben
schwere Sync-Probleme auf Web (Wasm).

### Symptome (Nutzer-Report 2025-08-25)
1. Todo auf PC erstellt → erscheint auf Handy ✓, aber nach Web-Reload
   **weg**. Mehrmaliges Reload → mal da, mal weg, scheinbar random.
2. Nach Minuten (wenn Sync endlich läuft) tauchen sie auf.
3. **Alte gelöschte Daten von vor Wipe tauchen wieder auf** — Resurrektion.
4. Nach Neuladen: mal nix, mal die 3 neuen, mal die alten vom Wipe, mal
   gemischt.

### Verdächtige Ursachen (noch zu analysieren)

#### A. `getSince(lastSyncedAt)` Off-by-One / Timing-Bug
- `collectLocalChanges` nutzt `updatedAt > lastSyncedAt` (neu eingeführt
  in Sync-A+). Wenn ein Todo erstellt wird, ist `updatedAt = nowMs()`.
  Beim Push setzt der Server `updatedAt = server_now` und liefert
  `newSyncedAt = server_now + 1`. Client speichert `lastSyncedAt =
  server_now + 1`. Beim nächsten Push: `updatedAt = server_now` (die
  Row vom letzten Sync) → `server_now > server_now + 1`? **NEIN** → wird
  NICHT gepusht. Das ist korrekt (kein Re-Push).
- ABER: bei einem **neuen** Todo, das LOKAL erstellt wurde (`updatedAt =
  nowMs() ≈ server_now`), und dann der Sync läuft: Server setzt es auf
  `server_now_s1`, Client bekommt `newSyncedAt = server_now_s1 + 1`. Wenn
  der Client DANN nochmal reloadet und `getSince(lastSyncedAt =
  server_now_s1 + 1)` macht → das neue Todo hat `updatedAt =
  server_now_s1` → `server_now_s1 > server_now_s1 + 1`? **NEIN** → wird
  NICHT gepusht. Korrekt.
- **Verdacht:** Das Problem ist nicht `getSince`, sondern die **DB-Flow
  feuert nicht / feuert stale Daten** auf Wasm nach `INSERT OR REPLACE`.

#### B. DB-Flow-Reaktivität auf Wasm (HAUPTVERDACHT)
- `TodoViewModel.openTodos` = `repo.observeOpenTodos().stateIn(Eagerly)`.
- `observeOpenTodos` = `dao.observeOpenTodos(): Flow<List<Todo>>` mit
  `@Query("SELECT * FROM todos WHERE completedAt IS NULL AND deletedAt
  IS NULL ORDER BY ...")`.
- Nach `applyServerChanges` (`upsertAll` = `INSERT OR REPLACE`) sollte
  die Flow neu feuern. Auf Wasm (Room3 + sqlite-web-worker) ist die
  Invalidation-Tracker aber evtl. **nicht zuverlässig** — besonders wenn
  `INSERT OR REPLACE` (DELETE+INSERT) die Tabelle invalidated.
- **Mögliche Folge:** Nach Reload zeigt die Flow stale Daten oder nichts,
  bis ein manuelles Refresh / SSE-Update kommt.
- **Lösung Verdacht B:** `INSERT OR IGNORE` + `UPDATE` statt `INSERT OR
  REPLACE` für alle Tabellen (wie schon für habits gemacht), weil REPLACE
  = DELETE+INSERT die Invalidation-Tracker verwirrt.

#### C. `INSERT OR REPLACE` = DELETE+INSERT → Invalidation-Tracker-Sturm
- `INSERT OR REPLACE` macht bei PK-Konflikt **DELETE + INSERT**.
- Room's Flow-Invalidation-Tracker feuert bei DELETE und bei INSERT.
- Bei `upsertAll` mit N Rows → 2N Invalidation-Events → Flows feuern
  mehrfach mit Zwischenzuständen → stale/UI-flicker.
- Auf Wasm single-connection kann das zu Race-Conditions führen.
- **Lösung Verdacht C:** `INSERT(IGNORE)` + `UPDATE` für ALLE Tabellen
  (atomar, kein DELETE, kein Invalidation-Sturm).

#### D. Anti-Resurrektion greift nicht bei Folders/Chat-Messages
- Die Anti-Resurrektion in `_upsert_row` prüft `existing["deletedAt"]`.
- `chat_messages` und `folders` haben `deletedAt` → ok.
- `todos` und `habits` haben `deletedAt` → ok.
- **Aber:** `habit_logs` und `habit_history` haben KEIN `deletedAt` →
  `has_deleted_at = False` → Anti-Resurrektion übersprungen. Append-only
  → kein Problem (kein Löschen).
- Sieht korrekt aus. Nicht die Ursache.

#### E. `serverTimeOffset` Race beim Page-Load
- `SyncManager.init { serverTimeOffset = prefs.serverTimeOffset }`.
- Wenn `prefs.serverTimeOffset` stale ist (z.B. von gestern), und die
  Server-Zeit jetzt anders ist → `nowMs()` ist falsch → `updatedAt` für
  neue Todos ist in der Zukunft/vergangenheit → LWW-Probleme.
- Aber: nach erstem Sync wird `serverTimeOffset` aktualisiert. Das
  erklärt nicht warum Items beim Reload verschwinden.

#### F. `lastSyncedAt` wird zu früh gesetzt → Pull verpasst
- `prefs.lastSyncedAt = response.newSyncedAt` wird NACH `applyServerChanges`
  gesetzt. Wenn `applyServerChanges` crasht (z.B. durch Invalidation-
  Sturm) → `lastSyncedAt` wird nie gesetzt → nächster Sync ist Full-Pull.
  Das wäre gut (nicht schlecht).
- Wenn `applyServerChanges` TEILWEISE läuft (einige Rows upserted, dann
  Exception) → `lastSyncedAt` wird nicht gesetzt → nächster Sync
  re-pullt alles. Das ist ok (idempotent).
- Sieht nicht nach der Ursache aus.

### Plan zur Untersuchung & Fix

1. **Debug-Logging in `applyServerChanges` + `collectLocalChanges`:**
   - `APPLY: todos=N habits=N notes=N ...` pro Sync.
   - `COLLECT: todos=N (since=X) ...` pro Push.
   - `FLOW[todos]: emitting N items` wenn Flow feuert.
2. **`INSERT OR REPLACE` → `INSERT(IGNORE)` + `UPDATE` für alle Tabellen**
   (Verdacht C). Wie schon für habits gemacht.
3. **DB-Flow-Reaktivität testen:** Nach `upsertAll` manuell eine Query
   machen und vergleichen — wenn die Flow stale Daten zeigt aber die
   DB frische hat → Invalidation-Tracker-Problem.
4. **Fallback: manuelles Refresh nach Sync:** Wenn die Flow nicht feuert,
   nach `applyServerChanges` einen manuellen Refresh der ViewModels
   triggern (schmutzig, aber funktioniert).
5. **Anti-Resurrektion verifizieren:** Server-Logs beim Push schauen, ob
   `_upsert_row` gelöschte Items skippt.

### Wichtige Dateien
- `compose-app/composeApp/src/commonMain/kotlin/com/earendil/todonotes/
  data/sync/SyncManager.kt` — `collectLocalChanges`, `applyServerChanges`,
  `syncInternal`.
- `compose-app/composeApp/src/commonMain/kotlin/com/earendil/todonotes/
  data/dao/TodoDao.kt` — `getSince`, `upsertAll`, `observeOpenTodos`.
- `compose-app/composeApp/src/commonMain/kotlin/com/earendil/todonotes/
  data/dao/HabitDao.kt` — `getHabitsSince`, `insertOrIgnoreHabits`,
  `updateAllHabits`, `observeHabits`.
- `compose-app/composeApp/src/commonMain/kotlin/com/earendil/todonotes/
  data/dao/FolderDao.kt` — `getSince`, `upsertAll`, `observeFolders`.
- `compose-app/composeApp/src/commonMain/kotlin/com/earendil/todonotes/
  data/dao/ChatMessageDao.kt` — `getSince`, `upsertAll`.
- `backend/app/sync.py` — `_upsert_row`, `_collect_server_changes`,
  `sync`.
- `backend/app/main.py` — `sync_endpoint`.

### Commits (aktuelle Session)
- `2a1e90e` — Sync-A+ (reaktiver Editor + safe flush + getSince)
- `4a2547c` — @Upsert → INSERT OR REPLACE (Wasm-Transaktionsleck-Fix)
- `74bd1be` — Sync beim App-Resume
- `b8d4d39` — Initialer Sync beim Login
- `5c232dc` — Auto-Sync resilient (Exception killt nicht mehr collect)
- `a3a0e0e` — Sync-Indikator (Save-Icon + Sekunden)
- `25185ec` — flush() NonCancellable (Connection-Leak-Fix)
- `51f34c9` — Anti-Resurrektion + serverTimeOffset persistiert

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
