package hunoia.luno.floatball.settings.alert

import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.runtime.floatball.alert.engine.AlertPlanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 删光层的预设仍是可编辑草稿：编辑查找必须找得到，选择器 / 播放才过滤。 */
class AlertPresetLookupTest {

    private val library = AlertLibrary(
        presets = listOf(AlertPreset(id = "empty", name = "空草稿")),
    )

    @Test
    fun `empty preset stays editable`() {
        assertEquals("empty", library.presetById("empty")?.id)
        assertTrue(library.presetById("empty")?.isPlayable() == false)
        assertNull(library.presetById("missing"))
    }

    @Test
    fun `picker and playback still drop empty presets`() {
        assertTrue(library.pickerPresets().none { it.id == "empty" })
        assertTrue(!AlertPlanFactory.presets(library).containsKey("empty"))
    }
}
