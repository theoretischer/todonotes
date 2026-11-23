package com.earendil.todonotes.data.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Link-Vorschau (F7): gecachte Open-Graph-Metadaten einer URL.
 *
 * Wird von [com.earendil.todonotes.data.repo.LinkPreviewRepository]
 * gefüllt, sobald eine URL erstmals dargestellt wird (Chat-Nachricht,
 * Todo-Titel/-Notiz). Die Vorschau-Karte zeigt Bild + Titel +
 * Beschreibung + Domain.
 *
 * **NICHT gesynct** — reiner Client-Cache (jedes Gerät holt sich seine
 * Vorschauen selbst; kein Server-Roundtrip nötig, und der Server müsste
 * sonst fremde URLs crawlen). Daher auch keine userId/LWW-Spalten.
 *
 * - url: normalisierte URL (ohne Fragment) — Primärschlüssel
 * - title/description/siteName: aus den og:-Tags (Fallback <title>)
 * - imageBytes: dekodierbares Bild (PNG/JPG) — als Blob, damit die
 *   Karte offline sofort da ist; null = kein Bild gefunden/ladbar
 * - fetchedAt: wann geholt → TTL für's Auffrischen
 * - failed: true = Fetch ist gescheitert (Negativ-Cache, damit wir
 *   kaputte URLs nicht bei jedem Anzeigen erneut anfragen)
 */
@Entity(tableName = "link_previews")
data class LinkPreview(
    @PrimaryKey
    val url: String,
    val title: String = "",
    val description: String = "",
    val siteName: String = "",
    val imageBytes: ByteArray? = null,
    val fetchedAt: Long = 0L,
    val failed: Boolean = false
) {
    // ByteArray in data class → equals/hashCode von Hand (sonst
    // Referenz-Vergleich, was bei Compose-`remember`-Keys stört).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LinkPreview) return false
        return url == other.url &&
            title == other.title &&
            description == other.description &&
            siteName == other.siteName &&
            fetchedAt == other.fetchedAt &&
            failed == other.failed &&
            (imageBytes?.contentEquals(other.imageBytes) ?: (other.imageBytes == null))
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + siteName.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + fetchedAt.hashCode()
        result = 31 * result + failed.hashCode()
        return result
    }
}
