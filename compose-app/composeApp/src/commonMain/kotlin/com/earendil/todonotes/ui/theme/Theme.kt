package com.earendil.todonotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

/**
 * Multiplatform-Theme (M7b — commonMain; Farbsystem M14).
 *
 * EIGENES Farbsystem statt Material-Dynamic-Color:
 *   - EIN blauer Akzent, sonst Grautöne. Rot ausschließlich zum Löschen.
 *   - Dark: Hintergrund #1c1d21, Karten/Nav #2a2c32, Eingabe #34363d,
 *     Text #e8e9ec, gedämpft #8b8e97, Akzent #8ab4ff/#2c3d63, Löschen #a8322b.
 *   - Light: #f6f6f8/#ffffff/#ececf0, Text #1c1d21, gedämpft #6c6f78,
 *     Akzent #2f5fc4/#dbe5fb, Löschen #B3261E.
 * Gefüllte Flächen ohne Outline, Radius 16-24px (shapes), keine Elevation.
 *
 * KEIN Dynamic Color (Android S+) mehr — das hätte die Wallpaper-Farben
 * über unser System gestaltet; resolveColorScheme ist überall statisch.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5FC4),          // Akzent (Blau)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE5FB),  // Akzent-Hintergrund
    onPrimaryContainer = Color(0xFF1C1D21),
    secondary = Color(0xFF2F5FC4),        // ein einziger Akzent — kein Violett
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE5FB),
    onSecondaryContainer = Color(0xFF1C1D21),
    tertiary = Color(0xFF2F5FC4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE5FB),
    onTertiaryContainer = Color(0xFF1C1D21),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF1C1D21),
    surface = Color(0xFFFFFFFF),           // Fläche (Karte)
    onSurface = Color(0xFF1C1D21),
    surfaceVariant = Color(0xFFECECF0),     // Fläche 2
    onSurfaceVariant = Color(0xFF6C6F78),   // gedämpfter Text
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECECF0),
    surfaceContainerHighest = Color(0xFFECECF0),
    outline = Color(0xFFC9CBD1),            // Linie
    outlineVariant = Color(0xFFE0E1E6),
    error = Color(0xFFB3261E),              // Löschen (universell)
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFB3261E),
    onErrorContainer = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),           // Akzent (Blau)
    onPrimary = Color(0xFF1C1D21),
    primaryContainer = Color(0xFF2C3D63), // Akzent-Hintergrund
    onPrimaryContainer = Color(0xFFE8E9EC),
    secondary = Color(0xFF8AB4FF),
    onSecondary = Color(0xFF1C1D21),
    secondaryContainer = Color(0xFF2C3D63),
    onSecondaryContainer = Color(0xFFE8E9EC),
    tertiary = Color(0xFF8AB4FF),
    onTertiary = Color(0xFF1C1D21),
    tertiaryContainer = Color(0xFF2C3D63),
    onTertiaryContainer = Color(0xFFE8E9EC),
    background = Color(0xFF1C1D21),
    onBackground = Color(0xFFE8E9EC),
    surface = Color(0xFF2A2C32),           // Fläche (Karte, Nav)
    onSurface = Color(0xFFE8E9EC),
    surfaceVariant = Color(0xFF34363D),    // Fläche 2 (Eingabefeld)
    onSurfaceVariant = Color(0xFF8B8E97),  // gedämpfter Text
    surfaceContainer = Color(0xFF2A2C32),
    surfaceContainerHigh = Color(0xFF34363D),
    surfaceContainerHighest = Color(0xFF34363D),
    outline = Color(0xFF44464E),           // Linie
    outlineVariant = Color(0xFF3A3C43),
    error = Color(0xFFA8322B),             // Löschen
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFA8322B),
    onErrorContainer = Color(0xFFFFFFFF),
)

/**
 * Liefert das ColorScheme — IMMER statisch (unser eigenes Farbsystem,
 * kein Dynamic Color; s. Dateikopf).
 */
@Composable
fun TodoNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = resolveColorScheme(darkTheme)
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Statisches ColorScheme (commonMain). */
internal fun staticColorScheme(dark: Boolean): ColorScheme =
    if (dark) DarkColors else LightColors

/**
 * Plattform-spezifische ColorScheme-Auflösung — überall statisch (M14:
 * Dynamic Color bewusst deaktiviert, es würde unser Farbsystem
 * überschreiben).
 */
@Composable
internal expect fun resolveColorScheme(darkTheme: Boolean): ColorScheme