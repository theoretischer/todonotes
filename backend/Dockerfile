# ---- Build stage ----
FROM python:3.12-slim AS builder

WORKDIR /build
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ---- Runtime stage ----
FROM python:3.12-slim

WORKDIR /app

# Runtime deps für sqlite3 (in slim schon enthalten, aber sicherheitshalber)
RUN apt-get update && apt-get install -y --no-install-recommends \
    sqlite3 \
    && rm -rf /var/lib/apt/lists/*

# Python-Packages aus Builder-Stage kopieren
COPY --from=builder /install /usr/local

# App-Code
COPY app/ ./app/

# Web-App (Wasm-dist, per deploy-web.sh hereinkopiert und committet)
COPY web/ ./web/

# Daten-Volume (SQLite-Datei liegt hier, überlebt Container-Rebuilds)
VOLUME ["/app/data"]
ENV DB_PATH=/app/data/todonotes.db

EXPOSE 8000

# uvicorn lauscht auf allen Interfaces (0.0.0.0) → local-IP + Reverse-Proxy
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
