package com.earendil.todonotes.data

import androidx.room3.RoomDatabase

/**
 * Konfiguriert den Database-Builder mit Migrationen und baut die
 * Database-Instanz.
 *
 * Der SQLiteDriver wird bereits im plattformspezifischen
 * [getDatabaseBuilder] gesetzt (BundledSQLiteDriver auf JVM/Android,
 * WebWorkerSQLiteDriver auf Wasm).
 */
fun buildDatabase(): TodoNotesDatabase {
    return getDatabaseBuilder()
        .addMigrations(*Migrations.ALL)
        .build()
}
