package com.earendil.todonotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Desktop-actual: immer statisches ColorScheme (kein Dynamic Color). */
@Composable
internal actual fun resolveColorScheme(darkTheme: Boolean): ColorScheme =
    staticColorScheme(darkTheme)
