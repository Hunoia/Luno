package hunoia.luno.capability.notify

import android.app.Notification
import android.os.Build

/**
 * 通知栏的进度呈现：Android 16（API 36）原生 `Notification.ProgressStyle`。
 *
 * 与岛上无关：真机实测小米的进度组件（`param_v2.progressInfo` / `multiProgressInfo`、
 * 小岛 `combinePicInfo` 环）对第三方包一律不渲染（场景白名单），岛上只能显示文字 `x/y`。
 * 通知栏卡片是 AOSP 的样式，因此进度条只在这里能真正画出来。
 *
 * `ProgressStyle` 是纯系统组件（真进度条 + 节点），不再用字符拼条；API < 36 / target ≤ 1
 * 时原样返回，调用方继续用普通卡片。
 */
object NotificationProgress {

    /** 进度百分比（0..100）：四舍五入，保证 1/3 不会一直在 33 与 34 之间跳。 */
    fun percent(count: Int, target: Int): Int {
        val safeTarget = target.coerceAtLeast(1)
        return ((count.coerceIn(0, safeTarget) * 100f) / safeTarget).toInt().coerceIn(0, 100)
    }

    /**
     * 给通知套上进度样式：进度条 + 每个目标节点（最多 [MAX_POINTS] 个，避免目标 20 时挤成一团）。
     * 颜色统一取强调色；不传则由系统默认。
     */
    fun apply(
        builder: Notification.Builder,
        count: Int,
        target: Int,
        color: Int?,
    ): Notification.Builder {
        if (Build.VERSION.SDK_INT < 36 || target <= 1) return builder
        val percent = percent(count, target)
        val style = Notification.ProgressStyle()
            .setProgress(percent)
            .setProgressIndeterminate(false)
            .setStyledByProgress(false)
        val points = target.coerceAtMost(MAX_POINTS)
        for (step in 1 until points) {
            val position = (step * 100) / points
            val point = Notification.ProgressStyle.Point(position)
            color?.let { point.setColor(it) }
            style.addProgressPoint(point)
        }
        color?.let { style.setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(it))) }
        return builder.setStyle(style)
    }

    /** 节点上限：超过这个数量的目标用进度比例表达，不再画点。 */
    private const val MAX_POINTS = 10
}
