package hunoia.luno.runtime.surface

/**
 * Surface 的隐藏状态机（纯状态，不碰窗口）。
 *
 * 三态语义见 architecture §7.2：
 * - 可见：由宿主自行判断；
 * - 软隐藏：面板计数 > 0 或界面级临时隐藏生效，窗口保留、不可见不可点；
 * - 摘窗：长态，由宿主决定 detach。
 *
 * 本类只回答「现在该不该软隐藏」，窗口的挂/摘与动画仍归具体 Surface。
 */
class SurfaceVisibilityState {

    private var panelCountValue = 0

    /**
     * 界面级临时隐藏（动作 `internal.hideFloatBall` 触发）：隐藏期间停留的前台界面
     * （应用维度，与场景配置同源）。null = 未隐藏。纯内存，进程重启即失效。
     */
    private var screenHidePackage: String? = null

    /** 在屏面板数；归零才允许恢复。 */
    val panelCount: Int get() = panelCountValue

    val isPanelHidden: Boolean get() = panelCountValue > 0

    /** 界面级临时隐藏是否生效：进入后停留期间恒真，前台离开 / 再触发 / 复位时清除。 */
    val isScreenHidden: Boolean get() = screenHidePackage != null

    /** 软隐藏 = 面板未退场或界面级临时隐藏生效。 */
    val isHidden: Boolean get() = isPanelHidden || isScreenHidden

    fun onPanelShown() {
        panelCountValue++
    }

    fun onPanelDismissed() {
        if (panelCountValue > 0) panelCountValue--
    }

    /**
     * 进入界面级临时隐藏：记录触发时的前台界面。
     * @return true = 状态有变化（调用方需要刷新窗口）；已隐藏时返回 false（无变化）。
     */
    fun enterScreenHide(currentPackage: String): Boolean {
        if (currentPackage.isBlank()) return false
        if (screenHidePackage != null) return false
        screenHidePackage = currentPackage
        return true
    }

    /**
     * 清除界面级临时隐藏（动作再触发 = 立即恢复）。
     * @return true = 确实清了槽（调用方需要刷新窗口）。
     */
    fun clearScreenHide(): Boolean {
        if (screenHidePackage == null) return false
        screenHidePackage = null
        return true
    }

    /**
     * 前台离开即清：前台换成其它应用（含回桌面）或已锁屏时隐藏态失效——槽在离开
     * 瞬间清掉，返回同一界面不会重新隐藏。
     * @return true = 本次清了槽（调用方需要刷新窗口）。
     */
    fun clearScreenHideIfLeft(currentPackage: String, locked: Boolean): Boolean {
        val hiddenFor = screenHidePackage ?: return false
        if (!locked && currentPackage == hiddenFor) return false
        screenHidePackage = null
        return true
    }

    /** 宿主销毁 / 进程重建：面板计数与临时隐藏全部清零，避免卡在永久隐藏。 */
    fun reset() {
        panelCountValue = 0
        screenHidePackage = null
    }
}
