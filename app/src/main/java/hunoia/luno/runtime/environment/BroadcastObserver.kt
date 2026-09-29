package hunoia.luno.runtime.environment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

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
            },
            Context.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}
