package com.earendil.todonotes.data.richtext

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serialisierungs-Brücke zwischen [NoteBody] und dem `bodyJson`-String,
 * der in der DB/sync transportiert wird (Block F3).
 *
 * Das Json-Objekt bewusst stabil gehalten (keine Pretty-Print, keine
 * nonstrict-Features die zur Laufzeit überraschen) — das Format soll
 * zwischen Client-Versionen und später dem Linux-Client kompatibel
 * bleiben.
 */
object NoteBodyJson {

    private val json = Json {
        ignoreUnknownKeys = true      // neue Felder (spätere Blocks) ignorieren → Forward-Kompatibilität
        encodeDefaults = true         // Defaults (z.B. style=NONE) ins JSON schreiben → deterministisch
        explicitNulls = false         // null-Felder (fontSize, color, caption) weglassen → kompakt
        classDiscriminator = "kind"    // Block/Segment-discriminator heißt "kind" (vermeidet Konflikt mit ListBlock.type)
    }

    /** [NoteBody] → kompakter JSON-String für DB/Sync. */
    fun encode(body: NoteBody): String = json.encodeToString(body)

    /** JSON-String → [NoteBody]. Wirft bei kaputtem JSON nicht, sondern
     *  liefert einen leeren Body (eine kaputte Notiz ist besser als ein Crash). */
    fun decode(bodyJson: String): NoteBody =
        if (bodyJson.isBlank()) EMPTY_BODY
        else runCatching { json.decodeFromString<NoteBody>(bodyJson) }
            .getOrElse { EMPTY_BODY }

    /** Direkt nach dem Decoden ggf. leere Notiz mit einem Paragraph anlegen. */
    fun decodeOrNew(bodyJson: String): NoteBody {
        val body = decode(bodyJson)
        return if (body.blocks.isEmpty()) newBodyWithEmptyParagraph() else body
    }
}
