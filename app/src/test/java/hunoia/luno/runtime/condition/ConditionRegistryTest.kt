package hunoia.luno.runtime.condition

import hunoia.luno.config.model.ConditionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 条件扩展点注册表：所有内置类型都必须注册，且脉冲/订阅声明可用。 */
class ConditionRegistryTest {

    @Test
    fun `every builtin condition type is registered`() {
        val builtin = setOf(
            ConditionType.FOREGROUND_APP,
            ConditionType.SCREEN_STATE,
            ConditionType.BATTERY_RANGE,
            ConditionType.CHARGING,
            ConditionType.TIME_RANGE,
            ConditionType.VOLUME_RANGE,
            ConditionType.RINGER_SILENT,
            ConditionType.NETWORK_TYPE,
            ConditionType.HEADPHONES,
            ConditionType.BLUETOOTH,
            ConditionType.AIRPLANE_MODE,
            ConditionType.APP_CHANGED_TO,
            ConditionType.SCREEN_EVENT,
            ConditionType.CHARGING_CHANGED,
            ConditionType.VOLUME_CHANGED,
            ConditionType.NOTIFICATION_POSTED,
            ConditionType.NOTIFICATION_REMOVED,
            ConditionType.NOTIFICATION_EXISTS,
            ConditionType.BLUETOOTH_CHANGED,
            ConditionType.HEADPHONES_CHANGED,
            ConditionType.SCHEDULED_TIME,
            ConditionType.GESTURE_PERFORMED,
            ConditionType.KEYBOARD_EVENT,
            ConditionType.MUSIC_PLAYING,
            ConditionType.MUSIC_CHANGED,
            ConditionType.TORCH_ON,
            ConditionType.TORCH_CHANGED,
            ConditionType.DND_ON,
            ConditionType.DND_CHANGED,
            ConditionType.MANUAL,
            ConditionType.VAR_COMPARE,
            ConditionType.APP_PACKAGE_CHANGED,
            ConditionType.INTERVAL,
            ConditionType.SHARE_RECEIVED,
            ConditionType.CLIPBOARD_CHANGED,
            ConditionType.WIFI_SSID,
        )
        // GROUP 由求值器递归处理，不注册成叶子。
        assertEquals(builtin, ConditionRegistry.registeredTypes - ConditionType.GROUP)
    }

    @Test
    fun `pulse and subscription declarations come from condition implementations`() {
        assertTrue(ConditionRegistry.isPulse(ConditionType.NOTIFICATION_POSTED))
        assertFalse(ConditionRegistry.isPulse(ConditionType.NOTIFICATION_EXISTS))
        assertEquals(setOf("notification"), ConditionRegistry.subscriptionKeys(ConditionType.NOTIFICATION_EXISTS))
        assertEquals(emptySet<String>(), ConditionRegistry.subscriptionKeys(ConditionType.WIFI_SSID))
    }
}
