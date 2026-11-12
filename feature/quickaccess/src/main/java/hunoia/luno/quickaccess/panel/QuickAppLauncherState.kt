package hunoia.luno.quickaccess.panel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.ui.theme.panelTeardownDelayMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 独立快速启动面板的窗口会话态：可见性 / 关闭动画编排。
 * 内容态（应用列表 / 查询 / 键盘 / 拉起）在 [content]，单功能面板直接复用后者。
 */
class QuickAppLauncherState(
    context: Context,
    coroutineScope: CoroutineScope,
    initialSettings: QuickAppLauncherSettings,
    requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit,
    onCloseAnimatedRaw: () -> Unit,
    onLaunch: (AppInfo, Boolean) -> Boolean,
    onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
) {
    val content = QuickLaunchContentState(
        context = context,
        coroutineScope = coroutineScope,
        initialSettings = initialSettings,
        requestEnableDisabledPackage = requestEnableDisabledPackage,
        onLaunch = onLaunch,
        onLaunched = { closeAnimated() },
    )

    var panelVisible by mutableStateOf(false)
    var closing by mutableStateOf(false)

    val closeAnimated: () -> Unit = {
        if (!closing) {
            closing = true
            panelVisible = false
            coroutineScope.launch {
                // 拆窗必须等退场播完：由面板家族时长推导，不写死（慢档会截断）。
                delay(panelTeardownDelayMillis())
                onCloseAnimatedRaw()
            }
        }
    }

    init {
        onRegisterCloseAnimated?.invoke(closeAnimated)
    }

    fun dispose() = content.dispose()

    /**
     * 可见性由 overlay / 组合侧驱动，而不是 content 里的 `LaunchedEffect`：
     * - 首次打开：`QuickAppLauncherRoot` 在**首帧之后**才置 true，保证 `AnimatedVisibility`
     *   先以 false 组合一帧、进场动画能播；
     * - 重开（窗口已挂、组合未销毁）：`show()` 直接置 true。
     * 关闭时置 false 会触发退场，拆窗由 `closeAnimated` 的定时编排负责。
     */
    fun setPanelShown(visible: Boolean) {
        if (panelVisible == visible) return
        panelVisible = visible
        // 必须在这里放开 closing 闩锁。closeAnimated 是 `if (!closing)` 守卫的，
        // 而 closing 置 true 之后没有任何地方复位 —— state 常驻前每次开面板都重建，
        // 所以看不出来；常驻之后第一次关闭就把闩锁焊死了，之后永远关不掉。
        if (visible) closing = false
    }
}
