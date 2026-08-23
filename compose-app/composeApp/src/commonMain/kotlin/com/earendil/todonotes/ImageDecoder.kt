package com.earendil.todonotes

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodiert rohe Bild-Bytes (PNG/JPG) zu einem ImageBitmap.
 * Plattform-spezifisch: Android via BitmapFactory, Skiko (Desktop/Wasm)
 * via org.jetbrains.skia.Image.
 *
 * Liefert null bei Fehler (z.B. ungültige Bytes).
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
