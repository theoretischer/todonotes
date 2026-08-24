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
- [ ] **Sync-A+ – Notiz-Sync bombenfest** (In Progress, siehe unten)
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

## Sync-A+ – Notiz-Sync bombenfest (In Progress)

**Problem:** Notiz-Sync ist datenverlustgefährdet. Konkrete Bugs:
1. Editor ist "blind" für Sync-Änderungen — lädt einmal, schreibt beim
   Verlassen drüber, sogar über gelöschte Notizen (`getById` statt
   `getLiveById`, kein `deletedAt`-Filter).
2. `flush()` liest eine potenziell veraltete DB-Version → `note.copy()`
   bewahrt `deletedAt` → Body in gelöschte Notiz = Datenverlust.
3. LWW auf ganze Notiz → bei gleichzeitigen Edits verschiedener Felder
   (Titel/Body) gewinnt eines, das andere geht verloren.
4. Löschen pflanzt sich nicht zum offenen Editor fort → Handy-Editor
   zeigt gelöschte Notiz, beim Verlassen überschreibt er sie.
5. `collectLocalChanges()` schickt jedes Mal ALLE Notizen.

**Lösung (Ansatz A+, evolutionär — kein OT/CRDT):**

### Schritt 1: `NoteDao` — reaktive Beobachtung
- `observeNote(id): Flow<Note>` hinzufügen (`SELECT * WHERE id = :id`,
  Room-Flow feuert bei UPDATE). Wird vom Editor beobachtet.

### Schritt 2: `NoteRepository.updateNote` — sicher
- `getLiveById` statt `getById` (filtert `deletedAt`).
- Wenn `null` → Notiz wurde gelöscht → `flush` bricht ab, Editor schließt.
- Expliziter Update: `dao.updateBody(id, title, bodyJson, now)` statt
  `note.copy()` (bewahrt `deletedAt` nicht versehentlich).
- `NoteDao.updateBody(id, title, bodyJson, updatedAt)` Query hinzufügen.

### Schritt 3: `NoteEditorViewModel` — reaktiv
- Beobachte `observeNote(id)` neben `load()`. Kommt Sync-Änderung rein:
  - Editor nicht dirty (Nutzer nicht am Tippen) → `_state` aktualisieren
    (Body/Titel), Cursor ans Ende. → **Live-Mitsehen am Handy!**
  - Editor dirty → puffern, nach flush mergen. (Stufe 1: überschreiben
    akzeptieren, Stufe 2 später: Konflikt-Dialog.)
- Bei gelöschter Notiz (`observeNote` liefert null oder `deletedAt != null`)
  → `_state.deleted = true` → Editor zeigt "Notiz wurde gelöscht" +
  schließt beim Verlassen.
- `NoteEditorState` um `deleted: Boolean = false` erweitern.

### Schritt 4: `NoteEditorScreen` — reaktiv
- `LaunchedEffect` auf VM-State: bei `state.deleted` → Toast/Dialog +
  `onBack()` (Editor schließt sich).
- Bei Body/Titel-Änderung aus Sync (nicht dirty) → `lines` neu parsen +
  Cursor ans Ende. NUR wenn der Nutzer nicht gerade tippt
  (`activeLineId` im selben Feld, keine Selektion).
- Auto-Save-Intervall auf 500ms (statt 1s) für Live-Gefühl.

### Schritt 5: `SyncManager` — nur geänderte Notizen pushen
- `collectLocalChanges` reduziert: Notizen mit `updatedAt > lastPushedAt`
  (pro Notiz gemerkt, global statt pro-Note reicht: `lastPushedAt =
  max(updatedAt aller gepushten Notizen)`).
- Alternativ: einfach `lastSyncedAt`-basiert (schon da) — Notizen mit
  `updatedAt > lastSyncedAt` pushen. Braucht keine neue Spalte.
  → `NoteDao.getAllForSyncSince(since): List<Note>` Query.
- Gleiche Logik für alle Tabellen (Todos, Habits, etc.) — Stufe 2.

### Schritt 6: LWW pro Feld (Stufe 2, optional später)
- Backend `_upsert_row` für Notes: Titel/Body separat vergleichen.
- Wenn incoming.title.updatedAt > existing.title.updatedAt → nur Titel.
- Braucht `titleUpdatedAt` + `bodyUpdatedAt` Spalten — oder einzeln
  2 Endpoints. Komplex, erst wenn Feld-Level-Konflikte real auftreten.

### Garantien (nach A+):
- **Kein Datenverlust durch Überschreiben gelöschter Notizen.**
- **Live-Mitsehen** am Handy (Body poppt auf, wenn PC speichert).
- **Editor schließt bei gelöschter Notiz** statt zu überschreiben.
- **Gleichzeitige Edits verschiedener Felder** — Stufe 1: LWW auf
  ganze Notiz (aktuell), Stufe 2: feldweise.
- **Sync-Push effizienter** — nur geänderte Notizen.

### Nicht umgesetzt (bewusst):
- Kein OT (Google-Docs-Style) — overkill, braucht WebSocket + komplexe
  Transformationslogik.
- Kein CRDT (Yjs/Automerge) — JS-Bibliotheken, Interop-Komplexität,
  Document-Größen-Wachstum.
- Keine echten Dateien statt DB — verliert Multi-User/atomare Queries,
  löst das LWW-Problem nicht (LWW pro Datei = LWW pro Notiz).
