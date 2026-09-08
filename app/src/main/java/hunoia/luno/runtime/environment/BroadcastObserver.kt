package hunoia.luno.runtime.environment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import hunoia.luno.core.Events
import hunoia.luno.bridge.WallpaperChangedEvent

class BroadcastObserver(
    private val context: Context,
    private val onScreenOff: () -> Unit = {},
    private val onUserPresent: () -> Unit = {},
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_USER_PRESENT -> onUserPresent()
                Intent.ACTION_WALLPAPER_CHANGED -> Events.post(WallpaperChangedEvent())
            }
        }
    }

    fun register() {
        if (registered) return
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_WALLPAPER_CHANGED)
            }
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
