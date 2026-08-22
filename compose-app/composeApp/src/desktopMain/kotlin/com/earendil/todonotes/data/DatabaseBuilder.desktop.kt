package com.earendil.todonotes.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * Desktop (JVM) actual: DB im User-Home-Verzeichnis.
 * ~/.todonotes/todonotes.db
 */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<TodoNotesDatabase> {
    val home = System.getProperty("user.home")
    val dir = File(home, ".todonotes").apply { mkdirs() }
    val dbFile = File(dir, "todonotes.db")
    return Room.databaseBuilder<TodoNotesDatabase>(
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
}
