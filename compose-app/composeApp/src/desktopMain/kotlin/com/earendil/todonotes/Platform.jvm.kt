package com.earendil.todonotes

class DesktopPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isTouch: Boolean = false
    override val defaultServerUrl: String = ""
}

actual fun getPlatform(): Platform = DesktopPlatform()

/** Desktop: Window-Focus über AWT KeyboardFocusManager. Wenn das
 *  Hauptfenster wieder den Focus bekommt → onAppResume feuern → Sync. */
actual fun setupAppLifecyclePlatform(container: AppContainer) {
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addPropertyChangeListener("focusedWindow") { e ->
            if (e.newValue != null) {
                container.onAppResume?.invoke()
            }
        }
}
