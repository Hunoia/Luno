package hunoia.luno.runtime.scene

import android.view.accessibility.AccessibilityEvent

/**
 * 情景识别的一次判定结果。[SceneScanDecision.Accepted] 之外的值都可直接丢弃事件，
 * 保持枚举是为了在调试日志里说明「为什么没扫」，而不是把每次丢弃都当成异常。
 */
internal enum class SceneScanDecision {
    Accepted,
    Disabled,
    NotSceneEvent,
    Locked,
    ScreenOff,
    NoPackage,
    OwnApp,
    SystemUi,
    InputMethod,
    Launcher,
}

/**
 * 一次事件的可判定上下文（事件自带的包名走 [SceneScanGate.decide] 的参数，不进这里）。
 */
data class SceneScanContext(
    val enabled: Boolean,
    val locked: Boolean,
    val interactive: Boolean,
    val launcher: Boolean,
)

/**
 * 情景识别的事件门禁：只让「用户此刻正看的一个应用界面」触发扫描。
 *
 * 与识屏取词（architecture §7.4）边界一致：只读无障碍文本、只做前台窗口的单次事件驱动读取，
 * 不做定时轮询、不扫后台窗口、不自动 OCR。
 */
internal object SceneScanGate {

    /** 通知栏/锁屏/音量条等系统 UI 的内容变化不是「用户在看的界面」。 */
    private val SYSTEM_UI_PACKAGES = setOf(
        "com.android.systemui",
        "com.miui.systemui",
    )

    fun decide(
        eventType: Int,
        context: SceneScanContext,
        eventPackage: String?,
        ownPackage: String,
    ): SceneScanDecision {
        if (!context.enabled) return SceneScanDecision.Disabled
        if (!isSceneEventType(eventType)) return SceneScanDecision.NotSceneEvent
        if (context.locked) return SceneScanDecision.Locked
        if (!context.interactive) return SceneScanDecision.ScreenOff
        if (eventPackage.isNullOrBlank()) return SceneScanDecision.NoPackage
        if (eventPackage == ownPackage) return SceneScanDecision.OwnApp
        if (eventPackage in SYSTEM_UI_PACKAGES) return SceneScanDecision.SystemUi
        // 输入法窗口即使被标成 application，也不该触发读屏。
        if (eventPackage.contains("inputmethod", ignoreCase = true)) return SceneScanDecision.InputMethod
        if (context.launcher) return SceneScanDecision.Launcher
        return SceneScanDecision.Accepted
    }

    /**
     * 界面内容变化 + 窗口状态变化 + 文本变化。桌面/系统栏的窗口变化事件不进来，
     * 避免「只是开了通知栏」就付一次树遍历。
     */
    private fun isSceneEventType(eventType: Int): Boolean = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        -> true

        else -> false
    }
}
