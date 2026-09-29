package hunoia.luno.bridge.vibration

import android.content.Context

internal var appContext: Context? = null

fun initVibrationContext(context: Context) {
    appContext = context
}


