package hunoia.luno.bridge.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 逐层剥 ContextWrapper 找宿主 Activity，找不到返回 null。
 *
 * Compose 树里 `LocalContext.current` 拿到的可能是 ContextThemeWrapper 之类，
 * 直接 as Activity 会炸。凡是需要 Activity 本尊（生命周期、Window、系统设置页）的地方都走这里。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}