package com.earendil.todonotes

import androidx.compose.runtime.Composable

/**
 * Wasm: HTML <input type="file"> mit JS interop.
 * Liest das Bild als Data-URL, extrahiert Base64, decodiert zu ByteArray.
 */
@Composable
actual fun rememberImagePicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    return {
        pickImageBase64 { base64 ->
            if (base64.isEmpty()) {
                onPicked(null)
            } else {
                try {
                    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                    val bytes = kotlin.io.encoding.Base64.decode(base64)
                    onPicked(bytes)
                } catch (e: Exception) {
                    onPicked(null)
                }
            }
        }
    }
}

/** JS-Interop: öffnet <input type="file">, liest Bild als Base64-String. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun pickImageBase64(callback: (String) -> Unit) {
    js("""
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = function(e) {
        var file = e.target.files[0];
        if (!file) { callback(""); return; }
        var reader = new FileReader();
        reader.onload = function(ev) {
            var dataUrl = ev.target.result;
            var base64 = dataUrl.split(',')[1];
            callback(base64);
        };
        reader.onerror = function() { callback(""); };
        reader.readAsDataURL(file);
    };
    input.click();
    """)
}
