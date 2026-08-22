package com.earendil.todonotes.ui

import androidx.compose.runtime.Composable

/** Wasm-actual: noop — kein Hardware-Back-Button. */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op — Screens haben TopBar-Zurück-Button.
}
