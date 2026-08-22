package com.earendil.todonotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Wasm-actual: immer statisches ColorScheme (kein Dynamic Color im Browser). */
@Composable
internal actual fun resolveColorScheme(darkTheme: Boolean): ColorScheme =
    staticColorScheme(darkTheme)
