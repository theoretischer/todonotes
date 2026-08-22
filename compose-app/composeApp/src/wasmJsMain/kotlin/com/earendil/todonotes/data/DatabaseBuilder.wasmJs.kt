@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.earendil.todonotes.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver

/**
 * WasmJS actual: SQLite über Web Worker + OPFS (Origin Private File System).
 *
 * Nutzt [WebWorkerSQLiteDriver] aus androidx.sqlite:sqlite-web, der mit einem
 * Web Worker kommuniziert (worker.js im sqlite-web-worker/ Ordner). Der Worker
 * nutzt @sqlite.org/sqlite-wasm für persistente OPFS-Speicherung — Daten
 * überleben also Browser-Reloads.
 *
 * Der Worker wird in der JS-Datei sqlite-worker.js erstellt (siehe
 * [SqliteWorkerJs.kt]). Das ist notwendig, weil js() in Kotlin/Wasm den
 * Code als Property-Wert (nicht als Funktion) ins Import-Objekt einbettet.
 */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<TodoNotesDatabase> {
    val driver = WebWorkerSQLiteDriver(createSqliteWorker())
    return Room.databaseBuilder<TodoNotesDatabase>(
        name = "todonotes.db"
    ).setDriver(driver)
}
