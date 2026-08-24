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
