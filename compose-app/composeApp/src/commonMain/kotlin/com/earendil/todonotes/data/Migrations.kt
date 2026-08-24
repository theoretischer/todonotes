package com.earendil.todonotes.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Room 3 KMP Migrationen für TodoNotes.
 *
 * Entspricht der Android-Version (Migrations.kt), aber mit der neuen
 * Room 3 API: Migration.migrate(connection: SQLiteConnection) statt
 * migrate(db: SupportSQLiteDatabase). SQL wird via execSQL(conn, sql)
 * ausgeführt (plattformneutral, funktioniert auf Android/JVM/Wasm).
 *
 * ## Schema-Historie (unverändert)
 *  - v1: nur `todos`
 *  - v2: + `habits`, `habit_logs` (Block B1)
 *  - v3: + `habit_history` (Block B10)
 *  - v4: entity-Set unverändert, Schema-Bereinigung
 *  - v5: `habits.byWeekdays` Spalte entfernt (Bugfix NOT-NULL-Crash)
 *  - v6: `notes` + `folders` (Block F1, Notiz-App)
 *  - v7: `notes.position` + `folders.position` (F6 Reorder)
 *  - v8: `notes.type` + `chat_messages` (Block H)
 *  - v9: `chat_messages.quotedMessageId` (Block H-Quote)
 *  - v10: `userId` auf alle 7 Daten-Tabellen (M1 Multi-User-Auth)
 *
 * NEVER use fallbackToDestructiveMigration — das löscht Nutzerdaten.
 */
object Migrations {

    /** v1 → v2: habits + habit_logs Tabellen angelegt. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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
            connection.execSQL(
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
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habitId` ON `habit_logs` (`habitId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_timestamp` ON `habit_logs` (`timestamp`)")
        }
    }

    /** v2 → v3: habit_history Tabelle angelegt. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_history_habitId` ON `habit_history` (`habitId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_history_loggedAt` ON `habit_history` (`loggedAt`)")
        }
    }

    /** v3 → v4: entity-Set unverändert (Schema-Bereinigung, kein struktureller Wandel). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            // Keine Änderung an den Tabellen.
        }
    }

    /** v4 → v5: habits.byWeekdays Spalte entfernt (Bugfix NOT-NULL-Crash beim Insert). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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
            connection.execSQL(
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
            connection.execSQL("DROP TABLE IF EXISTS `habits`")
            connection.execSQL("ALTER TABLE `habits_new` RENAME TO `habits`")
        }
    }

    /** v5 → v6: notes + folders Tabellen angelegt (Block F1, Notiz-App). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
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
            connection.execSQL(
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
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
        }
    }

    /** v6 → v7: notes + folders um Spalte `position` erweitert (1D-Reorder, Block F6). */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE notes ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE folders ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v7 → v8: Chat-Dateien (Block H). `notes.type`-Spalte (NOTE/CHAT,
     *  default NOTE) + neue `chat_messages`-Tabelle. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT 'NOTE'")
            connection.execSQL(
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
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_noteId` ON `chat_messages` (`noteId`)")
        }
    }

    /** v8 → v9: quotedMessageId für Zitate in Chat-Nachrichten (Block H-Quote). */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE chat_messages ADD COLUMN quotedMessageId TEXT")
        }
    }

    /** v9 → v10: userId-Spalte auf alle 7 Daten-Tabellen (M1 Multi-User-Auth).
     *  Default 'legacy-user' für bestehende Daten. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE todos ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
            connection.execSQL("ALTER TABLE habits ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
            connection.execSQL("ALTER TABLE habit_logs ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
            connection.execSQL("ALTER TABLE habit_history ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
            connection.execSQL("ALTER TABLE notes ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
            connection.execSQL("ALTER TABLE folders ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
            connection.execSQL("ALTER TABLE chat_messages ADD COLUMN userId TEXT NOT NULL DEFAULT 'legacy-user'")
        }
    }

    /** v10 → v11: notificationStyle auf todos (M8 — 0=Vollbild, 1=nur
     *  Benachrichtigung, 2=stumm). Default 0 = bisheriges Verhalten. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE todos ADD COLUMN notificationStyle INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v11 → v12: Zufriedenheits-Tracker.
     *  - habits.type TEXT ('HABIT' oder 'SATISFACTION')
     *  - habits.currentRating INTEGER (nullable, nur bei SATISFACTION)
     *  - habits.position INTEGER (Drag-Drop-Reorder)
     *  - habit_history.newRating INTEGER (nullable, bei Rating-Änderungen) */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE habits ADD COLUMN type TEXT NOT NULL DEFAULT 'HABIT'")
            connection.execSQL("ALTER TABLE habits ADD COLUMN currentRating INTEGER")
            connection.execSQL("ALTER TABLE habits ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE habit_history ADD COLUMN newRating INTEGER")
        }
    }

    /** Alle Migrationen, die Room ausführen darf. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12
    )
}
