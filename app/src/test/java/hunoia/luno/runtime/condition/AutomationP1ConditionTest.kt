package hunoia.luno.runtime.condition

import android.app.NotificationManager
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.NotificationMatchMode
import hunoia.luno.core.domain.PackageChangeAction
import hunoia.luno.config.model.RingerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationP1ConditionTest {

    private fun ctx(
        notificationPosted: NotificationPulse? = null,
        notificationRemoved: NotificationPulse? = null,
        activeNotifications: List<NotificationPulse> = emptyList(),
        packageChanged: PackageChangePulse? = null,
        shareReceived: SharePulse? = null,
        clipboardChanged: ClipboardPulse? = null,
        intervalDueKeys: Set<String> = emptySet(),
        wifiSsid: String? = null,
        networkType: NetworkType = NetworkType.WIFI,
        bluetoothAudioConnected: Boolean? = false,
        bluetoothDeviceName: String? = null,
        bluetoothDeviceAddress: String? = null,
    ) = ConditionContext(
        nowMs = 0L,
        packageName = "com.test",
        isLockScreen = false,
        isLauncher = false,
        isLandscape = false,
        isKeyboardInput = false,
        isCharging = false,
        batteryLevel = 80,
        minuteOfDay = 0,
        dayOfWeek = 1,
        networkType = networkType,
        wifiSsid = wifiSsid,
        headphonesConnected = false,
        bluetoothAdapterOn = true,
        bluetoothAudioConnected = bluetoothAudioConnected,
        bluetoothDeviceName = bluetoothDeviceName,
        bluetoothDeviceAddress = bluetoothDeviceAddress,
        airplaneMode = false,
        ringerMode = RingerMode.NORMAL,
        volumePercent = emptyMap(),
        isMusicPlaying = false,
        isTorchOn = false,
        isDndOn = false,
        appChangedTo = null,
        screenEvent = null,
        chargingChangedTo = null,
        volumeChanged = null,
        notificationPosted = notificationPosted,
        notificationRemoved = notificationRemoved,
        activeNotifications = activeNotifications,
        packageChanged = packageChanged,
        shareReceived = shareReceived,
        clipboardChanged = clipboardChanged,
        intervalDueKeys = intervalDueKeys,
    )

    private fun pulse(
        packageName: String = "com.a",
        title: String = "Title",
        text: String = "Body",
        ongoing: Boolean = false,
        clearable: Boolean = true,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    ) = NotificationPulse(
        packageName = packageName,
        title = title,
        text = text,
        ongoing = ongoing,
        clearable = clearable,
        importance = importance,
    )

    @Test
    fun `notification title and body filters are separate`() {
        val item = ConditionItem(
            type = ConditionType.NOTIFICATION_POSTED,
            notifyTitleKeyword = "hello",
            notifyTextKeyword = "world",
        )
        assertTrue(item.matches(ctx(notificationPosted = pulse(title = "Hello", text = "World"))))
        assertFalse(item.matches(ctx(notificationPosted = pulse(title = "Hello", text = "Other"))))
        assertFalse(item.matches(ctx(notificationPosted = pulse(title = "Other", text = "World"))))
    }

    @Test
    fun `notification glob and regex matching`() {
        val glob = ConditionItem(
            type = ConditionType.NOTIFICATION_POSTED,
            notifyTitleKeyword = "he*o",
            notifyMatchMode = NotificationMatchMode.GLOB,
        )
        assertTrue(glob.matches(ctx(notificationPosted = pulse(title = "hello"))))
        assertFalse(glob.matches(ctx(notificationPosted = pulse(title = "world"))))

        val regex = ConditionItem(
            type = ConditionType.NOTIFICATION_POSTED,
            notifyTitleKeyword = "^h.llo$",
            notifyMatchMode = NotificationMatchMode.REGEX,
        )
        assertTrue(regex.matches(ctx(notificationPosted = pulse(title = "hello"))))
        assertFalse(regex.matches(ctx(notificationPosted = pulse(title = "hello!"))))
    }

    @Test
    fun `notification exclude keyword and flags`() {
        val item = ConditionItem(
            type = ConditionType.NOTIFICATION_POSTED,
            notifyExcludeKeyword = "spam",
            notifyOngoing = false,
            notifyImportanceAtLeast = NotificationManager.IMPORTANCE_DEFAULT,
        )
        assertTrue(item.matches(ctx(notificationPosted = pulse(text = "normal"))))
        assertFalse(item.matches(ctx(notificationPosted = pulse(text = "spam"))))
        assertFalse(item.matches(ctx(notificationPosted = pulse(ongoing = true))))
        assertFalse(item.matches(ctx(notificationPosted = pulse(importance = NotificationManager.IMPORTANCE_LOW))))
    }

    @Test
    fun `notification exists uses active snapshot and can be negated`() {
        val exists = ConditionItem(type = ConditionType.NOTIFICATION_EXISTS, notifyExists = true)
        val absent = ConditionItem(type = ConditionType.NOTIFICATION_EXISTS, notifyExists = false)
        val active = listOf(pulse())
        assertTrue(exists.matches(ctx(activeNotifications = active)))
        assertFalse(absent.matches(ctx(activeNotifications = active)))
        assertFalse(exists.matches(ctx(activeNotifications = emptyList())))
        assertTrue(absent.matches(ctx(activeNotifications = emptyList())))
    }

    @Test
    fun `package change action and package filter`() {
        val item = ConditionItem(
            type = ConditionType.APP_PACKAGE_CHANGED,
            packageNames = listOf("com.a"),
            packageChangeAction = PackageChangeAction.ADDED,
        )
        assertTrue(item.matches(ctx(packageChanged = PackageChangePulse(PackageChangeAction.ADDED, "com.a"))))
        assertFalse(item.matches(ctx(packageChanged = PackageChangePulse(PackageChangeAction.REMOVED, "com.a"))))
        assertFalse(item.matches(ctx(packageChanged = PackageChangePulse(PackageChangeAction.ADDED, "com.b"))))
    }

    @Test
    fun `interval requires rule id and matching due key`() {
        val item = ConditionItem(type = ConditionType.INTERVAL, intervalMinutes = 5)
        val due = intervalDueKey("rule-1", item)!!
        assertTrue(item.matches(ctx(intervalDueKeys = setOf(due)), "rule-1"))
        assertFalse(item.matches(ctx(intervalDueKeys = setOf(due)), "rule-2"))
        assertFalse(item.matches(ctx(intervalDueKeys = emptySet()), "rule-1"))
    }

    @Test
    fun `share and clipboard triggers expose text`() {
        assertTrue(
            ConditionItem(type = ConditionType.SHARE_RECEIVED)
                .matches(ctx(shareReceived = SharePulse("look at https://example.com", "https://example.com"))),
        )
        assertTrue(
            ConditionItem(type = ConditionType.CLIPBOARD_CHANGED, clipboardKeyword = "hello")
                .matches(ctx(clipboardChanged = ClipboardPulse("hello world"))),
        )
        assertFalse(
            ConditionItem(type = ConditionType.CLIPBOARD_CHANGED, clipboardKeyword = "hello")
                .matches(ctx(clipboardChanged = ClipboardPulse("world"))),
        )
    }

    @Test
    fun `wifi ssid and bluetooth device identity degrade safely`() {
        val wifiAny = ConditionItem(type = ConditionType.WIFI_SSID)
        val wifiHome = ConditionItem(type = ConditionType.WIFI_SSID, wifiSsid = "Home")
        assertTrue(wifiAny.matches(ctx(wifiSsid = "Home")))
        assertTrue(wifiHome.matches(ctx(wifiSsid = "home")))
        assertFalse(wifiHome.matches(ctx(wifiSsid = null)))
        assertFalse(wifiHome.matches(ctx(networkType = NetworkType.MOBILE, wifiSsid = "Home")))

        val btDevice = ConditionItem(
            type = ConditionType.BLUETOOTH,
            bluetoothDeviceName = "Buds",
            bluetoothDeviceAddress = "AA:BB",
        )
        assertTrue(
            btDevice.matches(
                ctx(
                    bluetoothAudioConnected = true,
                    bluetoothDeviceName = "Buds",
                    bluetoothDeviceAddress = "aa:bb",
                ),
            ),
        )
        assertFalse(
            btDevice.matches(
                ctx(
                    bluetoothAudioConnected = true,
                    bluetoothDeviceName = "Other",
                    bluetoothDeviceAddress = "aa:bb",
                ),
            ),
        )
        assertFalse(btDevice.matches(ctx(bluetoothAudioConnected = false)))
    }
}
