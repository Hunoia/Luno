package hunoia.luno.bridge.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 逐层剥 ContextWrapper 找宿主 Activity。Compose 树里 `LocalContext.current`
 * 可能是 ContextThemeWrapper，直接 as Activity 会炸；需要 Activity 本尊
 *（生命周期、Window、系统设置页）的地方都走这里。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}