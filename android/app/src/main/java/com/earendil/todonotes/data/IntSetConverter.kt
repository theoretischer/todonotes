package com.earendil.todonotes.data

import androidx.room.TypeConverter
import com.earendil.todonotes.data.entity.NoteType

/** Konvertiert Set<Int> <-> "MO,TU,WE" String für Room. */
class IntSetConverter {
    @TypeConverter
    fun fromSet(set: Set<Int>?): String? =
        set?.takeIf { it.isNotEmpty() }?.joinToString(",")

    @TypeConverter
    fun toSet(value: String?): Set<Int> =
        if (value.isNullOrBlank()) emptySet()
        else value.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
}

/** Konvertiert NoteType <-> String (Room speichert kein Enum direkt). */
class NoteTypeConverter {
    @TypeConverter
    fun fromType(type: NoteType): String = type.name

    @TypeConverter
    fun toType(value: String): NoteType =
        runCatching { NoteType.valueOf(value) }.getOrDefault(NoteType.NOTE)
}
