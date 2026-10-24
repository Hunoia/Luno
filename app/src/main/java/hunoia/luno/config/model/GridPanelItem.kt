package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 网格面板里的一项：位置用 [x] / [y] / [spanX] / [spanY] 落在固定网格上。
 * 不用流式列表：跨格小部件在流式布局里无处安放，而跨格是摆桌面小部件的前提。
 */
@Serializable
@Keep
data class GridPanelItem(
    val id: String = UUID.randomUUID().toString(),
    /** 左上角列（从 0 起）。 */
    val x: Int = 0,
    /** 左上角行（从 0 起）。 */
    val y: Int = 0,
    /** 横向占格数。 */
    val spanX: Int = 1,
    /** 纵向占格数。 */
    val spanY: Int = 1,
    /** 所属页；默认 0，老的单页数据零改写。 */
    val pageIndex: Int = 0,
    val kind: Kind = Kind.Action(emptyList()),
) {
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
             * 该小部件的最小可用 span，加项时按 provider 的 `targetCellWidth/Height`
             * 算出并冻结。必须存：resize 下限判定不能跨进程现查，provider 卸载后
             * 会退化成 1×1；默认 1×1。
             */
            val minSpanX: Int = 1,
            val minSpanY: Int = 1,
        ) : Kind

        /**
         * Luno 内置小部件（Compose 自绘，不依赖 AppWidgetHost）：[widgetId] 见 `InternalWidgetIds`。
         * 不走 AppWidget：跨进程宿主、常驻、授权对自家部件是纯开销。
         */
        @Serializable
        @Keep
        data class Internal(
            val widgetId: String,
            /** 是否在自绘内容下垫一层底色（左上角按钮可切，默认关）。 */
            val showBackground: Boolean = false,
        ) : Kind
    }

    val right: Int get() = x + spanX
    val bottom: Int get() = y + spanY

    /**
     * 缩放下限：小部件项取 provider 下限，动作项最小 1×1。
     */
    val minSpanX: Int get() = (kind as? Kind.AppWidget)?.minSpanX?.coerceAtLeast(1) ?: 1
    val minSpanY: Int get() = (kind as? Kind.AppWidget)?.minSpanY?.coerceAtLeast(1) ?: 1

    /**
     * 两项是否矩形相交。[spanX] / [spanY] 在 [normalized] 后已 ≥1，这里不防 0：
     * 防了反而会把退化项悄悄挪走，不如让它显式重叠。
     */
    fun overlaps(other: GridPanelItem): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom

    /** 夹进 [columns] × [rows]：左/上越界平移回 0，右/下溢出收缩 span（至少 1 格）。 */
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
     * 设置页提示「哪些项被丢弃」用的人类可读名字，取不到返回 null（调用方决定兜底文案）。
     * 只有 typeId：真正的文案要 UI 层走 `actionText(...)` 注册表解析，这里给 typeId 而不编假名。
     */
    fun labelOrNull(): String? =
        (kind as? Kind.Action)?.items?.firstOrNull()?.typeId?.takeIf { it.isNotBlank() }
            ?: (kind as? Kind.AppWidget)?.provider?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
            ?: (kind as? Kind.Internal)?.widgetId?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
}

/** 首个动作。[AppWidget] 项没有变体概念（点击由 provider 自己处理）。 */
val GridPanelItem.action: StoredAction?
    get() = (kind as? GridPanelItem.Kind.Action)?.items?.firstOrNull()

