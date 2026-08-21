package com.earendil.todonotes.data.richtext

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Rich-Text-Modell für Notiz-Bodies (Block F3).
 *
 * Eine Notiz besteht aus einer geordneten Liste von [Block]-Elementen.
 * Jeder Block ist einer von:
 *  - [Block.Paragraph]  — Fließtext aus formatierten [Segment]en
 *  - [Block.ListBlock]  — Liste (geordnet, Bullet, Pfeil, Checkbox)
 *  - [Block.ImageBlock] — Bild-Referenz (Datei liegt im App-Storage)
 *
 * Serialisierung via kotlinx.serialization → landet als JSON-String in
 * `notes.bodyJson`. Das Format ist kompakt und sync-freundlich (kein
 * HTML/RTF, kein externer Editor nötig).
 *
 * Zeichnungen (F8) kommen später als [Block.DrawingBlock] dazu.
 */

// ---- Top-Level ----

/**
 * Der Notiz-Body: geordnete Liste von Blöcken.
 * Leere Notiz = leere Liste. Wird direkt als JSON-Array serialisiert.
 */
@Serializable
data class NoteBody(
    val blocks: List<Block> = emptyList()
)

// ---- Block-Typen ----

@Serializable
sealed class Block {

    /** Fließtext-Absatz aus einem oder mehreren formatierten [Segment]en. */
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val segments: List<Segment>
    ) : Block()

    /**
     * Liste von [ListItem]s. [type] bestimmt das Präfix vor jedem Item:
     *  - [ListType.ORDERED]  → 1. 2. 3.
     *  - [ListType.BULLET]   → •
     *  - [ListType.ARROW]    → →
     *  - [ListType.CHECKBOX] → ☐ / ☑ (getoggelt durch [ListItem.checked])
     */
    @Serializable
    @SerialName("list")
    data class ListBlock(
        val items: List<ListItem>,
        val type: ListType
    ) : Block()

    /**
     * Bild. Die eigentlichen Pixeldaten liegen als Datei im App-Internal-
     * Storage (`files/notes/<noteId>/<imageId>.png`), NICHT als Base64 im
     * JSON ( würde DB/Sync aufblähen). Hier steht nur die Referenz.
     * [caption] ist optionaler Text unter dem Bild.
     */
    @Serializable
    @SerialName("image")
    data class ImageBlock(
        val imageId: String,
        val width: Int,
        val height: Int,
        val caption: String? = null
    ) : Block()
}

// ---- Segment (inline-Formatierung) ----

/**
 * Ein Text-Stück mit Formatierung. Zusammenhängender Text in einem
 * [Paragraph] wird an Stilwechseln in mehrere Segmente zerlegt.
 * [fontSize] in pt (null = Default des Renderers), [color] als ARB-Int
 * (null = Default-Farbe).
 */
@Serializable
data class Segment(
    val text: String,
    val style: TextStyle = TextStyle.NONE,
    val fontSize: Int? = null,
    val color: Long? = null
)

/** Text-Stil. Können später erweitert werden (strikeThrough etc.). */
@Serializable
enum class TextStyle {
    @SerialName("none") NONE,
    @SerialName("bold") BOLD,
    @SerialName("italic") ITALIC,
    @SerialName("underline") UNDERLINE
}

// ---- Listen ----

@Serializable
enum class ListType {
    @SerialName("ordered") ORDERED,
    @SerialName("bullet") BULLET,
    @SerialName("arrow") ARROW,
    @SerialName("checkbox") CHECKBOX
}

/** Ein einzelnes List-Item. [checked] nur relevant für [ListType.CHECKBOX]. */
@Serializable
data class ListItem(
    val segments: List<Segment>,
    val checked: Boolean = false
)

// ---- Helper / Defaults ----

/** Eine leere Notiz (keine Blöcke). */
val EMPTY_BODY = NoteBody()

/** convenience: Notiz mit einem leeren Paragraph starten (neue Notiz). */
fun newBodyWithEmptyParagraph(): NoteBody = NoteBody(blocks = listOf(Block.Paragraph(listOf(Segment("")))))
