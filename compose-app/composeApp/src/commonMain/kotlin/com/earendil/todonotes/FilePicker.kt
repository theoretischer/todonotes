package com.earendil.todonotes

import androidx.compose.runtime.Composable

/**
 * Registriert einen Bildauswahl-Dialog (Platform-spezifisch).
 *
 * Liefert eine launch-Funktion zurueck. Wird diese aufgerufen,
 * oeffnet sich der Datei-Dialog. Nach der Auswahl wird onPicked
 * mit den rohen Bild-Bytes aufgerufen (oder null bei Abbruch).
 *
 * Android: ActivityResultContracts.GetContent
 * Desktop: javax.swing.JFileChooser
 * Wasm:    HTML input file
 */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit
