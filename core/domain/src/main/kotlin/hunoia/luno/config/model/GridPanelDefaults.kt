package hunoia.luno.config.model

object GridPanelDefaults {
    const val ColumnCount = 4
    const val MinColumnCount = 2
    const val MaxColumnCount = 6
    const val CardHeightFraction = 0.62f
    const val MinCardHeightFraction = 0.20f
    const val MaxCardHeightFraction = 0.90f

    const val CardWidthFraction = 0.72f
    const val MinCardWidthFraction = 0.20f
    const val MaxCardWidthFraction = 0.90f

    /**
     * 横屏默认（竖屏用上面两档）：横屏屏宽是长边、屏高是短边，照搬竖屏比例会撑出
     * 巨宽扁卡（0.72 × 长边），格子被放大到只剩 1–2 行。宽按长边收紧、高按短边放宽。
     */
    const val LandscapeCardWidthFraction = 0.30f
    const val LandscapeCardHeightFraction = 0.80f

    const val CardAlpha = 0.75f
    const val MinCardAlpha = 0f
    const val MaxCardAlpha = 1f

    /** 面板与内置部件背景模糊：默认开启，0 表示关闭。 */
    const val BlurRadiusDp = 48f
    const val MinBlurRadiusDp = 0f
    const val MaxBlurRadiusDp = 80f
}
