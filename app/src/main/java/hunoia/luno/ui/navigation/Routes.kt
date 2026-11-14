package hunoia.luno.ui.navigation

import androidx.annotation.Keep
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

/** 通知：通用呈现模式（标准 / 实时 / 超级岛）、岛细节与权限状态。 */
@Keep
@Serializable
data object NotificationSettings

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

/** 网格面板属性编辑（名称/列数/宽高比）；项在面板内编辑。 */
@Keep
@Serializable
data class GridPanelEdit(
    val panelId: String,
)

/** 单功能面板设置：widgetId 固定，决定是哪块功能面板。 */
@Keep
@Serializable
data class SinglePanelEdit(
    val widgetId: String,
)

/** 习惯编辑；habitId 空 = 新增。 */
@Keep
@Serializable
data class HabitEdit(
    val habitId: String = "",
)

/** 趋势详情；[openRecord] = 进页即弹快速记录（部件主体 / 提醒通知）。 */
@Keep
@Serializable
data class TrendDetail(
    val metricId: String = "",
    val openRecord: Boolean = false,
)

/** 趋势指标编辑；metricId 空 = 新增。 */
@Keep
@Serializable
data class TrendEdit(
    val metricId: String = "",
)

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

/** 指针设置页（起始位置 / 移动手感 / 光标外观）。 */
@Keep
@Serializable
data object PointerSettings

/** 外观设置页：列表风格与面板 / 浮层 / 浮窗动效档位。 */
@Keep
@Serializable
data object AppearanceSettings

/** 指针位置调整页：[follow] 为 true 编辑跟随偏移，否则编辑初始落点。 */
@Keep
@Serializable
data class PointerAdjust(val follow: Boolean)

@Keep
@Serializable
data object FloatBallEdit

/** 通知胶囊独立设置页（与悬浮球/建议胶囊并列）。 */
@Keep
@Serializable
data object FloatBallNotificationCapsule

/** 建议胶囊独立设置页（规则表入口）。 */
@Keep
@Serializable
data object FloatBallSuggestionCapsule

/** 建议规则编辑页；ruleId 为空 = 新增。 */
@Keep
@Serializable
data class SuggestionRuleEdit(
    val ruleId: String = "",
)

/** 运行日志：开关、最近日志预览、清空与导出。 */
@Keep
@Serializable
data object LogSettings

/** 定时自动备份：开关、备份目录、执行状态、立即备份与从自动备份恢复。 */
@Keep
@Serializable
data object AutoBackupSettings

/** 应用冻结管理页：冻结 / 解冻、一键名单与批量操作。 */
@Keep
@Serializable
data object AppControl

/** 剪贴板管理页：历史 / 固定 / 批量 / 导入导出 / 记录设置。 */
@Keep
@Serializable
data object Clipboard

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
