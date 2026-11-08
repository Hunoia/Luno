package hunoia.luno.config.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 通用通知设置：按类型覆盖优先，缺省跟随全局（`NotificationKind` 加类型时回归这里）。 */
class NotificationDisplaySettingsTest {

    @Test
    fun `缺省全局标准且无覆盖`() {
        val settings = NotificationDisplaySettings()
        assertEquals(NotificationMode.STANDARD, settings.mode)
        assertEquals(NotificationMode.STANDARD, settings.modeFor(NotificationKind.HABIT))
        assertEquals(NotificationMode.STANDARD, settings.modeFor(NotificationKind.BACKUP))
    }

    @Test
    fun `覆盖优先于全局`() {
        val settings = NotificationDisplaySettings(
            mode = NotificationMode.LIVE,
            overrides = mapOf(NotificationKind.BACKUP to NotificationMode.ISLAND),
        )
        assertEquals(NotificationMode.LIVE, settings.modeFor(NotificationKind.HABIT))
        assertEquals(NotificationMode.ISLAND, settings.modeFor(NotificationKind.BACKUP))
    }

    @Test
    fun `每个类型都能独立取到模式`() {
        for (kind in NotificationKind.entries) {
            val settings = NotificationDisplaySettings(
                overrides = mapOf(kind to NotificationMode.ISLAND),
            )
            assertEquals(NotificationMode.ISLAND, settings.modeFor(kind))
        }
    }

    @Test
    fun `岛默认值：不展开、停留 5 秒、保留通知栏、不带外发光与拖拽`() {
        val settings = NotificationDisplaySettings()
        assertEquals(false, settings.islandAutoExpand)
        assertEquals(NotificationDisplaySettings.DEFAULT_ISLAND_EXPAND_SECONDS, settings.islandExpandSeconds)
        assertEquals(true, settings.islandKeepShadeNotification)
        assertEquals(false, settings.islandOuterGlow)
        assertEquals(false, settings.islandDragShare)
        assertEquals(0, settings.islandTimeoutSeconds)
    }

    @Test
    fun `停留档位都落在 clamp 区间内`() {
        // 面板只提供 3 / 5 / 10，默认值必须在档位里，否则列表选不中。
        val options = NotificationDisplaySettings.ISLAND_EXPAND_SECONDS_OPTIONS
        assert(options.contains(NotificationDisplaySettings.DEFAULT_ISLAND_EXPAND_SECONDS))
        assert(options.all { it in 1..60 })
    }
}
