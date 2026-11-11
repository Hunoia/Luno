package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** 动效档位序列化：默认全标准，未知 / 缺失键不炸，备份导出导入保留档位。 */
class MotionSettingsTest {

    @Test
    fun `默认全部标准`() {
        val settings = MotionTuningSettings()
        assertEquals(MotionSpeed.Standard, settings.panel)
        assertEquals(MotionSpeed.Standard, settings.overlay)
        assertEquals(MotionSpeed.Standard, settings.floating)
    }

    @Test
    fun `三档独立往返`() {
        val settings = MotionTuningSettings(
            panel = MotionSpeed.Slowest,
            overlay = MotionSpeed.Faster,
            floating = MotionSpeed.Slower,
        )
        val decoded = JsonSerializer.decodeFromString<MotionTuningSettings>(
            JsonSerializer.encodeToString(settings),
        )
        assertEquals(settings, decoded)
    }

    @Test
    fun `旧备份缺键回落标准`() {
        val decoded = JsonSerializer.decodeFromString<MotionTuningSettings>("{}")
        assertEquals(MotionTuningSettings(), decoded)
    }

    @Test
    fun `高级设置整体往返保留动效档位`() {
        val settings = AdvancedSettings(
            motion = MotionTuningSettings(panel = MotionSpeed.Slower),
        )
        val decoded = JsonSerializer.decodeFromString<AdvancedSettings>(
            JsonSerializer.encodeToString(settings),
        )
        assertEquals(settings.motion, decoded.motion)
    }
}
