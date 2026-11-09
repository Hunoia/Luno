package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板定位与渲染必须共用同一份生效比例：旧配置里的高度可低到 0.25，
 * 定位若另按 0.35 计算，面板实际更矮就会在底部留空。
 */
class QuickLauncherGeometryTest {

    @Test
    fun `stored low height fraction is honored by both position and render`() {
        val geometry = QuickLauncherGeometry(contentHeightFraction = 0.25f)
        assertEquals(0.25f, geometry.effectiveContentHeightFraction(), 0.0001f)
    }

    @Test
    fun `height fraction is clamped to the shared range`() {
        assertEquals(
            QuickLauncherGeometry.MinContentHeightFraction,
            QuickLauncherGeometry(contentHeightFraction = 0.1f).effectiveContentHeightFraction(),
            0.0001f,
        )
        assertEquals(
            QuickLauncherGeometry.MaxContentHeightFraction,
            QuickLauncherGeometry(contentHeightFraction = 1.2f).effectiveContentHeightFraction(),
            0.0001f,
        )
    }

    @Test
    fun `panel width fraction is clamped to the shared range`() {
        assertEquals(
            QuickLauncherGeometry.MinPanelWidthFraction,
            QuickLauncherGeometry(panelWidthFraction = 0.5f).effectivePanelWidthFraction(),
            0.0001f,
        )
        assertEquals(1f, QuickLauncherGeometry(panelWidthFraction = 1.5f).effectivePanelWidthFraction(), 0.0001f)
    }
}
