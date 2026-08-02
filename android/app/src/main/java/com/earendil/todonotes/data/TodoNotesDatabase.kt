package com.earendil.todonotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.earendil.todonotes.data.dao.FolderDao
import com.earendil.todonotes.data.dao.HabitDao
import com.earendil.todonotes.data.dao.NoteDao
import com.earendil.todonotes.data.dao.TodoDao
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.Todo

@Database(
    entities = [Todo::class, Habit::class, HabitLog::class, HabitHistoryEntry::class,
        Folder::class, Note::class],
    version = 6,
    // Schema-JSON pro Version nach app/schemas/ schreiben (via KSP room.schemaLocation).
    // Wird committet → Nachvollziehbarkeit + Basis für Migrations-Tests.
    exportSchema = true
)
@TypeConverters(IntSetConverter::class)
abstract class TodoNotesDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: TodoNotesDatabase? = null

        fun get(context: Context): TodoNotesDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // Sicherheitsnetz: vor dem Öffnen die aktuelle DB-Datei
                    // wegkopieren (todonotes.db -> todonotes.db.bak-v<version>).
                    // Falls eine Migration später mal fehlschlägt, kann man das
                    // Backup manuell zurückspielen statt Daten zu verlieren.
                    backupDatabaseFile(context.applicationContext, 6)
                    Room.databaseBuilder(
                        context.applicationContext,
                        TodoNotesDatabase::class.java,
                        "todonotes.db"
                    )
                        // Echte Migrationen statt destructive wipe. NIEMALS
                        // fallbackToDestructiveMigration() — das löscht Nutzerdaten.
                        .addMigrations(*Migrations.ALL)
                        .build()
                        .also { INSTANCE = it }
                }
            }

        /** Kopiert databases/todonotes.db nach databases/todonotes.db.bak-v<version>,
         *  falls vorhanden. Fehlschläge werden nur geloggt (Backup ist Best-Effort). */
        private fun backupDatabaseFile(context: Context, version: Int) {
            try {
                val dbFile = context.getDatabasePath("todonotes.db")
                if (!dbFile.exists()) return
                val bak = dbFile.resolveSibling("todonotes.db.bak-v$version")
                // Pro Version nur einmal sichern, danach nicht überschreiben.
                if (bak.exists()) return
                dbFile.copyTo(bak, overwrite = false)
            } catch (e: Exception) {
                android.util.Log.w("TodoNotesDB", "Backup der DB fehlgeschlagen", e)
            }
        }
    }
}
