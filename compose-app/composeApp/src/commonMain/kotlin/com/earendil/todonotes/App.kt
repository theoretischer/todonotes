package com.earendil.todonotes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.earendil.todonotes.ui.theme.TodoNotesTheme

/**
 * App-Einstiegspunkt (M7b — echtes UI).
 *
 * Empfängt den [AppContainer] (Service-Locator) vom plattformspezifischen
 * Entry-Point und reicht ihn an [TodoNotesApp] weiter.
 */
@Composable
fun App(container: AppContainer) {
    TodoNotesTheme {
        TodoNotesApp(container)
    }
}
