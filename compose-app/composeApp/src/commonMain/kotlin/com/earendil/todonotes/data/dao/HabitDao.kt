package com.earendil.todonotes.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Upsert
import androidx.room3.Query
import androidx.room3.Update
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    /** Alle aktiven Habits (nicht gelöscht), sortiert nach Erstellungszeit. */
    @Query(
        """
        SELECT * FROM habits
        WHERE deletedAt IS NULL
        ORDER BY position ASC, createdAt ASC
        """
    )
    fun observeHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): Habit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: Habit)

    @Update
    suspend fun update(habit: Habit)

    @Query("UPDATE habits SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // ---- HabitLogs ----

    /** Alle Logs eines Habits ab :since (Start der aktuellen Periode). */
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since AND deletedAt IS NULL ORDER BY timestamp ASC")
    suspend fun logsSince(habitId: String, since: Long): List<HabitLog>

    /** Anzahl Logs eines Habits ab :since (für Count-Berechnung, ohne Liste zu laden). */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since AND deletedAt IS NULL")
    suspend fun countSince(habitId: String, since: Long): Int

    /** Reaktiver Count eines Habits ab :since — feuert neu, wenn habit_logs sich
     *  ändert. Für die Progress-Anzeige im UI (M7c-Fix: statt N×2 suspend-Queries
     *  pro refresh ein einzelner Flow pro Habit). */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since AND deletedAt IS NULL")
    fun observeCountSince(habitId: String, since: Long): Flow<Int>

    /** Alle Logs eines Habits (aufsteigend) — für die Perioden-Grafik im Tracker-Detail. */
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND deletedAt IS NULL ORDER BY timestamp ASC")
    fun observeLogsForHabit(habitId: String): Flow<List<HabitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLog)

    /** Neuesten nicht-gelöschten Log ab :since SOFT-löschen (Undo des letzten +1).
     *  M11: Soft-Delete statt hart — sonst liefert der Full-Down-Sync das
     *  hart gelöschte Undo zurück und der Count springt wieder hoch. */
    @Query(
        """
        UPDATE habit_logs SET deletedAt = :now, updatedAt = :now
        WHERE id = (
            SELECT id FROM habit_logs
            WHERE habitId = :habitId AND timestamp >= :since AND deletedAt IS NULL
            ORDER BY timestamp DESC LIMIT 1
        )
        """
    )
    suspend fun deleteLatestLogSince(habitId: String, since: Long, now: Long)

    /** Alle Logs eines Habits ab :since SOFT-löschen (Periode abschließen,
     *  Counter-Reset). M11: Tombstones statt hartes Löschen (Sync!). */
    @Query(
        """
        UPDATE habit_logs SET deletedAt = :now, updatedAt = :now
        WHERE habitId = :habitId AND timestamp >= :from AND deletedAt IS NULL
        """
    )
    suspend fun deleteLogsSince(habitId: String, from: Long, now: Long)

    /** Anzahl Logs eines Habits im Zeitraum [from, until). */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND timestamp >= :from AND timestamp < :until AND deletedAt IS NULL")
    suspend fun countBetween(habitId: String, from: Long, until: Long): Int

    /** Alle Logs eines Habits im Zeitraum [from, until) — für Count-Korrektur. */
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND timestamp >= :from AND timestamp < :until AND deletedAt IS NULL ORDER BY timestamp ASC")
    suspend fun logsBetween(habitId: String, from: Long, until: Long): List<HabitLog>

    /** Einzelnen Log per ID SOFT-löschen (Count-Korrektur). M11: Sync! */
    @Query("UPDATE habit_logs SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun deleteLogById(id: String, now: Long)

    // ---- HabitHistory ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HabitHistoryEntry)

    /** M11: Verlaufseintrag soft-löschen (Swipe-to-delete im Verlauf-Tab).
     *  Tombstone bleibt für den Sync → Löschung wird mitgesynct (vorher
     *  append-only → Löschung kam per Full-Down-Sync immer zurück). */
    @Query("UPDATE habit_history SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteHistoryEntry(id: String, now: Long)

    /** History-Einträge für ein Habit + Periodenstart (für Count-Korrektur). */
    @Query("SELECT * FROM habit_history WHERE habitId = :habitId AND periodStart = :periodStart AND deletedAt IS NULL")
    suspend fun historyByPeriod(habitId: String, periodStart: Long): List<HabitHistoryEntry>

    // ---- Sync ----

    /** Alle Habits einmalig (inkl. soft-deleted, für Sync-Upstream). */
    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsForSync(): List<Habit>

    /** Nur Habits, die seit :since geändert wurden (Sync-A+: effizienter Push). */
    @Query("SELECT * FROM habits WHERE updatedAt > :since")
    suspend fun getHabitsSince(since: Long): List<Habit>

    /** Server-Änderungen einspielen: INSERT(IGNORE) + UPDATE.
     *  Zweistufig aber sicher: kein DELETE → kein CASCADE auf habit_logs/history.
     *  @Insert(REPLACE) würde DELETE+INSERT machen und habit_logs/history
     *  löschen → Datenverlust. @Upsert wirft auf Wasm Exception bei
     *  PK-Konflikt → Transaktionsleck. INSERT(IGNORE)+UPDATE umgeht beides. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreHabits(habits: List<Habit>)

    @Update
    suspend fun updateAllHabits(habits: List<Habit>)

    /** Alle Logs einmalig (für Sync-Upstream). */
    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogsForSync(): List<HabitLog>

    /** Server-Logs einspielen (@Insert REPLACE — atomar, keine Childs → sicher). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllLogs(logs: List<HabitLog>)

    /** Alle History-Einträge einmalig. */
    @Query("SELECT * FROM habit_history")
    suspend fun getAllHistoryForSync(): List<HabitHistoryEntry>

    /** Server-History einspielen (@Insert REPLACE — atomar, keine Childs → sicher). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllHistory(entries: List<HabitHistoryEntry>)

    /** Alle aktiven Habits einmalig (nicht reaktiv). */
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY position ASC, createdAt ASC")
    suspend fun getAllHabitsOnce(): List<Habit>

    /** Alle Verlaufseinträge aller Habits, neueste zuerst. */
    @Query("SELECT * FROM habit_history WHERE deletedAt IS NULL ORDER BY loggedAt DESC")
    fun observeHabitHistory(): Flow<List<HabitHistoryEntry>>

    /** Alle Zeilen löschen (lokaler Wipe nach Server-Wipe). */
    @Query("DELETE FROM habits")
    suspend fun clearAllHabits()

    @Query("DELETE FROM habit_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM habit_history")
    suspend fun clearAllHistory()
}
