package com.earendil.todonotes.ui

import androidx.compose.runtime.Composable

/**
 * System-Back-Handler (M7d — commonMain expect/actual).
 *
 * Android: nutzt androidx.activity.compose.BackHandler (Hardware-Back).
 * Wasm/Desktop: noop — kein Hardware-Back-Button. Die Screens haben
 * stattdessen einen Zurück-Button in der TopBar.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
