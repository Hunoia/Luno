package hunoia.luno.bridge.vibration

import android.content.Context

private var appCtx: Context? = null

/** 震动用的应用上下文；由 `initVibrationContext` 在 App 启动时注入。 */
val appContext: Context? get() = appCtx

fun initVibrationContext(context: Context) {
    appCtx = context
}
