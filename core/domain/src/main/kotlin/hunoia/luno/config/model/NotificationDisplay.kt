package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 通知呈现模式（通用通知设置，全局默认 + 按 [NotificationKind] 覆盖）。
 *
 * - [STANDARD]：普通通知，全设备可用。
 * - [LIVE]：Android 16+ promotedOngoing（Live Update / 状态栏胶囊）；
 *   HyperOS 4 上即系统渲染的岛。权限或系统不支持时回退 [STANDARD]。
 * - [ISLAND]：小米超级岛，`miui.focus.param`（FocusTemplate V3）+ XMSF 鉴权绕过。
 *   探测或发布失败回退 [LIVE]（满足 Android 16+ promoted 条件时）否则 [STANDARD]。
 *   非 Xiaomi 设备可选但运行时回退。
 */
@Keep
enum class NotificationMode {
    STANDARD,
    LIVE,
    ISLAND,
    ;

    companion object {
        val DEFAULT = STANDARD
    }
}

/** 受通用通知设置管理的通知类型（封闭枚举；加类型时改模型，不做旧配置兼容）。 */
@Keep
enum class NotificationKind { HABIT, BACKUP, TODO }

/**
 * 通用通知显示设置（D11：不做旧配置兼容；`habitReminderStyle` 直接替换为本模型）。
 */
@Serializable
@Keep
data class NotificationDisplaySettings(
    val mode: NotificationMode = NotificationMode.DEFAULT,
    /** 按 [NotificationKind] 覆盖全局 [mode]；缺省类型跟随全局。 */
    val overrides: Map<NotificationKind, NotificationMode> = emptyMap(),
    /** 岛是否首发自动展开大岛（`islandFirstFloat` / `enableFloat`）。 */
    val islandAutoExpand: Boolean = false,
    /** 岛超时秒数；0 = 不设超时（跟随通知 ongoing 语义）。 */
    val islandTimeoutSeconds: Int = 0,
    /**
     * 发布焦点通知时切断 XMSF 网络的时长（毫秒）：让 SystemUI 的鉴权失败放行。
     * 太短会在鉴权回包前就恢复 → 岛被撤回；太长只是白等（断网期间只影响 XMSF）。
     */
    val islandDisconnectMs: Int = DEFAULT_ISLAND_DISCONNECT_MS,
    /** 首帧展开后大岛停留秒数（`param_island.expandedTime`）；0 或未展开 = 不展开。 */
    val islandExpandSeconds: Int = DEFAULT_ISLAND_EXPAND_SECONDS,
    /** 岛卡片外发光（`param_v2.outEffectSrc = outer_glow`）。 */
    val islandOuterGlow: Boolean = false,
    /** ISLAND 模式下同时保留通知栏条目（`isShowNotification`）；关掉就只有岛。 */
    val islandKeepShadeNotification: Boolean = true,
    /** 长按岛卡片可拖拽分享（`param_island.shareData`）。 */
    val islandDragShare: Boolean = false,
    /**
     * 进度类通知（点了会更新进度的，如习惯「+1」）点击后自动移除整条通知的延时（秒）。
     * 0 = 不移除（可继续点）。到点前再点一次会重置计时；达标立即移除。
     */
    val progressDismissSeconds: Int = DEFAULT_PROGRESS_DISMISS_SECONDS,
) {
    /** [kind] 的最终生效模式：覆盖优先，否则全局。 */
    fun modeFor(kind: NotificationKind): NotificationMode = overrides[kind] ?: mode

    companion object {
        /** 与 LinkGo / InstallerX 默认一致：展开 5 秒后收回胶囊。 */
        const val DEFAULT_ISLAND_EXPAND_SECONDS = 5

        /** 断网时长默认 100ms（LinkGo 的 BLIND_WINDOW_MS）。 */
        const val DEFAULT_ISLAND_DISCONNECT_MS = 100

        /** 断网时长可选档位（毫秒）。 */
        val ISLAND_DISCONNECT_MS_OPTIONS = listOf(100, 150, 200, 300)

        /** 大岛停留可选档位（秒）；关展开走 [islandAutoExpand]，这里只给「展开多久」。 */
        val ISLAND_EXPAND_SECONDS_OPTIONS = listOf(3, 5, 10)

        /** 进度类通知点击后的自动移除延时默认 30 秒：够看清进度、也够补点。 */
        const val DEFAULT_PROGRESS_DISMISS_SECONDS = 30

        /** 自动移除延时可选档位（秒）；0 = 不移除。 */
        val PROGRESS_DISMISS_SECONDS_OPTIONS = listOf(0, 10, 30, 60, 120)
    }
}
