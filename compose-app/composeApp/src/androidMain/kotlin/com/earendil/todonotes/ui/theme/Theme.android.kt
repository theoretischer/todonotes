package com.earendil.todonotes.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Android-actual: IMMER statisch (M14: Dynamic Color deaktiviert — es
 *  würde das eigene Farbsystem mit den Wallpaper-Farben überschreiben). */
@Composable
internal actual fun resolveColorScheme(darkTheme: Boolean): ColorScheme =
    staticColorScheme(darkTheme)
