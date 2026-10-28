package hunoia.luno.runtime.surface

import android.os.SystemClock

/**
 * Surface 的隐藏状态机（纯状态，不碰窗口）。
 *
 * 三态语义见 architecture §7.2：
 * - 可见：由宿主自行判断；
 * - 软隐藏：面板计数 > 0 或临时隐藏未到期，窗口保留、不可见不可点；
 * - 摘窗：长态，由宿主决定 detach。
 *
 * 本类只回答「现在该不该软隐藏」，窗口的挂/摘与动画仍归具体 Surface。
 * 时钟可注入，便于单元测试。
 */
class SurfaceVisibilityState(
    private val now: () -> Long = SystemClock::uptimeMillis,
) {

    private var panelCountValue = 0
    private var tempHideUntilMs = 0L

    /** 在屏面板数；归零才允许恢复。 */
    val panelCount: Int get() = panelCountValue

    val isPanelHidden: Boolean get() = panelCountValue > 0

    /** 是否设置过临时隐藏（无论是否到期）。 */
    val hasTempHide: Boolean get() = tempHideUntilMs > 0L

    fun isTempHidden(): Boolean = tempHideUntilMs > now()

    fun tempHideRemainingMs(): Long = (tempHideUntilMs - now()).coerceAtLeast(0L)

    /** 软隐藏 = 面板未退场或临时隐藏未到期。 */
    val isHidden: Boolean get() = isPanelHidden || isTempHidden()

    fun onPanelShown() {
        panelCountValue++
    }

    fun onPanelDismissed() {
        if (panelCountValue > 0) panelCountValue--
    }

    /**
     * 设置临时隐藏；重复隐藏取更晚的截止时间。
     * @return 是否延长了截止时间（false = 已有更晚的隐藏，调用方无需重排定时器）。
     */
    fun applyTempHide(durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val deadline = now() + durationMs
        if (deadline <= tempHideUntilMs) return false
        tempHideUntilMs = deadline
        return true
    }

    fun cancelTempHide() {
        tempHideUntilMs = 0L
    }

    /** 宿主销毁 / 进程重建：面板计数与临时隐藏全部清零，避免卡在永久隐藏。 */
    fun reset() {
        panelCountValue = 0
        tempHideUntilMs = 0L
    }
}
