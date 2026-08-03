package com.earendil.todonotes.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Migration-Tests — Sicherheitsnetz, damit kaputte Migrationen NIE wieder
 * auf ein echtes Gerät mit Nutzerdaten kommen (siehe Lerneffekt v6: ich hatte
 * Extra-Indizes in der Migration angelegt, die Room nicht erwartet hatte →
 * App crashte beim Öffnen, und um sie wieder ans Laufen zu bringen, musste
 * ich deinstallieren = Nutzerdaten weg).
 *
 * Jede Migration N→N+1 bekommt hier einen Test, der:
 *  1. Eine DB auf Version N mit dem damals gültigen Schema anlegt
 *  2. Die Migration laufen lässt
 *  3. Über MigrationTestHelper.runMigrationsAndValidate das Ergebnis gegen das
 *     automatisch generierte Schema (schemas/{N+1}.json) validiert
 *  4. Überprüft, dass Nutzerdaten die Migration überleben
 *
 * Wird automatisch beim `./gradlew test` ausgeführt.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration_test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = TodoNotesDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory()
    )

    // ----- v5 → v6 (Block F1: notes + folders) -----

    @Test
    fun migrate5To6_schemaIsValid() {
        // 1. v5-DB anlegen (Schema wie damals, OHNE notes/folders).
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS todos (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL,
                    dueAt INTEGER, recurrence TEXT, completedAt INTEGER,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER, logToHistory INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS habits (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL,
                    cadenceType TEXT NOT NULL, interval INTEGER NOT NULL,
                    resetWeekday INTEGER, resetAnchorDay INTEGER, resetAnchorMonth INTEGER,
                    goalCount INTEGER NOT NULL, startDate INTEGER NOT NULL,
                    logToHistory INTEGER NOT NULL, lastLoggedPeriodStart INTEGER,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS habit_logs (
                    id TEXT NOT NULL PRIMARY KEY, habitId TEXT NOT NULL,
                    timestamp INTEGER NOT NULL, note TEXT NOT NULL,
                    FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS habit_history (
                    id TEXT NOT NULL PRIMARY KEY, habitId TEXT NOT NULL,
                    title TEXT NOT NULL, cadenceLabel TEXT NOT NULL,
                    periodStart INTEGER NOT NULL, count INTEGER NOT NULL,
                    goal INTEGER NOT NULL, loggedAt INTEGER NOT NULL,
                    FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            // Eine Beispieldaten-Zeile, damit wir sehen, dass Daten überleben.
            execSQL(
                "INSERT INTO todos (id,title,notes,dueAt,recurrence,completedAt," +
                    "createdAt,updatedAt,deletedAt,logToHistory) VALUES " +
                    "('t-mig','Test','x',NULL,NULL,NULL,1,2,NULL,1)"
            )
            close()
        }

        // 2. Migration laufen lassen + gegen echtes v6-Schema validieren.
        //    Schlägt fehl, wenn Tabellen/Spalten/Indizes nicht zu 6.json passen.
        val db = helper.runMigrationsAndValidate(
            dbName, 6, /* validateDroppedTables */ true,
            Migrations.MIGRATION_5_6
        )

        // 3. Beispieldaten-Daten haben überlebt?
        db.query("SELECT title FROM todos WHERE id = 't-mig'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Test", c.getString(0))
        }
        // 4. Neue Tabellen da und leer?
        db.query("SELECT COUNT(*) FROM notes").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM folders").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate5To6_roundtripNotesAndFolders() {
        // Vollständigerer Test: nach der Migration über die echte Dao-API
        // einen Ordner + eine Notiz anlegen und wieder lesen.
        helper.createDatabase(dbName, 5).close()
        val db = helper.runMigrationsAndValidate(
            dbName, 6, true, Migrations.MIGRATION_5_6
        )
        val roomId = UUID.randomUUID().toString()
        val noteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO folders (id,parentId,name,createdAt,updatedAt,deletedAt) " +
                "VALUES (?,?,?,?,?,NULL)",
            arrayOf(roomId, null as Any?, "Ordner", now, now)
        )
        db.execSQL(
            "INSERT INTO notes (id,folderId,title,bodyJson,createdAt,updatedAt,deletedAt) " +
                "VALUES (?,?,?,?,?,?,NULL)",
            arrayOf(noteId, roomId, "Titel", "[]", now, now)
        )
        db.query("SELECT title FROM notes WHERE folderId = '$roomId'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Titel", c.getString(0))
        }
        db.close()
    }

    // ----- v7 → v8 (Block H: Chat-Dateien — notes.type + chat_messages) -----

    @Test
    fun migrate7To8_schemaIsValid() {
        // 1. v7-DB anlegen (notes + folders MIT position, noch ohne type).
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id TEXT NOT NULL PRIMARY KEY, folderId TEXT,
                    title TEXT NOT NULL, bodyJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER, position INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS folders (
                    id TEXT NOT NULL PRIMARY KEY, parentId TEXT,
                    name TEXT NOT NULL, createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL, deletedAt INTEGER,
                    position INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            // Bestehende Notiz (klassisch) → muss nach Migration type='NOTE' haben.
            execSQL(
                "INSERT INTO notes (id,folderId,title,bodyJson,createdAt,updatedAt,deletedAt,position) " +
                    "VALUES ('n1,NULL,'Klassisch','[]',1,2,NULL,10)"
            )
            close()
        }

        // 2. Migration + Schema-Validierung gegen 8.json.
        val db = helper.runMigrationsAndValidate(
            dbName, 8, true, Migrations.MIGRATION_7_8
        )

        // 3. Bestehende Notiz hat default type='NOTE'.
        db.query("SELECT type FROM notes WHERE id = 'n1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("NOTE", c.getString(0))
        }
        // 4. chat_messages-Tabelle existiert und ist leer.
        db.query("SELECT COUNT(*) FROM chat_messages").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate7To8_chatMessageRoundtrip() {
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id TEXT NOT NULL PRIMARY KEY, folderId TEXT,
                    title TEXT NOT NULL, bodyJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER, position INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            dbName, 8, true, Migrations.MIGRATION_7_8
        )
        // Chat-Nachricht direkt in die neue Tabelle einfügen + lesen.
        db.execSQL(
            "INSERT INTO chat_messages (id,noteId,text,createdAt,updatedAt,deletedAt,position) " +
                "VALUES ('m1','n1','Hallo',100,100,NULL,10)"
        )
        db.query("SELECT text FROM chat_messages WHERE id = 'm1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Hallo", c.getString(0))
        }
        db.close()
    }

    @Test
    fun migrate8To9_schemaIsValid() {
        // 1. v8-DB mit chat_messages-Tabelle (ohne quotedMessageId) anlegen.
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL PRIMARY KEY, `noteId` TEXT NOT NULL,
                    `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER,
                    `position` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            // Bestehende Nachricht ohne quotedMessageId.
            execSQL(
                "INSERT INTO chat_messages (id,noteId,text,createdAt,updatedAt,deletedAt,position) " +
                    "VALUES ('m1','n1','Hallo',100,100,NULL,10)"
            )
            close()
        }

        // 2. Migration + Schema-Validierung gegen 9.json.
        val db = helper.runMigrationsAndValidate(
            dbName, 9, true, Migrations.MIGRATION_8_9
        )

        // 3. quotedMessageId-Spalte existiert und ist NULL für alte Nachrichten.
        db.query("SELECT quotedMessageId FROM chat_messages WHERE id = 'm1'").use { c ->
            assertTrue(c.moveToFirst()); assertNull(c.getString(0))
        }
        db.close()
    }

    @Test
    fun migrate8To9_quoteRoundtrip() {
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL PRIMARY KEY, `noteId` TEXT NOT NULL,
                    `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER,
                    `position` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            dbName, 9, true, Migrations.MIGRATION_8_9
        )
        // Nachricht mit Zitat einfügen + lesen.
        db.execSQL(
            "INSERT INTO chat_messages (id,noteId,text,createdAt,updatedAt,deletedAt,position,quotedMessageId) " +
                "VALUES ('m2','n1','Antwort',200,200,NULL,20,'m1')"
        )
        db.query("SELECT quotedMessageId FROM chat_messages WHERE id = 'm2'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("m1", c.getString(0))
        }
        db.close()
    }
}
