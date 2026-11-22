package hunoia.luno.runtime.button

import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gesture.BuildConfig
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.ButtonVisibilitySettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.config.model.VisibilityTargetMode
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.RuntimeStateHub
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hunoia.luno.core.LOG_TAG
import java.util.concurrent.ConcurrentHashMap

/**
 * 触钮临时隐藏（`internal.hideTriggerButton` / `hideGestureButton`）。
 *
 * 只存在于内存：无持久化、进程重启即失效。读写都在主线程（执行口 `Dispatchers.Main.immediate`、
 * 刷新链在 `MainScope`），用并发容器只是为了不让“以后多一条写入方”直接变成数据竞争。
 */
class ButtonHideRuntime(
    private val scope: CoroutineScope,
    private val onStateChanged: () -> Unit,
) {
    private val hiddenButtons = ConcurrentHashMap<String, Long>()

    /** 批量临时隐藏：到期自动恢复；期间重复 hide 会刷新到期时间。 */
    fun hideTemporarily(buttonIds: Collection<String>, delayMs: Long) {
        if (buttonIds.isEmpty()) return
        val deadline = SystemClock.uptimeMillis() + delayMs
        buttonIds.forEach { hiddenButtons[it] = deadline }
        onStateChanged()
        scope.launch {
            delay(delayMs)
            val expired = buttonIds.filter { (hiddenButtons[it] ?: 0L) <= SystemClock.uptimeMillis() }
            if (expired.isNotEmpty()) {
                expired.forEach(hiddenButtons::remove)
                onStateChanged()
            }
        }
    }

    fun getSnapshot(): Map<String, Long> = hiddenButtons.toMap()
}

class ButtonRefreshCoordinator(
    private val runtimeSettingsStore: SettingsStore,
    private val buttonWindowController: ButtonWindowController,
    /** 场景触发回调（进入/退出边缘的每个动作一次）；由宿主接统一执行口。 */
    private val onVisibilityTrigger: (profile: VisibilityProfile, enter: Boolean, action: StoredAction) -> Unit =
        { _, _, _ -> },
) {
    private val triggerRuntime = VisibilityTriggerRuntime(onFire = onVisibilityTrigger)

    fun refresh(runtimeState: GestureRuntimeState) {
        RuntimeStateHub.update(runtimeState)
        val settings = runtimeSettingsStore.snapshot()
        val policy = ButtonVisibilityPolicy(
            initialSettings = settings.initialSettings,
            runtimeState = runtimeState,
        )
        buttonWindowController.updateVisibility(policy)
        triggerRuntime.onRefresh(
            gestureEnabled = settings.initialSettings.gestureEnabled,
            profiles = settings.initialSettings.buttonVisibility.profiles,
            state = runtimeState,
        )
        if (BuildConfig.DEBUG) {
            val visibleCount = buttonWindowController.buttonViews.count { view ->
                val target = view.tag as? hunoia.luno.runtime.overlay.GestureButtonWindowTarget ?: return@count false
                policy.shouldShow(target.sourceButton)
            }
            Log.i(LOG_TAG, "button refresh: visible=$visibleCount total=${buttonWindowController.buttonViews.size}")
        }
    }
}

/**
 * 显隐场景的边缘检测：场景匹配集合的差分 → 进入 / 退出触发（architecture §5.3）。
 *
 * - 显隐是「持续态」，触发是「边缘事件」：只在命中集合发生变化的那次刷新产生触发。
 * - 首次刷新、profiles 配置变化、总开关关闭都只重新基线，不触发（无边缘）；
 *   进程重启同理——边缘是内存态，重启后从当前状态重新基线。
 * - 冷却：per-profile（[VisibilityProfile.cooldownSeconds]，进入/退出共用一个窗口，
 *   窗口内边缘被丢弃）；另有全局地板 [GLOBAL_TRIGGER_MIN_INTERVAL_MS]——地板未过时
 *   本次边缘**推迟**（保留上次基线，下次刷新重算），只降速、不消灭配置成环。
 * - 与显隐的「最后命中生效」无关：每条场景独立检测自己的边缘。
 */
