package com.earendil.todonotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Android-actual: nutzt androidx.activity.compose.BackHandler (Hardware-Back). */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
