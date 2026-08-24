package com.earendil.todonotes.ui.notes

/**
 * Pure Reorder-Logik für 1D-Drag der Notizen-Liste (Block F6).
 *
 * Long-Press + vertikale Bewegung tauscht die gezogene Zeile mit dem
 * Nachbarn, sobald der Fingerweg eine Zeilenhöhe überschreitet. Jeder
 * Tausch wird sofort über [onSwap] persistiert (position-Spalte in der DB),
 * sodass die Liste live neu geliefert wird — ohne Ghost-Overlay.
 *
 * Die Funktion ist bewusst rein (keine Compose-/Platform-Abhängigkeiten),
 * damit die Tausch-Schritte Unit-getestet werden können.
 */

/** Welcher Typ gerade neu sortiert wird — Ordner, Notizen oder Habits. */
enum class ReorderKind { FOLDER, NOTE, HABIT }

/**
 * Zustand einer laufenden Reorder-Geste.
 *
 * [draggedId] die gezogene Zeile, [kind] ihre Art, [index] ihr aktueller
 * Index innerhalb der gleichartigen Zeilen, [startIndex] der Index beim
 * Drag-Start (fuer die visuelle Offset-Berechnung) und [accumPx] der noch
 * nicht in einen Tausch umgerechnete vertikale Fingerweg (Rest, < 1 Zeilen-
 * hoehe).
 */
data class ReorderSession(
    val draggedId: String,
    val kind: ReorderKind,
    val index: Int,
    val startIndex: Int,
    val accumPx: Float
)

/** Ergebnis eines Reorder-Schritts: neuer Index + Restweg + ob getauscht wurde. */
data class ReorderStep(
    val newIndex: Int,
    val newAccumPx: Float,
    val swapped: Boolean
)

/**
 * Verarbeitet den zusätzlichen vertikalen Fingerweg [dragAmountPx] für die
 * laufende Geste [session] gegenüber der ID-Liste [repositories].
 *
 * Sobald [accumPx] eine halbe Zeilenhöhe [heightPx] überschreitet (positiv =
 * nach unten, negativ = nach oben), tauscht die gezogene Zeile mit ihrem
 * direkten Nachbarn über [onSwap] und der Index wandert mit; der Restweg
 * bleibt für den nächsten Schritt erhalten. Auf diese Weise schiebt die
 * gezogene Zeile die übrigen "zur Seite", während der Finger noch gehalten
 * wird.
 *
 * Mehrere Zeilen in einem Wurf werden einzeln verarbeitet: die Intern-Reihen-
 * folge wandert mit, sodass die gezogene Zeile nacheinander an jedem Nachbarn
 * vorbeizieht (nicht denselben Nachbarn mehrfach tauscht).
 */
fun reorderStep(
    session: ReorderSession,
    repositories: List<String>,
    heightPx: Int,
    dragAmountPx: Float,
    onSwap: (fromId: String, toId: String) -> Unit
): ReorderStep {
    val h = heightPx.coerceAtLeast(1)
    val threshold = h / 2f
    var accum = session.accumPx + dragAmountPx
    val work = repositories.toMutableList()
    var index = session.index.coerceIn(0, repositories.lastIndex)
    var swapped = false
    var crossing = true
    while (crossing) {
        if (accum > threshold && index < work.lastIndex) {
            val neighbor = work[index + 1]
            onSwap(session.draggedId, neighbor)
            work.removeAt(index)
            work.add(index + 1, session.draggedId)
            accum -= h
            index += 1
            swapped = true
        } else if (accum < -threshold && index > 0) {
            val neighbor = work[index - 1]
            onSwap(session.draggedId, neighbor)
            work.removeAt(index)
            work.add(index - 1, session.draggedId)
            accum += h
            index -= 1
            swapped = true
        } else {
            crossing = false
        }
    }
    return ReorderStep(index, accum, swapped)
}
