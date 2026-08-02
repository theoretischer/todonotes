package com.earendil.todonotes.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit-Tests für die 1D-Reorder-Logik (Block F6). */
class ReorderLogicTest {

    private val repos = listOf("a", "b", "c", "d")

    private fun swaps(): MutableList<Pair<String, String>> = mutableListOf()
    private fun onSwap(list: MutableList<Pair<String, String>>): (String, String) -> Unit =
        { a, b -> list.add(a to b) }

    // ---- ein Schritt nach unten (Finger langsam nach unten ziehen) ----

    @Test
    fun `weniger als die halbe Zeilenhoehe tauscht nicht`() {
        // Schwelle = halbe Zeilenhöhe (40 bei 80); 15px bleibt darunter.
        val s = ReorderSession("c", ReorderKind.NOTE, 2, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 80, dragAmountPx = 15f, onSwap = onSwap(out))
        assertEquals(2, step.newIndex)
        assertFalse(step.swapped)
        assertEquals(15f, step.newAccumPx)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `halbierte Schwelle tauscht schon beim Ueberqueren der halben Zeile`() {
        // h=80 → Schwelle 40; 45px überschreiten sie → 1 Tausch.
        val s = ReorderSession("c", ReorderKind.NOTE, 2, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 80, dragAmountPx = 45f, onSwap = onSwap(out))
        assertEquals(3, step.newIndex)
        assertTrue(step.swapped)
        assertEquals(listOf("c" to "d"), out)
    }

    @Test
    fun `eine Zeilenhoehe nach unten tauscht mit dem unteren Nachbarn`() {
        val s = ReorderSession("c", ReorderKind.NOTE, 2, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 80, dragAmountPx = 90f, onSwap = onSwap(out))
        assertEquals(3, step.newIndex)
        assertEquals(10f, step.newAccumPx)
        assertEquals(listOf("c" to "d"), out)
    }

    // ---- ein Schritt nach oben ----

    @Test
    fun `eine Zeilenhoehe nach oben tauscht mit dem oberen Nachbarn`() {
        val s = ReorderSession("b", ReorderKind.NOTE, 1, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 80, dragAmountPx = -90f, onSwap = onSwap(out))
        assertEquals(0, step.newIndex)
        assertEquals(listOf("b" to "a"), out)
    }

    // ---- Schrittwechsel über mehrere Zeilen in einem grossen Wurf ----

    @Test
    fun `mehrere Zeilenhohen tauschen mehrfach bei grosser Bewegung`() {
        val s = ReorderSession("a", ReorderKind.NOTE, 0, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 100, dragAmountPx = 250f, onSwap = onSwap(out))
        assertEquals(2, step.newIndex)
        assertEquals(listOf("a" to "b", "a" to "c"), out)
        // Rest nach 2x 100 von 250
        assertEquals(50f, step.newAccumPx)
    }

    // ---- Grenzen: erste/letzte Position ----

    @Test
    fun `erste Position kann nicht nach oben tauschen`() {
        val s = ReorderSession("a", ReorderKind.NOTE, 0, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 80, dragAmountPx = -90f, onSwap = onSwap(out))
        assertEquals(0, step.newIndex)
        assertFalse(step.swapped)
        assertTrue(out.isEmpty())
        // Rest bleibt, wird aber gedeckelt (nichts passiert weiter)
        assertEquals(-90f, step.newAccumPx)
    }

    @Test
    fun `letzte Position kann nicht nach unten tauschen`() {
        val s = ReorderSession("d", ReorderKind.NOTE, 3, 0f)
        val out = swaps()
        val step = reorderStep(s, repos, heightPx = 80, dragAmountPx = 90f, onSwap = onSwap(out))
        assertEquals(3, step.newIndex)
        assertFalse(step.swapped)
        assertTrue(out.isEmpty())
    }

    // ---- Restlicher Weg wird zwischen den Schritten mitgeführt ----

    @Test
    fun `angesammelter Rest landet im naechsten Schritt`() {
        // Erst ein halber Schritt (40px), dann nochmal 50px = 90px gesamt → genau 1 Tausch
        val outOne = swaps()
        val stepOne = reorderStep(
            ReorderSession("b", ReorderKind.NOTE, 1, 0f), repos, 80, 40f, onSwap(outOne)
        )
        assertFalse(stepOne.swapped)
        val outTwo = swaps()
        val stepTwo = reorderStep(
            ReorderSession("b", ReorderKind.NOTE, 1, stepOne.newAccumPx), repos, 80, 50f, onSwap(outTwo)
        )
        assertEquals(2, stepTwo.newIndex)
        assertEquals(listOf("b" to "c"), outTwo)
        assertEquals(10f, stepTwo.newAccumPx)
    }
}
