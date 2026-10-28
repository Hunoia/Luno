package hunoia.luno.action.template

import android.content.Context
import hunoia.luno.bridge.system.readClipboardText
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.RingerMode
import hunoia.luno.core.domain.RuntimeSnapshot
import hunoia.luno.core.domain.formatTimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 模板变量取值上下文：把 IO / Android 查询包成显式能力，函数本身只做纯变换。
 */
class VariableContext(
    val snapshot: RuntimeSnapshot,
    private val appContext: Context,
) {
    suspend fun appLabel(packageName: String): String? {
        if (packageName.isBlank()) return null
        AppLabelCache.get(packageName)?.let { return it }
        val label = withContext(Dispatchers.IO) {
            runCatching {
                val pm = appContext.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } ?: return null
        AppLabelCache.put(packageName, label)
        return label
    }

    /** 剪贴板文本：后台无焦点时系统返回 null（Android 10+ 限制），此时给空串。 */
    suspend fun clipboard(): String = readClipboardText(appContext).orEmpty()

    fun today(): String {
        val calendar = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private object AppLabelCache {
        @Volatile
        private var last: Pair<String, String>? = null

        fun get(packageName: String): String? = last?.takeIf { it.first == packageName }?.second

        fun put(packageName: String, label: String) {
            last = packageName to label
        }
    }
}

/** 一个模板变量 token 的取值函数。只读 [VariableContext]，不修改调度状态。 */
fun interface VariableFunction {
    suspend fun resolve(context: VariableContext): String?
}

/**
 * 模板变量注册表：新增变量只注册一条 [VariableFunction]，不再修改中心 `when(token)`。
 * 重复 token 在启动期直接失败。
 */
object VariableFunctionRegistry {

    private val functions = LinkedHashMap<String, VariableFunction>()

    init {
        registerBuiltins()
    }

    fun register(token: String, function: VariableFunction) {
        require(token.isNotBlank()) { "variable token must not be blank" }
        require(functions.put(token, function) == null) { "duplicate variable token: $token" }
    }

    /** 未知 token 返回 null，由 ParamTemplates 原样保留。 */
    suspend fun resolve(token: String, context: VariableContext): String? =
        functions[token]?.resolve(context)

    fun contains(token: String): Boolean = functions.containsKey(token)

    val tokens: Set<String> get() = functions.keys.toSet()

    private fun registerBuiltins() {
        with(RuntimeVariables) {
            register(PKG) { it.snapshot.packageName }
            register(PKG_LABEL) { ctx -> ctx.appLabel(ctx.snapshot.packageName) ?: ctx.snapshot.packageName }
            register(TIME) { formatTimeOfDay(it.snapshot.minuteOfDay) }
            register(DATE) { it.today() }
            register(HOUR) { "%02d".format(it.snapshot.minuteOfDay / 60) }
            register(MINUTE) { "%02d".format(it.snapshot.minuteOfDay % 60) }
            register(TIMESTAMP) { System.currentTimeMillis().toString() }
            register(BATTERY) { it.snapshot.batteryLevel.takeIf { level -> level >= 0 }?.toString().orEmpty() }
            register(CHARGING) { it.snapshot.isCharging.toString() }
            register(NETWORK) { it.snapshot.networkType.name }
            register(SILENT) { (it.snapshot.ringerMode == RingerMode.SILENT).toString() }
            register(HEADPHONES) { it.snapshot.headphonesConnected.toString() }
            register(BLUETOOTH) {
                (it.snapshot.bluetoothAudioConnected ?: it.snapshot.bluetoothAdapterOn).toString()
            }
            register(AIRPLANE) { it.snapshot.airplaneMode.toString() }
            register(LANDSCAPE) { it.snapshot.isLandscape.toString() }
            register(LOCKSCREEN) { it.snapshot.isLockScreen.toString() }
            register(LAUNCHER) { it.snapshot.isLauncher.toString() }
            register(KEYBOARD) { it.snapshot.isKeyboardInput.toString() }
            register(TRIGGER_APP) { it.snapshot.appChangedTo.orEmpty() }
            register(TRIGGER_GESTURE_ID) { it.snapshot.gesturePerformedId.orEmpty() }
            register(TRIGGER_GESTURE_NAME) {
                (it.snapshot.gesturePerformedName ?: it.snapshot.gesturePerformedId).orEmpty()
            }
            register(TRIGGER_RULE_NAME) { it.snapshot.triggerRuleName.orEmpty() }
            register(TRIGGER_SCREEN) { it.snapshot.screenEvent?.name.orEmpty() }
            register(TRIGGER_CHARGING) { it.snapshot.chargingChangedTo?.toString().orEmpty() }
            register(TRIGGER_VOLUME) {
                it.snapshot.volumeChanged?.let { change ->
                    "${change.stream.name}_${change.direction.name}"
                }.orEmpty()
            }
            register(TRIGGER_NOTIFICATION_PACKAGE) {
                (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)?.packageName.orEmpty()
            }
            register(TRIGGER_NOTIFICATION_TITLE) {
                (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)?.title.orEmpty()
            }
            register(TRIGGER_NOTIFICATION_TEXT) {
                (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)?.text.orEmpty()
            }
            register(TRIGGER_NOTIFICATION_APP) {
                val packageName = (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)
                    ?.packageName
                if (packageName == null) "" else it.appLabel(packageName).orEmpty()
            }
            register(TRIGGER_NOTIFICATION_CHANNEL) {
                (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)?.channelId.orEmpty()
            }
            register(TRIGGER_NOTIFICATION_GROUP) {
                (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)?.groupKey.orEmpty()
            }
            register(TRIGGER_NOTIFICATION_CATEGORY) {
                (it.snapshot.notificationPosted ?: it.snapshot.notificationRemoved)?.category.orEmpty()
            }
            register(TRIGGER_PACKAGE_NAME) { it.snapshot.packageChanged?.packageName.orEmpty() }
            register(TRIGGER_PACKAGE_ACTION) { it.snapshot.packageChanged?.action.orEmpty() }
            register(TRIGGER_SHARE_TEXT) { it.snapshot.shareReceived?.text.orEmpty() }
            register(TRIGGER_SHARE_URL) { it.snapshot.shareReceived?.url.orEmpty() }
            register(TRIGGER_CLIPBOARD_TEXT) { it.snapshot.clipboardChanged?.text.orEmpty() }
            register(WIFI_SSID) { it.snapshot.wifiSsid.orEmpty() }
            register(BLUETOOTH_DEVICE_NAME) { it.snapshot.bluetoothDeviceName.orEmpty() }
            register(BLUETOOTH_DEVICE_ADDRESS) { it.snapshot.bluetoothDeviceAddress.orEmpty() }
            register(CLIPBOARD) { it.clipboard() }
            AudioStream.entries.forEach { stream ->
                register(volumeToken(stream)) { ctx ->
                    ctx.snapshot.volumePercent[stream]?.toString().orEmpty()
                }
            }
        }
    }
}
