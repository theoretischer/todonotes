package com.earendil.todonotes.data

import androidx.room3.ColumnTypeConverter
import com.earendil.todonotes.data.entity.NoteType

/** Konvertiert Set<Int> <-> "MO,TU,WE" String für Room. */
class IntSetConverter {
    @ColumnTypeConverter
    fun fromSet(set: Set<Int>?): String? =
        set?.takeIf { it.isNotEmpty() }?.joinToString(",")

    @ColumnTypeConverter
    fun toSet(value: String?): Set<Int> =
        if (value.isNullOrBlank()) emptySet()
        else value.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
}

/** Konvertiert NoteType <-> String (Room speichert kein Enum direkt). */
class NoteTypeConverter {
    @ColumnTypeConverter
    fun fromType(type: NoteType): String = type.name

    @ColumnTypeConverter
    fun toType(value: String): NoteType =
        runCatching { NoteType.valueOf(value) }.getOrDefault(NoteType.NOTE)
}
