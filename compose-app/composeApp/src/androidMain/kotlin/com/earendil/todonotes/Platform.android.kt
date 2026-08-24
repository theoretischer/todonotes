package com.earendil.todonotes

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val isTouch: Boolean = true
    override val defaultServerUrl: String = ""
}

actual fun getPlatform(): Platform = AndroidPlatform()

/** Android: Lifecycle wird in MainActivity via LifecycleEventObserver
 *  gehandhabt (ON_RESUME → onAppResume). Hier Noop. */
actual fun setupAppLifecyclePlatform(container: AppContainer) { }
