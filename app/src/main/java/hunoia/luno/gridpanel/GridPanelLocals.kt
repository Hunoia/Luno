package hunoia.luno.gridpanel

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 网格面板内「打开外部应用/页面」后请求关闭面板。
 *
 * 自绘小部件（最近应用、媒体控制）自己处理点击，不经过 ActionItemCell 的
 * `execute → closeWithAnim()` 链路；不在这里补一次关闭，启动的应用会被
 * 全屏面板盖住，看起来像「点了没反应」。
 */
val LocalGridPanelClose = staticCompositionLocalOf<() -> Unit> { {} }
