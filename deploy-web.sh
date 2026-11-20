#!/usr/bin/env bash
# Baut die Web-App (Wasm) und kopiert das Ergebnis nach backend/web/.
# Danach: git add/commit/push → auf dem Server wie gewohnt
#   git pull && docker compose up -d --build
#
# Ohne .map-Debug-Dateien ist das Dist ~16MB (mit: ~18MB).
set -euo pipefail

cd "$(dirname "$0")/compose-app"

# WICHTIG: build/wasm/node_modules enthält eine KOPIE des lokalen npm-
# Paketes (@androidx/sqlite-web-worker). Gradle erkennt Aenderungen an
# worker.js dort NICHT zuverlässig -> veralteter Worker im Build!
#
# M14-Perf: Frueher rm -rf build/wasm (loeschte AUCH node_modules ->
# jedes Mal komplettes yarn install + alle Wasm-Tasks neu = 21-60s).
# Jetzt: die Kopie per rsync MIT DEM QUELLORDNER SYNCHRONISIEREN
# (nicht löschen — yarn-Install bleibt UP-TO-DATE, der Worker-Inhalt
# ist trotzdem immer frisch). GLEICHZEITIG das gelöschte Paket aus
# einem früheren Testlauf wiederherstellen, falls fehlt (idempotent).
mkdir -p build/wasm/node_modules/@androidx/sqlite-web-worker
rsync -a --delete sqlite-web-worker/ build/wasm/node_modules/@androidx/sqlite-web-worker/
# ...und die Kopie in packages/composeApp/kotlin (dorthin kopiert es
# der KGP-Task "wasmJsProcessResources" bzw. webpack-resolve — frisch
# halten, gleiche Quelle):
mkdir -p build/wasm/packages/composeApp/kotlin
rsync -a sqlite-web-worker/worker.js build/wasm/packages/composeApp/kotlin/sqlite-worker.js

echo ">>> Baue Web-App..."
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :composeApp:wasmJsBrowserDistribution

DIST="composeApp/build/dist/wasmJs/productionExecutable"
DEST="../backend/web"

echo ">>> Kopiere nach backend/web/ (ohne .map-Debug-Dateien)..."
rm -rf "$DEST"
mkdir -p "$DEST"
rsync -a --exclude='*.map' "$DIST"/ "$DEST"/

echo ">>> Fertig. Nächster Schritt:"
echo "    git add backend/web && git commit -m 'web: neu gebaut' && git push"
echo "    Auf dem Server: git pull && docker compose up -d --build"