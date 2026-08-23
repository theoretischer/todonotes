#!/usr/bin/env bash
# Baut die Web-App (Wasm) und kopiert das Ergebnis nach backend/web/.
# Danach: git add/commit/push → auf dem Server wie gewohnt
#   git pull && docker compose up -d --build
#
# Ohne .map-Debug-Dateien ist das Dist ~16MB (mit: ~18MB).
set -euo pipefail

cd "$(dirname "$0")/compose-app"

# WICHTIG: build/wasm enthält eine KOPIE des lokalen npm-Paketes
# (@androidx/sqlite-web-worker). Gradle erkennt Aenderungen an worker.js
# dort NICHT zuverlässig -> veralteter Worker im Build! Deshalb vorher
# loeschen und mit --rerun-tasks bauen.
rm -rf build/wasm

echo ">>> Baue Web-App (dauert ~1-2 Min)..."
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon --rerun-tasks

DIST="composeApp/build/dist/wasmJs/productionExecutable"
DEST="../backend/web"

echo ">>> Kopiere nach backend/web/ (ohne .map-Debug-Dateien)..."
rm -rf "$DEST"
mkdir -p "$DEST"
rsync -a --exclude='*.map' "$DIST"/ "$DEST"/

echo ">>> Fertig. Nächster Schritt:"
echo "    git add backend/web && git commit -m 'web: neu gebaut' && git push"
echo "    Auf dem Server: git pull && docker compose up -d --build"
