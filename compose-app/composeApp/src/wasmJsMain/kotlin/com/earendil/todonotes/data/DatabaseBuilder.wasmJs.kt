package com.earendil.todonotes.data

import androidx.room3.Room
import androidx.room3.RoomDatabase

/**
 * WasmJS actual: WebWorkerSQLiteDriver (OPFS-basiert).
 *
 * TODO (M9): Web Worker mit SQLite WASM einrichten — braucht worker.js
 * im resources + @JsFun für new Worker(...). Siehe:
 * https://github.com/danysantiago/room-web-demo
 *
 * Vorerst: inMemoryBuilder (Daten überleben Reload nicht).
 * Für persistente Wasm-DB siehe M9 (Web-Target).
 */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<TodoNotesDatabase> {
    // In-Memory für jetzt — persistente OPFS-DB kommt in M9.
    return Room.inMemoryDatabaseBuilder<TodoNotesDatabase>()
}
