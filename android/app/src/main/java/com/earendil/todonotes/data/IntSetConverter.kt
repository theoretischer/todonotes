package com.earendil.todonotes.data

import androidx.room.TypeConverter

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
