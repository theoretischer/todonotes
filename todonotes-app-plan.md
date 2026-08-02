# Todo/Notes App – Architekturplan

## 1. Tech-Stack

| Komponente | Sprache/Framework | Warum |
|---|---|---|
| Android-App | Kotlin + Jetpack Compose | Fullscreen-Intents, Material 3, WorkManager/AlarmManager brauchen native Android-APIs – kein Web/PWA-Ansatz möglich |
| Linux-App | Python + PyGObject (GTK4 + libadwaita) | Schnellste Iteration, native GNOME-Optik "for free", genug Performance für eine Todo-App. Alternative: Rust + gtk-rs, wenn du später mehr Performance/Robustheit willst |
| Backend | Python + FastAPI + SQLite | Minimaler Server, leicht selbst zu hosten (z.B. auf Raspberry Pi / kleinem VPS), SQLite reicht für Ein-Nutzer-Betrieb |
| Sync-Protokoll | REST + Polling (später optional WebSocket für Push) | Einfach zu debuggen, funktioniert auch bei instabiler Verbindung |
| Recurrence-Engine | RFC 5545 RRULE via Library (`dmfs/lib-recur` auf Android, `python-dateutil.rrule` auf Backend/Linux) | Nicht selbst bauen – Edge Cases (Monatsende, Schaltjahre) sind eine Falle |

Warum kein CalDAV: spart zwar das Backend, aber RRULE-Edge-Cases wie "maximal 2x die Woche" sind kein Standard-CalDAV-Feature und du müsstest das trotzdem selbst dranbauen. Eigenes Backend gibt dir volle Kontrolle.

---

## 2. Datenmodell (zentral, gilt für Backend + beide Clients)

```
Todo
├── id            UUID (client-generiert, damit Offline-Erstellung konfliktfrei ist)
├── title          string
├── notes          string (optional, langer Text)
├── due_at         datetime | null
├── recurrence     RRULE-String | null (z.B. "FREQ=WEEKLY;BYDAY=WE")
├── recurrence_extra  JSON | null   (für Nicht-RRULE-Regeln wie "max 2x/Woche")
├── completed_at   datetime | null
├── created_at     datetime
├── updated_at     datetime          (für Conflict Resolution)
├── deleted_at     datetime | null   (Soft Delete – wichtig für Sync!)
```

Notiz-only Einträge sind einfach ein `Todo` ohne `due_at`/`recurrence`.

---

## 3. Ablauf: Erstellen/Bearbeiten eines Todos

```
[Client (Android/Linux)]
   1. User erstellt/ändert Todo
   2. Sofortiger lokaler Write in lokale SQLite-DB
   3. updated_at = jetzt
   4. Eintrag in "pending_sync" Queue (oder einfach: alles mit
      updated_at > last_synced_at wird beim nächsten Sync geschickt)
   5. UI updated sofort (optimistic update) – kein Warten auf Server
```

## 4. Ablauf: Sync

```
[Client] --POST /sync {last_synced_at, changed_items}--> [Backend]

Backend:
   - nimmt eingehende changed_items
   - pro Item: wenn Server-Version neuer (updated_at) → Client-Update verwerfen
              wenn Client-Version neuer → übernehmen
   - gibt alle Items zurück, die seit last_synced_at auf dem Server
     geändert wurden (inkl. von anderen Clients)

[Client] <--Response {server_changes, new_sync_timestamp}-- [Backend]
   - merged server_changes in lokale DB
   - speichert new_sync_timestamp
```

Conflict-Resolution-Strategie: **Last-Write-Wins** über `updated_at`. Für eine Ein-Personen-App reicht das locker – kein Bedarf für CRDTs o.ä.

Trigger für Sync: beim App-Start, alle X Minuten im Hintergrund (Android: WorkManager periodic; Linux: einfacher Timer/Cronjob), und manuell per Pull-to-refresh/Button.

---

## 5. Ablauf: Recurrence → Notification (Android)

```
1. Todo mit recurrence gespeichert
2. Client berechnet via RRULE-Library die nächste(n) Occurrence(s)
3. Für die nächste fällige Zeit: AlarmManager.setExactAndAllowWhileIdle(...)
4. Zum Zeitpunkt X: BroadcastReceiver wird geweckt
5. BroadcastReceiver baut Notification mit:
     - NotificationChannel (Importance = HIGH)
     - .setFullScreenIntent(pendingIntent, true)
6. System zeigt Fullscreen-Activity über Sperrbildschirm
7. Nach Fertig-Erledigen: nächste Occurrence wird neu berechnet + neuer Alarm gesetzt
```

**Wichtig zu prüfen:** Ab Android 14 ist `USE_FULL_SCREEN_INTENT` nicht mehr automatisch gewährt für alle Apps – du musst zur Laufzeit über `NotificationManager.canUseFullScreenIntent()` checken und den Nutzer ggf. zu den Einstellungen schicken, falls die Permission fehlt. Einmalig einzurichten, danach kein Problem mehr.

---

## 6. Entwicklungsreihenfolge (Phasen)

1. **Phase 0 – Fundament:** Datenmodell final festlegen, RRULE-Format für deine Spezialfälle definieren (v.a. "max 2x/Woche")
2. **Phase 1 – Android MVP (lokal, kein Sync):** Todos anlegen/abhaken, Recurrence, Fullscreen-Notification. Hier merkst du am schnellsten, ob sich die App gut anfühlt.
3. **Phase 2 – Backend:** FastAPI mit `/todos` CRUD + `/sync` Endpoint, simpler Auth-Token (kein OAuth nötig für Ein-Nutzer-App)
4. **Phase 3 – Android Sync:** Client an Backend anbinden
5. **Phase 4 – Linux App:** GTK4/libadwaita-Client, gleiche Sync-Logik wie Android
6. **Phase 5 – Design-Politur:** Material 3 Feinschliff auf Android, libadwaita-Konventionen auf Linux (Adwaita-Farben, richtige Header-Bars, etc.)
7. **Phase 6 – Extras:** Tags/Kategorien, Suche, was dir sonst noch fehlt

Empfehlung: nicht alle Phasen "fertig" machen bevor du weitermachst – nach Phase 1 schon eine Woche lang echt benutzen, dann merkst du, was in den Spezialregeln noch fehlt, bevor du Zeit in Sync/zweite Plattform steckst.

---

## 7. Repo-Struktur (Vorschlag)

```
todo-app/
├── backend/          (FastAPI, SQLite, Sync-Endpoint)
├── android/           (Kotlin, Jetpack Compose)
├── linux/             (Python, GTK4/libadwaita)
└── shared/            (RRULE-Definitionen, API-Schema als Referenz/Doku)
```
