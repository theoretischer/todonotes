package com.earendil.todonotes

import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity

/**
 * „Teilen mit der App" (M16): Share-Flow-Controller, Android-only.
 *
 * Eine andere App teilt Text (ACTION_SEND, text/plain) an uns:
 * 1. MainActivity empfängt den Intent (onCreate + onNewIntent — App kann
 *    via Share schon laufen oder frisch gestartet werden) und leitet ihn
 *    an [handleOnce] weiter.
 * 2. Der geteilte Text landet als Prefill im Quick-Add-Feld des Aufgaben-
 *    Tabs. Nutzer-Regeln:
 *      - Haken drücken → Aufgabe direkt erstellt → Endbedingung.
 *      - Notebook-Knopf neben den Optionen → „An Notiz anhängen"-Modus
 *        im Notizen-Tab: Ordner öffnen, Chat/Notiz antippen.
 *          - Notiz → Text ans Ende der Notiz (ShareAppend-Regeln), die
 *            Notiz wird NICHT geöffnet.
 *          - Chat → Text als neue Chat-Nachricht.
 *      - Endbedingung: finaler Sync (falls dirty) + moveTaskToBack →
 *        zurück zur absendenden App (Share-Activity läuft in deren Task).
 *
 * Der Picker ist bewusst „ein Schuss": Nach dem Einfügen (oder Back/
 * Tab-Wechsel ohne Auswahl) ist der Share-Status weg. Kein halb offener
 * Modus, der später überrascht.
 */
class ShareFlowController(private val activity: ComponentActivity) {

    /** Damit onCreate+onNewIntent nicht doppelt feuern (je nach Start-Weg
     *  kommt der Intent auf beiden Wegen). */
    private var lastIntent: Intent? = null

    /** Intent aus onCreate/onNewIntent. Liefert true, wenn es ein
     *  ACTION_SEND-Text-Intent war (dann ist der Share-Flow aktiv). */
    fun handleOnce(intent: Intent): Boolean {
        if (lastIntent == intent) return false
        lastIntent = intent
        if (intent.action != Intent.ACTION_SEND) return false
        if (intent.type?.startsWith("text/") != true) return false
        val text = extractText(intent) ?: return false
        if (text.isBlank()) return false
        ContainerAccess.get(activity).sharedText = text
        return true
    }

    private fun extractText(intent: Intent): String? {
        val cs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Absender können String ODER CharSequence (z.B. Spannable) packen.
            intent.getParcelableExtra(Intent.EXTRA_TEXT, android.os.Parcelable::class.java)
                as? CharSequence ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        } else {
            @Suppress("DEPRECATION")
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }
        return cs?.toString()
    }
}