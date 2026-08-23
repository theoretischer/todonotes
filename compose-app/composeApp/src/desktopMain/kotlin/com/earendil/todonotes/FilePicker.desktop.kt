package com.earendil.todonotes

import androidx.compose.runtime.Composable
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    return {
        Thread {
            SwingUtilities.invokeLater {
                val fc = JFileChooser()
                fc.fileFilter = FileNameExtensionFilter("Bilder", "png", "jpg", "jpeg")
                if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    try {
                        onPicked(fc.selectedFile.readBytes())
                    } catch (e: Exception) {
                        onPicked(null)
                    }
                } else {
                    onPicked(null)
                }
            }
        }.start()
    }
}
