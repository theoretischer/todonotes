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
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun logsSince(habitId: String, since: Long): List<HabitLog>

    /** Anzahl Logs eines Habits ab :since (für Count-Berechnung, ohne Liste zu laden). */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since")
    suspend fun countSince(habitId: String, since: Long): Int

    /** Reaktiver Count eines Habits ab :since — feuert neu, wenn habit_logs sich
     *  ändert. Für die Progress-Anzeige im UI (M7c-Fix: statt N×2 suspend-Queries
     *  pro refresh ein einzelner Flow pro Habit). */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since")
    fun observeCountSince(habitId: String, since: Long): Flow<Int>

    /** Alle Logs eines Habits (aufsteigend) — für die Perioden-Grafik im Tracker-Detail. */
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY timestamp ASC")
    fun observeLogsForHabit(habitId: String): Flow<List<HabitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLog)

    /** Neuesten Log eines Habits ab :since löschen (Undo des letzten +1). */
    @Query(
        """
        DELETE FROM habit_logs
        WHERE id = (
            SELECT id FROM habit_logs
            WHERE habitId = :habitId AND timestamp >= :since
            ORDER BY timestamp DESC LIMIT 1
        )
        """
    )
    suspend fun deleteLatestLogSince(habitId: String, since: Long)

    /** Alle Logs eines Habits ab :since löschen (Periode abschließen). */
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND timestamp >= :since")
    suspend fun deleteLogsSince(habitId: String, since: Long)

    /** Anzahl Logs eines Habits im Zeitraum [from, until). */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND timestamp >= :from AND timestamp < :until")
    suspend fun countBetween(habitId: String, from: Long, until: Long): Int

    /** Alle Logs eines Habits im Zeitraum [from, until) — für Count-Korrektur. */
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND timestamp >= :from AND timestamp < :until ORDER BY timestamp ASC")
    suspend fun logsBetween(habitId: String, from: Long, until: Long): List<HabitLog>

    /** Einzelnen Log per ID löschen (für Count-Korrektur). */
    @Query("DELETE FROM habit_logs WHERE id = :id")
    suspend fun deleteLogById(id: String)

    // ---- HabitHistory ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HabitHistoryEntry)

    /** Verlaufseintrag per id loeschen (fuer Swipe-to-delete im Verlauf-Tab). */
    @Query("DELETE FROM habit_history WHERE id = :id")
    suspend fun deleteHistoryEntry(id: String)

    /** History-Einträge für ein Habit + Periodenstart (für Count-Korrektur). */
    @Query("SELECT * FROM habit_history WHERE habitId = :habitId AND periodStart = :periodStart")
    suspend fun historyByPeriod(habitId: String, periodStart: Long): List<HabitHistoryEntry>

    // ---- Sync ----

    /** Alle Habits einmalig (inkl. soft-deleted, für Sync-Upstream). */
    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsForSync(): List<Habit>

    /** Nur Habits, die seit :since geändert wurden (Sync-A+: effizienter Push). */
    @Query("SELECT * FROM habits WHERE updatedAt > :since")
    suspend fun getHabitsSince(since: Long): List<Habit>

    /** Server-Änderungen einspielen. @Upsert = INSERT OR UPDATE (kein DELETE,
     *  kein CASCADE auf habit_logs/history). */
    @Upsert
    suspend fun upsertAllHabits(habits: List<Habit>)

    /** Alle Logs einmalig (für Sync-Upstream). */
    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogsForSync(): List<HabitLog>

    /** Server-Logs einspielen (@Upsert — kein DELETE/CASCADE). */
    @Upsert
    suspend fun upsertAllLogs(logs: List<HabitLog>)

    /** Alle History-Einträge einmalig. */
    @Query("SELECT * FROM habit_history")
    suspend fun getAllHistoryForSync(): List<HabitHistoryEntry>

    /** Server-History einspielen (@Upsert — kein DELETE/CASCADE). */
    @Upsert
    suspend fun upsertAllHistory(entries: List<HabitHistoryEntry>)

    /** Alle aktiven Habits einmalig (nicht reaktiv). */
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY position ASC, createdAt ASC")
    suspend fun getAllHabitsOnce(): List<Habit>

    /** Alle Verlaufseinträge aller Habits, neueste zuerst. */
    @Query("SELECT * FROM habit_history ORDER BY loggedAt DESC")
    fun observeHabitHistory(): Flow<List<HabitHistoryEntry>>

    /** Alle Zeilen löschen (lokaler Wipe nach Server-Wipe). */
    @Query("DELETE FROM habits")
    suspend fun clearAllHabits()

    @Query("DELETE FROM habit_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM habit_history")
    suspend fun clearAllHistory()
}
