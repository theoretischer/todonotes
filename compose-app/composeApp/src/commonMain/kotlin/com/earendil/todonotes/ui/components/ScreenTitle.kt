package com.earendil.todonotes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Große Titelzeile oben links über dem Screen (M14) — der „Anker", an dem
 * sich der Screen orientiert. Aufgaben/Gewohnheiten/Verlauf bekommen ihn
 * („Aufgaben"/„Gewohnheiten"/„Verlauf"); Notizen NICHT (dort ist der Ordner-
 * Breadcrumb der Anker).
 *
 * Liegt ÜBER der scrollenden Liste (nicht mitgescrollt), unter der Status-
 * leiste (statusBarsPadding), linksbündig.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}