package com.earendil.todonotes.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room-Migrationen für TodoNotes.
 *
 * ## Warum das hier wichtig ist
 * Frühe Dev-Phase: `fallbackToDestructiveMigration()` — bei jeder Schema-Änderung
 * wurde die DB gelöscht. Ab v5 ist das ABgeschafft: jede Schema-Änderung bekommt
 * eine echte Migration (ALTER TABLE / CREATE TABLE), Daten bleiben erhalten.
 *
 * ## Schema-Historie (wie wir hierhin kamen)
 *  - v1: nur `todos`
 *  - v2: + `habits`, `habit_logs` (Block B1)
 *  - v3: + `habit_history` (Block B10) — siehe HabitHistoryEntry
 *  - v4: entity-Set unverändert, aber Schema-Bereinigung
 *  - v5: `habits.byWeekdays` Spalte entfernt (Bugfix NOT-NULL-Crash)
 *  - v6: `notes` + `folders` Tabellen angelegt (Block F1, Notiz-App)
 *
 * Weil bisher destructive migriert wurde, stehen alle Live-Geräte praktisch auf
 * einem frisch angelegten v5-Schema. Die Migrationen 1→5 laufen daher in der
 * Praxis selten — sie dokumentieren die Historie und fangen alte Installs ab.
 *
 * ## Wie eine NEUE Migration ab v6 geschrieben wird
 * Wenn du ein Entity-Feld/Tabelle änderst:
 *   1. @Database(version = N+1) hochzählen
 *   2. Hier eine `val MIGRATION_N_Nplus1 = object : Migration(N, N+1) { ... }`
 *      mit den nötigen ALTER/CREATE-Statements anlegen
 *   3. In TodoNotesDatabase.get() bei addMigrations(...) registrieren
 *   4. schemas/{N+1}.json wird vom KSP-Plugin automatisch erzeugt → committen
 *
 * Beispiel:
 * ```
 * val MIGRATION_5_6 = object : Migration(5, 6) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE habits ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
 *     }
 * }
 * ```
 *
 * NEVER use fallbackToDestructiveMigration() again — das löscht Nutzerdaten.
 */
object Migrations {

    /** v1 → v2: habits + habit_logs Tabellen angelegt. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Schema wie von Room für die Entities v2 erzeugt.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habits` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `cadenceType` TEXT NOT NULL,
                    `interval` INTEGER NOT NULL,
                    `byWeekdays` TEXT,
                    `resetWeekday` INTEGER,
                    `resetAnchorDay` INTEGER,
                    `resetAnchorMonth` INTEGER,
                    `goalCount` INTEGER NOT NULL,
                    `startDate` INTEGER NOT NULL,
                    `logToHistory` INTEGER NOT NULL,
                    `lastLoggedPeriodStart` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habit_logs` (
                    `id` TEXT NOT NULL,
                    `habitId` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `note` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habitId` ON `habit_logs` (`habitId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_timestamp` ON `habit_logs` (`timestamp`)")
        }
    }

    /** v2 → v3: habit_history Tabelle angelegt. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habit_history` (
                    `id` TEXT NOT NULL,
                    `habitId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `cadenceLabel` TEXT NOT NULL,
                    `periodStart` INTEGER NOT NULL,
                    `count` INTEGER NOT NULL,
                    `goal` INTEGER NOT NULL,
                    `loggedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_history_habitId` ON `habit_history` (`habitId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_history_loggedAt` ON `habit_history` (`loggedAt`)")
        }
    }

    /** v3 → v4: entity-Set unverändert (Schema-Bereinigung, kein struktureller Wandel). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Keine Änderung an den Tabellen.
        }
    }

    /** v4 → v5: habits.byWeekdays Spalte entfernt (Bugfix NOT-NULL-Crash beim Insert). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // SQLite kann DROP COLUMN erst ab 3.35. Room emuliert es via
            // Tabellen-Umkopierung, damit wir minSdk-kompatibel bleiben.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `habits_new` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `cadenceType` TEXT NOT NULL,
                    `interval` INTEGER NOT NULL,
                    `resetWeekday` INTEGER,
                    `resetAnchorDay` INTEGER,
                    `resetAnchorMonth` INTEGER,
                    `goalCount` INTEGER NOT NULL,
                    `startDate` INTEGER NOT NULL,
                    `logToHistory` INTEGER NOT NULL,
                    `lastLoggedPeriodStart` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `habits_new` (
                    `id`,`title`,`notes`,`cadenceType`,`interval`,
                    `resetWeekday`,`resetAnchorDay`,`resetAnchorMonth`,
                    `goalCount`,`startDate`,`logToHistory`,`lastLoggedPeriodStart`,
                    `createdAt`,`updatedAt`,`deletedAt`
                )
                SELECT
                    `id`,`title`,`notes`,`cadenceType`,`interval`,
                    `resetWeekday`,`resetAnchorDay`,`resetAnchorMonth`,
                    `goalCount`,`startDate`,`logToHistory`,`lastLoggedPeriodStart`,
                    `createdAt`,`updatedAt`,`deletedAt`
                FROM `habits`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE IF EXISTS `habits`")
            db.execSQL("ALTER TABLE `habits_new` RENAME TO `habits`")
        }
    }

    /** v5 → v6: notes + folders Tabellen angelegt (Block F1, Notiz-App).
     *
     *  notes: id, folderId (null = Wurzel), title (erste Body-Zeile),
     *         bodyJson (serialisierter Rich-Text-Baum, F3), createdAt,
     *         updatedAt, deletedAt (Soft-Delete für Sync).
     *         Bilder werden NICHT als Base64 hierin gespeichert, sondern
     *         als Dateien + Referenz im bodyJson (F7/F9).
     *
     *  folders: id, parentId (null = Wurzel, sonst Unterordner), name,
     *           createdAt, updatedAt, deletedAt.
     *           Note.folderId referenziert Folder.id ohne harten FK,
     *           damit Sync ohne Reihenfolgen-Probleme funktioniert. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notes` (
                    `id` TEXT NOT NULL,
                    `folderId` TEXT,
                    `title` TEXT NOT NULL,
                    `bodyJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `folders` (
                    `id` TEXT NOT NULL,
                    `parentId` TEXT,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
        }
    }

    /** v6 → v7: notes + folders um Spalte `position` erweitert (1D-Reorder, Block F6). */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE folders ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v7 → v8: Chat-Dateien (Block H). `notes.type`-Spalte (NOTE/CHAT,
     *  default NOTE) + neue `chat_messages`-Tabelle (eigenes createdAt pro
     *  Nachricht, bleibt beim Bearbeiten stabil). */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Bestehende Notizen sind klassische Notizen → default 'NOTE'.
            // SQLite hat kein ENUM; wir speichern den Namen als TEXT.
            db.execSQL("ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT 'NOTE'")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL,
                    `noteId` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_noteId` ON `chat_messages` (`noteId`)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Block H-Quote: quotedMessageId für Zitate in Chat-Nachrichten.
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN quotedMessageId TEXT")
        }
    }

    /** Alle Migrationen, die Room ausführen darf. Reihenfolge ist egal — Room
     *  baut sich den Pfad von der alten zur neuen Version selbst zusammen. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )
}