class VisibilityTriggerRuntime(
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
    private val onFire: (profile: VisibilityProfile, enter: Boolean, action: StoredAction) -> Unit,
) {
    private var lastProfiles: List<VisibilityProfile>? = null
    private var lastMatched: Set<String>? = null
    /** 上次实际触发批次的时间；-1 = 从未触发过（不能拿 Long.MIN_VALUE 做哨兵：减法溢出）。 */
    private var lastFireBatchMs = -1L
    private val lastFireMs = mutableMapOf<String, Long>()

    /** 每次触钮刷新调用一次（与显隐求值同源，不新增事件）。 */
    fun onRefresh(gestureEnabled: Boolean, profiles: List<VisibilityProfile>, state: GestureRuntimeState) {
        val matched = if (gestureEnabled) {
            profiles.filter { it.enabled && it.matches(state) }.map { it.id }.toSet()
        } else {
            emptySet()
        }
        val configChanged = profiles != lastProfiles
        lastProfiles = profiles
        if (!gestureEnabled || configChanged || lastMatched == null) {
            // 重新基线：只记录当前命中集合（总开关关闭时记 null，重开后的首刷重新基线），
            // 不触发。基线必须记下**本次**命中集合，否则下一次刷新会把真实差分错当基线。
            lastMatched = if (gestureEnabled) matched else null
            return
        }
        val previous = lastMatched ?: return
        val now = nowMs()
        if (lastFireBatchMs >= 0 &&
            now - lastFireBatchMs < GLOBAL_TRIGGER_MIN_INTERVAL_MS &&
            matched != previous
        ) {
            // 全局地板未过：不推进基线，边缘保留到下一次刷新（地板过期后按最新状态重算）。
            return
        }
        lastMatched = matched
        var fired = false
        fun tryFire(id: String, enter: Boolean) {
            val profile = profiles.firstOrNull { it.id == id } ?: return
            val actions = if (enter) profile.enterActions else profile.exitActions
            if (actions.isEmpty()) return
            val cooldownMs = profile.cooldownSeconds.coerceAtLeast(0) * 1000L
            val lastFire = lastFireMs[profile.id]
            if (lastFire != null && now - lastFire < cooldownMs) return
            lastFireMs[profile.id] = now
            fired = true
            actions.forEach { onFire(profile, enter, it) }
        }
        (matched - previous).forEach { tryFire(it, enter = true) }
        (previous - matched).forEach { tryFire(it, enter = false) }
        if (fired) lastFireBatchMs = now
    }

    fun reset() {
        lastProfiles = null
        lastMatched = null
        lastFireMs.clear()
        lastFireBatchMs = -1L
    }

    private companion object {
        /**
         * 任意两次触发批次的最小间隔。作用是给「桌面→开 X / 在 X→回桌面」这类互踢配置降速：
         * 环不会消失（用户配置自查），但从每秒十次压到一秒一次，足以被察觉并纠正。
         */
        const val GLOBAL_TRIGGER_MIN_INTERVAL_MS = 1_000L
    }
}

class ButtonVisibilityPolicy(
    private val initialSettings: InitialSettings,
    private val runtimeState: GestureRuntimeState,
) {
    fun shouldShow(button: GestureButton): Boolean {
        if (!initialSettings.gestureEnabled) return false
        if ((runtimeState.hiddenGestureButtons[button.id] ?: 0L) > runtimeState.nowMs) return false
        // 用户基线：场景配置只能相对它隐藏 / 恢复，不能把手动关掉的钮重新打开。
        if (!button.enabled) return false
        return evaluateProfiles(button.id, initialSettings.buttonVisibility.profiles, runtimeState)
    }
}

/** 场景求值：最后命中的带显隐效果的一条生效（visible=null 的纯动作场景不参与）；target 不覆盖本对象的配置不改状态。 */
private fun evaluateProfiles(
    targetId: String,
    profiles: List<VisibilityProfile>,
    state: GestureRuntimeState,
): Boolean {
    var visible = true
    profiles.forEach { profile ->
        if (profile.enabled && profile.matches(state) && profile.affects(targetId)) {
            profile.visible?.let { visible = it }
        }
    }
    return visible
}

private fun VisibilityProfile.matches(state: GestureRuntimeState): Boolean {
    if (packageNames.isNotEmpty() && state.currentPackageName !in packageNames) return false
    if (screens.isNotEmpty() && screens.none { it.matches(state) }) return false
    return true
}

private fun VisibilityScreen.matches(state: GestureRuntimeState): Boolean = when (this) {
    VisibilityScreen.LOCK_SCREEN -> state.isNowInLockScreenPage
    VisibilityScreen.LAUNCHER -> state.isInLauncher
    VisibilityScreen.LANDSCAPE -> state.isLandscape
    VisibilityScreen.PORTRAIT -> !state.isLandscape
    VisibilityScreen.KEYBOARD_INPUT -> state.isKeyboardInputActive
    VisibilityScreen.SCREEN_OFF -> state.isScreenOff
}

private fun VisibilityProfile.affects(targetId: String): Boolean = when (mode) {
    VisibilityTargetMode.ALL -> true
    VisibilityTargetMode.ONLY -> targetId in targetIds
    VisibilityTargetMode.EXCEPT -> targetId !in targetIds
}

/**
 * Surface 是否被场景允许显示。null = 运行态尚未就绪，按允许处理（不阻塞原有门控）。
 *
 * 总开关、临时隐藏、面板占用与功能开关仍由各 Surface 宿主判断；这里只做场景匹配。
 */
fun shouldShowSurface(
    targetId: String,
    initialSettings: InitialSettings,
    runtimeState: GestureRuntimeState?,
): Boolean = shouldShowSurface(targetId, initialSettings.buttonVisibility, runtimeState)

/** 只吃可见性配置的窄入口：调用方不必为了一个字段构造整个 [InitialSettings]。 */
fun shouldShowSurface(
    targetId: String,
    visibility: ButtonVisibilitySettings,
    runtimeState: GestureRuntimeState?,
): Boolean {
    val state = runtimeState ?: return true
    return evaluateProfiles(targetId, visibility.profiles, state)
}
