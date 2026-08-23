// Erzeugt den SQLite-Web-Worker für OPFS-persistente Datenbanken.
// Wird von Kotlin/Wasm via @JsModule("./sqlite-worker.js") als
// external fun createSqliteWorker() importiert.
export function createSqliteWorker() {
    return new Worker(new URL('@androidx/sqlite-web-worker/worker.js', import.meta.url));
}
