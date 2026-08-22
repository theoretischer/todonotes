package com.earendil.todonotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

/**
 * Multiplatform-Theme (M7b — commonMain).
 *
 * Basis: statische Light/Dark ColorSchemes. Auf Android (S+) kann zusätzlich
 * Dynamic Color aktiviert werden — das passiert im expect/actual
 * [resolveColorScheme], weil dynamicLightColorScheme/dynamicDarkColorScheme
 * Context brauchen und Android-only sind.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF7C3AED),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    secondary = Color(0xFFA78BFA),
)

/**
 * Liefert die ColorScheme — ggf. dynamic (Android S+), sonst statisch.
 * [darkTheme] = System-Dark-Mode.
 */
@Composable
fun TodoNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = resolveColorScheme(darkTheme)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Statisches Fallback-ColorScheme (commonMain). */
internal fun staticColorScheme(dark: Boolean): ColorScheme =
    if (dark) DarkColors else LightColors

/**
 * Plattform-spezifische ColorScheme-Auflösung.
 * - Android: Dynamic Color ab Android S (12), sonst statisch.
 * - Desktop/Wasm: immer statisch.
 */
@Composable
internal expect fun resolveColorScheme(darkTheme: Boolean): ColorScheme
