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
- **0.6.0** (Backend): **Block D1 — Sync-Server.** Python 3.12 + FastAPI + SQLite (roh, kein ORM) in Docker (multi-stage, slim). `POST /sync` (alle Tabellen in einem Call), Last-Write-Wins über `updated_at`, Append-only für logs/history via INSERT OR IGNORE. Bearer-Token-Auth (constant-time). `/health` public. SQLite im Named-Volume (überlebt Rebuilds). docker-compose lauscht auf 127.0.0.1:8000 (für Reverse-Proxy). Alle Sync-Tests grün: health, 403 ohne Token, initialer Pull, Todo-Push-Roundtrip, Konflikt (älter verliert/neuer gewinnt), Habit+Log+History, finaler Pull. Repo auf GitHub (private, Account `theoretischer`, noreply-Email für Contribution-Graph).

## Erledigte Blöcke
- [x] **Block A – Todos**: erstellen/erledigen/bearbeiten/swipe-delete, Recurrence (RFC 5545), Option-C-Bugfix
- [x] **Block B – Gewohnheiten**: Cadence (n mal pro Tag/Woche/Monat/Jahr/n Tage), Goal-Count-Tracking, Perioden-Reset, Verlauf pro Periode, "Periode abschließen" im ⋮-Menü
- [x] **Migrations-Framework**: echte Room-Migrationen statt destructive, Schema-JSON, Backup-Sicherheitsnetz
- [x] **Block D1 – Sync-Server**: Docker + FastAPI + SQLite, `/sync` mit Last-Write-Wins, Token-Auth, alle 4 Tabellen, lokal validiert

## Offen
- [ ] **Block D2 – Android-Sync-Client**: Retrofit/OkHttp, WorkManager periodic sync, `last_synced_at` persistieren, Sync-Button/Pull-to-refresh, baseUrl + Token in Settings
- [ ] **Block D3 – Deploy**: auf Server mit Docker, Reverse-Proxy (Caddy) für `todo.christopherh.de` + HTTPS
- [ ] **Block E – Linux-Client**: GTK4, synchronisiert gegen denselben Server
- [ ] **Block F – Notizen**: ordentliche Notiz-Funktion (statt Platzhalter-Tab)
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
**Block D2 – Android-Sync-Client**: Retrofit-Service + WorkManager + Sync-Settings in der App.
