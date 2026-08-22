package com.earendil.todonotes.data

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android-actual: Holt den DB-Pfad vom Context und erstellt einen
 * Room.databaseBuilder. Der Context wird via [appContext] gesetzt —
 * wird beim App-Start in MainActivity aufgerufen.
 */

private var appContext: Context? = null

fun setAppContext(context: Context) {
    appContext = context.applicationContext
}

actual fun getDatabaseBuilder(): RoomDatabase.Builder<TodoNotesDatabase> {
    val ctx = appContext ?: error("setAppContext() muss vor DB-Zugriff aufgerufen werden")
    val dbFile = ctx.getDatabasePath("todonotes.db")
    return Room.databaseBuilder<TodoNotesDatabase>(
        context = ctx,
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
}
