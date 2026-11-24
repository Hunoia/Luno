package hunoia.luno.floatball.settings.alert

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertFamily
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLibraryExport
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.EdgeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 素材导出 / 合并导入：id 冲突重建、重名避让、调色板引用跟着改。 */
class AlertLibraryBackupTest {

    private fun preset(id: String, name: String, paletteId: String? = null): AlertPreset =
        AlertPreset(
            id = id,
            name = name,
            paletteId = paletteId,
            layers = listOf(
                AlertLayer(
                    id = "$id-l0",
                    family = AlertFamily.Edge,
                    color = AlertColorRef(paletteId = paletteId),
                    edge = EdgeParams(),
                ),
            ),
        )

    @Test
    fun `export carries every preset`() {
        val library = AlertLibrary(
            presets = listOf(
                preset("user_a", "我的"),
            ),
        )
        val export = library.toExport(now = 1L)
        assertEquals(1, export.presets.size)
        assertEquals("user_a", export.presets.single().id)
        assertEquals(1L, export.exportedAt)
        assertTrue(export.palettes.isEmpty())
    }

    @Test
    fun `merge keeps existing ids and avoids duplicate names`() {
        val existing = AlertLibrary(
            palettes = listOf(AlertPalette(id = "p1", name = "夜色", colors = listOf(1))),
            presets = listOf(preset("user_a", "我的")),
        )
        val export = AlertLibraryExport(
            palettes = listOf(
                AlertPalette(id = "p1", name = "夜色", colors = listOf(2)),
                AlertPalette(id = "p2", name = "新色", colors = listOf(3)),
            ),
            presets = listOf(
                preset("user_a", "我的", paletteId = "p1"),
                preset("user_b", "另一个", paletteId = "p2"),
            ),
        )
        val merged = existing.mergeImport(export)!!
        // 原调色板不动；撞 id 的导入调色板换成新 id，名字加后缀。
        assertEquals("p1", merged.palettes[0].id)
        assertEquals(listOf(1), merged.palettes[0].colors)
        assertEquals(3, merged.palettes.size)
        assertEquals("夜色 (2)", merged.palettes[1].name)
        assertNotEquals("p1", merged.palettes[1].id)

        // 预设全部按用户预设重建 id；撞名加后缀；调色板引用改到新 id。
        assertEquals(3, merged.presets.size)
        assertEquals(3, merged.presets.map { it.id }.toSet().size)
        val remapped = merged.presets.first { it.name == "我的 (2)" }
        assertEquals(merged.palettes[1].id, remapped.paletteId)
        assertEquals(merged.palettes[1].id, remapped.layers.single().color.paletteId)
        val other = merged.presets.first { it.name == "另一个" }
        assertEquals("新色", merged.palettes.first { it.id == other.paletteId }.name)
    }

    @Test
    fun `higher schema version is rejected`() {
        val merged = AlertLibrary().mergeImport(
            AlertLibraryExport(schemaVersion = 99, presets = listOf(preset("u", "n"))),
        )
        assertNull(merged)
    }

    @Test
    fun `empty import is a no-op`() {
        val library = AlertLibrary(presets = listOf(preset("u", "n")))
        assertEquals(library, library.mergeImport(AlertLibraryExport()))
    }
}
