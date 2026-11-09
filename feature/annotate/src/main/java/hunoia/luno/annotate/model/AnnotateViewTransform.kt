package hunoia.luno.annotate.model

/**
 * 画布视图变换（双指缩放/平移）。
 *
 * 它不是文档内容：不进撤销、不持久化、不参与导出。从 [AnnotateState] 拆出来，
 * 让捏合只更新这一个 state（以及 `graphicsLayer` 的变换），不触发内容绘制失效。
 */
data class AnnotateViewTransform(
    val zoom: Float = 1f,
    val pan: Pt = Pt(0f, 0f),
) {
    fun sanitized(): AnnotateViewTransform = copy(
        zoom = zoom.coerceIn(AnnotateOps.MIN_VIEW_ZOOM, AnnotateOps.MAX_VIEW_ZOOM),
    )

    fun reset(): AnnotateViewTransform = AnnotateViewTransform()

    /** 适应按钮循环：安全区域适配 ↔ 全屏适配。 */
    fun toggled(safeZoom: Float): AnnotateViewTransform {
        val safe = safeZoom.coerceIn(AnnotateOps.MIN_VIEW_ZOOM, 1f)
        val target = if (zoom <= safe + 0.001f) 1f else safe
        return AnnotateViewTransform(target, Pt(0f, 0f))
    }

    /** 叠加到 contain 基准上，得到命中测试/文字编辑器使用的视图坐标。 */
    fun applyTo(base: ContainFit): ContainFit = base.withViewTransform(zoom, pan)
}
