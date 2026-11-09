package hunoia.luno.bridge.ui

import android.view.WindowInsets
import android.view.WindowManager

/**
 * 浮层安全区（屏幕绝对坐标，px）：只约束上下。
 *
 * 产品口径：垂直避让**当前可见**的状态栏 / 导航栏（全屏游戏里没有系统栏就不留空），
 * 水平贴物理屏边 —— 横屏的挖孔 / 侧边系统区不参与避让。全应用浮层（悬浮球、胶囊、
 * 网格面板、快捷启动）共用这一份口径，不各写一套。
 */
data class SystemBarsSafeArea(val top: Int = 0, val bottom: Int = 0) {
    companion object {
        val None = SystemBarsSafeArea()
    }
}

/**
 * 读屏幕绝对坐标的系统栏安全区：`currentWindowMetrics` 与 IME 让位同源，避免小窗
 * `rootWindowInsets` 的「窗口被覆盖量」参考系随窗口位置漂移。失败返回 null，调用方
 * 可沿用上一次的值（不要静默清成 0，否则球会溜进状态栏）。
 */
fun WindowManager.systemBarsSafeAreaOrNull(): SystemBarsSafeArea? = runCatching {
    val bars = currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
    SystemBarsSafeArea(top = bars.top, bottom = bars.bottom)
}.getOrNull()

/** [systemBarsSafeAreaOrNull] 的非空版本：拿不到按 0 处理（等价于「没有系统栏」）。 */
fun WindowManager.systemBarsSafeArea(): SystemBarsSafeArea =
    systemBarsSafeAreaOrNull() ?: SystemBarsSafeArea.None
