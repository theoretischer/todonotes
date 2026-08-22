package com.earendil.todonotes.data

import androidx.room3.RoomDatabase

/**
 * Plattformspezifischer Database-Builder.
 *
 * Android: braucht Context, Pfad via getDatabasePath.
 * Desktop (JVM): Pfad im User-Home.
 * Wasm: WebWorkerSQLiteDriver (OPFS).
 *
 * Die eigentliche Builder-Konfiguration (Driver, Migrationen) passiert
 * in [buildDatabase] in commonMain — nur die Pfad-Ermittlung ist
 * plattformspezifisch.
 */
expect fun getDatabaseBuilder(): RoomDatabase.Builder<TodoNotesDatabase>
