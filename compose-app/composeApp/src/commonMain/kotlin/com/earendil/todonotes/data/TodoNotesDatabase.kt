package com.earendil.todonotes.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.earendil.todonotes.data.dao.ChatMessageDao
import com.earendil.todonotes.data.dao.FolderDao
import com.earendil.todonotes.data.dao.HabitDao
import com.earendil.todonotes.data.dao.NoteDao
import com.earendil.todonotes.data.dao.TodoDao
import com.earendil.todonotes.data.entity.ChatMessage
import com.earendil.todonotes.data.entity.Folder
import com.earendil.todonotes.data.entity.Habit
import com.earendil.todonotes.data.entity.HabitHistoryEntry
import com.earendil.todonotes.data.entity.HabitLog
import com.earendil.todonotes.data.entity.Note
import com.earendil.todonotes.data.entity.Todo

/**
 * Room 3 KMP Database für TodoNotes.
 *
 * Schema v10 (M1: userId auf alle Tabellen).
 *
 * Die plattformspezifische Konstruktion (Database-Builder) liegt in
 * expect/actual: [getDatabaseBuilder] pro Plattform, dann [buildDatabase]
 * hier in commonMain mit BundledSQLiteDriver + Migrationen.
 */
@Database(
    entities = [Todo::class, Habit::class, HabitLog::class, HabitHistoryEntry::class,
        Folder::class, Note::class, ChatMessage::class],
    version = 11,
    exportSchema = true
)
@ColumnTypeConverters(IntSetConverter::class, NoteTypeConverter::class)
@ConstructedBy(TodoNotesDatabaseConstructor::class)
abstract class TodoNotesDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun chatMessageDao(): ChatMessageDao
}

@Suppress("KotlinNoActualForExpect")
expect object TodoNotesDatabaseConstructor : RoomDatabaseConstructor<TodoNotesDatabase> {
    override fun initialize(): TodoNotesDatabase
}
