package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.domain.ActionLibraryEntry

/**
 * 动作展示需要的配置快照（动作库条目、具名面板）。
 *
 * 归 `:feature:actions`：这些是「动作怎么显示」的知识；`:app` 在组装时用
 * `CompositionLocalProvider` 注入同一份配置快照，主进程与 `:service` 运行时都提供。
 */

val LocalActionLibraryEntries = staticCompositionLocalOf<List<ActionLibraryEntry>> { emptyList() }

/** 全局动作面板列表。用于把 `internal.actionPanel` 显示成具名面板而非类型名。 */
val LocalActionPanels = staticCompositionLocalOf<List<ActionPanel>> { emptyList() }

/** 全局网格面板列表。用于把 `internal.gridPanel` 显示成具名面板而非类型名。 */
val LocalGridPanels = staticCompositionLocalOf<List<GridPanel>> { emptyList() }

/** 全局子手势列表。用于把 `internal.subGesture` 显示成具名子手势而非类型名。 */
val LocalSubGestures = staticCompositionLocalOf<List<SubGesture>> { emptyList() }

/**
 * 面板引用（`internal.gridPanel` 的 panelId）的功能图标解析器：入参 panelId
 * （网格 UUID 或 `single:widgetId`），返回可渲染图标（ImageVector / TextIcon），未知返回 null。
 *
 * `:feature:actions` 不依赖 `:feature:gridpanel`，无法直接拿部件功能图标 / `iconFor`；
 * 默认无解析器，由 app/gridpanel 层注入 `resolvePanelRefIcon`（见 `PanelDockCatalog`）。
 */
val LocalPanelRefIcon = staticCompositionLocalOf<(String) -> Any?> { { null } }

/**
 * 页面级导航控制器。`:app` 与 `:service` 的页面宿主各自提供；
 * 默认缺省抛错，避免静默拿空控制器。
 */
val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }
