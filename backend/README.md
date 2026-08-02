# TodoNotes Backend

Minimaler Sync-Server für die TodoNotes-Android-App (und später Linux-Client).

**Stack**: Python 3.12 + FastAPI + SQLite (roh, kein ORM) + Docker.

## Schnellstart (lokal zum Entwickeln)

```bash
cd backend
cp .env.example .env          # SYNC_TOKEN setzen!
docker compose up -d --build
curl http://localhost:8000/health
# -> {"status":"ok"}
```

## Sync-Protokoll

Ein einziger Endpoint: `POST /sync`

```json
// Request
{
  "last_synced_at": 1722520000000,   // 0 beim ersten Sync
  "client_id": "android-s24-abc123",
  "changes": {
    "todos":            [ {Todo}, ... ],
    "habits":           [ {Habit}, ... ],
    "habit_logs":       [ {HabitLog}, ... ],
    "habit_history":    [ {HabitHistoryEntry}, ... ]
  }
}

// Response
{
  "new_synced_at": 1722520005000,
  "server_changes": {
    "todos":            [ {Todo}, ... ],
    "habits":           [ {Habit}, ... ],
    "habit_logs":       [ {HabitLog}, ... ],
    "habit_history":    [ {HabitHistoryEntry}, ... ]
  }
}
```

### Conflict Resolution: Last-Write-Wins
Pro Zeile: wenn `client.updated_at > server.updated_at` → Client gewinnt,
sonst → Server gewinnt. `deleted_at` ist ein normales Feld (Soft-Delete
wird synchronisiert wie jede andere Änderung).

### Datenvolumen
Der Server returniert **nur** Zeilen, deren `updated_at` (bzw. `logged_at`
bei Logs/History) > `last_synced_at` ist. Wenn sich nichts geändert hat,
sind die Listen leer → Response ist winzig.

## Auth

Bearer-Token in `Authorization: Bearer <SYNC_TOKEN>`. Token steht in `.env`
bzw. der Docker-Compose-Umgebung. Bei falschem Token → 401.

## Deploy auf Server (mit Reverse-Proxy)

Das Image lauscht auf Port 8000. Für `todo.christopherh.de` einen Reverse-Proxy
davor schalten (Caddy/Nginx/Traefik), der HTTPS terminiert und nach
`localhost:8000` weiterleitet. Beispiel-Caddyfile:

```
todo.christopherh.de {
    reverse_proxy localhost:8000
}
```

Caddy holt automatisch Let's-Encrypt-Zertifikate. In der Android-App dann als
`baseUrl` eintragen: `https://todo.christopherh.de`.

Für lokales Testen ohne Domain: `http://<server-ip>:8000` geht auch (dann
aber keinen Token produktiv nutzen, da HTTP unverschlüsselt).
