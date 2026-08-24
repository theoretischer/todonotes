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
