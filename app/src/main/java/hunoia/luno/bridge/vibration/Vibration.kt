package hunoia.luno.bridge.vibration

import android.Manifest.permission.VIBRATE
import android.content.Context

internal var appContext: Context? = null

fun initVibrationContext(context: Context) {
    appContext = context
}


