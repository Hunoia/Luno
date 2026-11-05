package hunoia.luno.domain.annotate

import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.ToolStyle
import hunoia.luno.annotate.model.ToolbarAnchor
import hunoia.luno.annotate.store.AnnotatePreferences
import hunoia.luno.config.model.AnnotateSettings
import hunoia.luno.config.model.AnnotateToolStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标注偏好映射：跨版本容错（未知工具键忽略）、默认值不丢、round-trip 稳定。
 */
class AnnotateSettingsMapperTest {

    @Test
    fun `styles map known tool names and ignore unknown keys`() {
        val settings = AnnotateSettings(
            styles = mapOf(
                ToolKind.PEN.name to AnnotateToolStyle(colorArgb = 0xFF00FF00, strokeWidth = 9f),
                "SOME_FUTURE_TOOL" to AnnotateToolStyle(),
            ),
        )

        val preferences = AnnotateSettingsMapper.toPreferences(settings)

        assertEquals(setOf(ToolKind.PEN), preferences.styles.keys)
        assertEquals(0xFF00FF00, preferences.styles.getValue(ToolKind.PEN).colorArgb)
        assertEquals(9f, preferences.styles.getValue(ToolKind.PEN).strokeWidth)
    }

    @Test
    fun `every known tool and the anchor survive a round trip`() {
        val styles = ToolKind.entries.associateWith { tool ->
            ToolStyle(
                colorArgb = 0xFF000000 or tool.ordinal.toLong(),
                strokeWidth = tool.ordinal + 1f,
                textSize = tool.ordinal + 10f,
                mosaicCell = tool.ordinal + 20f,
            )
        }
        val anchor = ToolbarAnchor(0.25f, 0.75f)

        val encoded = AnnotateSettingsMapper.toSettings(AnnotatePreferences(styles, anchor))
        val decoded = AnnotateSettingsMapper.toPreferences(encoded)

        assertEquals(styles, decoded.styles)
        assertEquals(anchor, decoded.anchor)
    }

    @Test
    fun `anchor needs both fractions`() {
        assertNull(AnnotateSettingsMapper.toPreferences(AnnotateSettings()).anchor)
        assertNull(
            AnnotateSettingsMapper.toPreferences(AnnotateSettings(toolbarXFraction = 0.5f)).anchor,
        )
        assertNull(
            AnnotateSettingsMapper.toPreferences(AnnotateSettings(toolbarYFraction = 0.5f)).anchor,
        )
        assertEquals(
            ToolbarAnchor(0.5f, 0.25f),
            AnnotateSettingsMapper.toPreferences(
                AnnotateSettings(toolbarXFraction = 0.5f, toolbarYFraction = 0.25f),
            ).anchor,
        )
    }

    @Test
    fun `settings without preferences are empty`() {
        assertTrue(AnnotateSettings().isEmpty())
        assertTrue(!AnnotateSettings(toolbarXFraction = 0.5f).isEmpty())
        assertTrue(!AnnotateSettings(toolbarYFraction = 0.5f).isEmpty())
        assertTrue(!AnnotateSettings(styles = mapOf("PEN" to AnnotateToolStyle())).isEmpty())
    }
}
