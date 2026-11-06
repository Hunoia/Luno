package hunoia.luno.bridge.ui

import android.os.Looper
import hunoia.luno.core.bridge.BuildConfig

fun isMainThread(): Boolean = Looper.getMainLooper()?.isCurrentThread == true

/**
 * 阻塞调用进入前断言不在主线程（architecture §4.6，仅 Debug 生效）。
 *
 * 放在 `withContext(Dispatchers.IO/Default)` 内部，用来锁住“这段工作必须离开 Main”的契约：
 * 以后有人把外层的 `withContext` 去掉，Debug 构建会立刻在这里失败，而不是线上卡顿。
 */
fun assertNotMainThread(tag: String) {
    if (!BuildConfig.DEBUG) return
    requireNotMainThread(isMainThread(), tag)
}

/** 纯函数便于单测；[tag] 用于定位是哪段阻塞工作。 */
internal fun requireNotMainThread(isMain: Boolean, tag: String) {
    check(!isMain) { "阻塞调用 [$tag] 不得在主线程执行（architecture §4.6）" }
}
