package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 网格面板里的一项。位置用 [x] / [y] / [spanX] / [spanY] 落在固定网格上
 * （一墨 GridItem 的同款模型）。
 *
 * 为什么不用「按内容顺序的流式列表」：跨格小部件在流式布局里无处安放，
 * 而跨格是摆桌面小部件的前提 —— AppWidget 的尺寸由 provider 自己声明，
 * 宿主只能按格数喂给它。
 *
 * **存量数据不迁移**：[GridPanel.items] 的流式面板直接作废（见 docs/grid-panel.md）。
 */
@Serializable
@Keep
data class GridPanelItem(
    val id: String = UUID.randomUUID().toString(),
    /** 左上角所在列（从 0 起）。 */
    val x: Int = 0,
    /** 左上角所在行（从 0 起）。 */
    val y: Int = 0,
    /** 横向占格数。 */
    val spanX: Int = 1,
    /** 纵向占格数。 */
    val spanY: Int = 1,
    /** 简版多页的所属页（见 [GridPanel.pageCount]）。默认 0 = 单页数据零改写。 */
    val pageIndex: Int = 0,
    val kind: Kind = Kind.Action(emptyList()),
) {
    /** 拖拽与落位判定的稳定身份。 */
    val cellKey: String get() = id

    @Serializable
    @Keep
    sealed interface Kind {
        /** 存库用：按 tag 分发，避开 sealed 的多态序列化开销。 */
        @Serializable
        @Keep
        data class Action(val items: List<StoredAction>) : Kind

        /** 第三方桌面小部件：[appWidgetId] 由 [AppWidgetHost] 分配，宿主被删会失效。 */
        @Serializable
        @Keep
        data class AppWidget(
            val appWidgetId: Int,
            val provider: String,
            /**
             * 这一个小部件的**最小可用span**，加项那一刻按 provider 声明的
             * `targetCellWidth/Height` 算出（见 `widgetSpanFor`）后冻结。
             *
             * 为什么必须存下来而不是 resize 时现查：缩到手柄时要拿它做下限，而
             * 现查要过 `AppWidgetManager`（跨进程 IPC），在手势回调里做会掉帧；
             * 更糟的是 provider 被卸载后查不到，就只能退化成 1×1 —— 于是用户能把
             * 小部件缩到 provider 根本画不出来的尺寸，得到一个空白格子。
             * 冻结值让下限判定不再依赖系统服务的实时状态。
             *
             * 默认 1×1：老数据（以及非小部件项）读出来是 1，行为与改动前一致。
             */
            val minSpanX: Int = 1,
            val minSpanY: Int = 1,
        ) : Kind
    }

    val right: Int get() = x + spanX
    val bottom: Int get() = y + spanY

    /**
     * 缩放手柄能减到的最小 span。小部件项有下限（provider 声明的尺寸），
     * 动作项没有 —— 1×1 就是最小。
     */
    val minSpanX: Int get() = (kind as? Kind.AppWidget)?.minSpanX?.coerceAtLeast(1) ?: 1
    val minSpanY: Int get() = (kind as? Kind.AppWidget)?.minSpanY?.coerceAtLeast(1) ?: 1

    /**
     * 两项是否矩形相交。[spanX] / [spanY] 在 [normalized] 里已被夹到 ≥1，
     * 这里不再防 0 —— 防了反而会把「退化项」悄悄挪走，不如让它显式重叠。
     */
    fun overlaps(other: GridPanelItem): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom

    /**
     * 夹进 [columns] × [rows] 的网格边界内：整体左/上越界就平移回 0，
     * 右侧/底部溢出则收缩 span（收缩到 0 视为 1，保证每项至少一格）。
     */
    fun normalized(columns: Int, rows: Int): GridPanelItem {
        val cols = columns.coerceAtLeast(1)
        val maxRows = rows.coerceAtLeast(1)
        val nx = x.coerceIn(0, cols - 1)
        val ny = y.coerceIn(0, maxRows - 1)
        return copy(
            x = nx,
            y = ny,
            spanX = spanX.coerceIn(1, cols - nx),
            spanY = spanY.coerceIn(1, maxRows - ny),
        )
    }

    /**
     * 给「设置页要告诉用户哪些项被丢弃了」用的人类可读名字。取不到返回 null
     * （调用方自己决定用什么兜底文案），别在这里编一个假名。
     *
     * [StoredAction] 只有 `typeId`，没有现成的显示名 —— 真正的文案要走
     * `actionText(...)` 那套注册表解析，而那需要组合上下文，属于 UI 层职责。
     * 这里给 typeId 是诚实的（宁可显示内部标识，也不编一个看起来像真的名字）。
     */
    fun labelOrNull(): String? =
        (kind as? Kind.Action)?.items?.firstOrNull()?.typeId?.takeIf { it.isNotBlank() }
            ?: (kind as? Kind.AppWidget)?.provider?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
}

/** 网格里放了什么。[AppWidget] 项没有变体概念（点击由 provider 自己处理）。 */
val GridPanelItem.action: StoredAction?
    get() = (kind as? GridPanelItem.Kind.Action)?.items?.firstOrNull()

