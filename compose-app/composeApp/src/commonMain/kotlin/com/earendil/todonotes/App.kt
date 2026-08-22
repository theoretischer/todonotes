package com.earendil.todonotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App-Einstiegspunkt (M7a — noch Skeleton; M7b baut das echte UI).
 *
 * Empfängt den [AppContainer] (Service-Locator) vom plattformspezifischen
 * Entry-Point. Aktuell nur Anzeige dass das Fundament steht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(container: AppContainer) {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("TodoNotes") }) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("M7a Fundament steht!")
                Text("Platform: ${getPlatform().name}")
                Text("DB-Tabelle todos: ${container.database.todoDao() != null}")
            }
        }
    }
}
