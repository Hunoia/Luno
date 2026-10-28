package hunoia.luno.config.model

object GridPanelDefaults {
    const val ColumnCount = 4
    const val MinColumnCount = 2
    const val MaxColumnCount = 6
    const val CardHeightFraction = 0.62f
    const val MinCardHeightFraction = 0.30f
    const val MaxCardHeightFraction = 0.90f

    const val CardWidthFraction = 0.72f
    const val MinCardWidthFraction = 0.5f
    /** 横屏下限更小：屏宽大，0.5 已占近半屏。 */
    const val MinCardWidthFractionLandscape = 0.35f
    const val MaxCardWidthFraction = 0.9f
    const val CardAlpha = 0.75f
    const val MinCardAlpha = 0.3f
    const val MaxCardAlpha = 1f

    const val ScreenMarginDp = 12f
    const val MinScreenMarginDp = 0f
    const val MaxScreenMarginDp = 48f
}
