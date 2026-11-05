package hunoia.luno.ui.navigation

import androidx.annotation.Keep
import hunoia.luno.config.model.GestureTriggerType
import kotlinx.serialization.Serializable



@Keep
@Serializable
data object Home

/** 屏幕识别设置页：全屏长按识屏与幽灵卡参数。 */
@Keep
@Serializable
data object ScreenPickSettings

@Keep
@Serializable
data object RecentAppsBlocklist

/** 链接分发设置（规则列表 + 胶囊/未命中行为）。 */
@Keep
@Serializable
data object LinkDispatchRules

/** 链接分发规则编辑页；ruleId 空 = 新增，[pattern] 非空 = 预填匹配串（识屏卡片「新建规则」）。 */
@Keep
@Serializable
data class LinkDispatchRuleEdit(
    val ruleId: String = "",
    val pattern: String = "",
)

/** 授权总览：每项一行的状态 + 跳系统设置 + 单项授权，顶部一键补齐。 */
@Keep
@Serializable
data object PrivilegedPermissions

/** 无障碍管理：列出设备上全部已安装无障碍服务并就地启停（含本服务状态卡）。 */
@Keep
@Serializable
data object AccessibilityManager

/** 固定通知隐藏：Luno 常驻通知与通知栏里其他应用的常驻通知。 */
@Keep
@Serializable
data object PersistentNotificationSettings

/** 状态栏图标隐藏：读写 `Settings.Secure.icon_blacklist`。 */
@Keep
@Serializable
data object StatusBarIconSettings

@Keep
@Serializable
data object NotificationBlocklist

@Keep
@Serializable
data object ActionPanelList

@Keep
@Serializable
data class ActionPanelEdit(
    val panelId: String,
)

@Keep
@Serializable
data object GridPanelList

/** 网格面板属性编辑（名称/列数/宽高比）；项在面板内编辑。 */
@Keep
@Serializable
data class GridPanelEdit(
    val panelId: String,
)

/** 习惯管理列表（主设置 → 习惯）。 */
@Keep
@Serializable
data object HabitList

/** 习惯编辑；habitId 空 = 新增。 */
@Keep
@Serializable
data class HabitEdit(
    val habitId: String = "",
)

/** 代办管理列表（主设置 → 代办）。 */
@Keep
@Serializable
data object TodoList

@Keep
@Serializable
data object SearchEngineSettings

/** 引擎编辑页（引擎设置页的下级页）；engineId 空 = 新增。 */
@Keep
@Serializable
data class SearchEngineEdit(
    val engineId: String = "",
)

/** 搜索内容源与来源标识设置（引擎设置页的子页）。 */
@Keep
@Serializable
data object SearchSourceSettings

/**
 * 小窗设置列表：跟随触发位置与固定位置入口。
 */
@Keep
@Serializable
data object MiniWindowSettings

/**
 * 小窗位置/大小调整页。独立页面而非全屏 overlay：纯配置任务，不需看见真实屏幕，
 * 页内画示意图即可（见 ui/settings/action/MiniWindowAdjustScreen.kt）。
 */
@Keep
@Serializable
data object MiniWindowAdjust

@Keep
@Serializable
data object FloatBallEdit

/** 运行日志：开关、最近日志预览、清空与导出。 */
@Keep
@Serializable
data object LogSettings

/** 触钮显隐场景列表。 */
@Keep
@Serializable
data object ButtonVisibility

/** 触钮显隐场景编辑页；[profileId] = `new` 表示新建。 */
@Keep
@Serializable
data class ButtonVisibilityEdit(
    val profileId: String,
)

@Keep
@Serializable
data class ActionLibraryEdit(
    val entryId: String,
    val typeId: String? = null,
)


const val ACTION_LIBRARY_ENTRY_ID = hunoia.luno.actions.library.ACTION_LIBRARY_NEW_ENTRY_ID
