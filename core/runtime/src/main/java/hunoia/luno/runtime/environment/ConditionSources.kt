package hunoia.luno.runtime.environment

import android.util.Log
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import hunoia.luno.config.model.ConnectionDirection
import hunoia.luno.config.model.NetworkType

/** 自动化条件所需的系统电平状态：网络 / 耳机 / 蓝牙 / 飞行模式 */
class ConditionSources(
    private val tag: String = "ConditionSources",
    private val context: Context,
    private val onStateChanged: () -> Unit = {},
    /** 连接方向脉冲：只发真实翻转，启动时的首次读取不算（init 守卫）。 */
    private val onBluetoothPulse: (ConnectionDirection) -> Unit = {},
    private val onHeadphonesPulse: (ConnectionDirection) -> Unit = {},
) {
    var networkType: NetworkType = NetworkType.NONE
        private set

    /** 读不到/无定位权限/未连接时为 null；不要把 `<unknown ssid>` 当成真实网络名。 */
    var wifiSsid: String? = null
        private set

    /**
     * 是否需要读 SSID：**只有存在 WiFi 名称条件时**才为 true。
     *
     * 读 SSID 在 Android 8.1+ 需要定位权限，且每读一次系统都会点亮「正在使用位置信息」。
     * 没有规则用 SSID 就一次都不碰——这是"Luno 一直在用位置权限"的根因。
     */
    var wifiSsidNeeded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                readWifiSsid()
            } else {
                wifiSsid = null
            }
        }

    var headphonesConnected: Boolean = false
        private set

    var bluetoothAdapterOn: Boolean? = null
        private set

    var bluetoothAudioConnected: Boolean? = null
        private set

    var bluetoothDeviceName: String? = null
        private set

    var bluetoothDeviceAddress: String? = null
        private set

    var airplaneMode: Boolean = false
        private set

    private var started = false

    /** 权限刷新只同步状态，不把「刚拿到权限后读到的现状」补成一次连接事件。 */
    private var suppressPulses = false

    /** 首次读取完成前不发脉冲：避免启动瞬间把"本来就连着"当成一次连接事件。 */
    private var headphonesInitDone = false
    private var bluetoothAudioInitDone = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val airplaneObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = readAirplaneMode()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(localContext: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> bluetoothAdapterOn = true
                        BluetoothAdapter.STATE_OFF -> bluetoothAdapterOn = false
                        // 过渡态保持上一个确定值：TURNING_OFF 先清成 null 会把「已连接」
                        // 提前抹掉，等真正的 OFF 到达时就发不出断开边沿了。
                        BluetoothAdapter.STATE_TURNING_ON,
                        BluetoothAdapter.STATE_TURNING_OFF -> Unit
                    }
                    readBluetoothAudio()
                    onStateChanged()
                }

                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                AVRCP_CONNECTION_STATE_CHANGED -> readBluetoothAudio()

            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) = refreshHeadphones()
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) = refreshHeadphones()
    }

    companion object {
        private const val AVRCP_CONNECTION_STATE_CHANGED =
            "android.bluetooth.avrcp.profile.action.CONNECTION_STATE_CHANGED"

        private val HEADPHONE_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }

    fun start() {
        if (started) return
        started = true

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateNetworkType(toNetworkType(capabilities), notify = true)
            }

            override fun onAvailable(network: Network) {
                // 仅更新状态，不回调：网络切换瞬间 onCapabilitiesChanged 会紧接着带上正确能力到达。
                readActiveNetwork(notify = false)
            }

            override fun onLost(network: Network) {
                networkType = NetworkType.NONE
                wifiSsid = null
                onStateChanged()
            }
        }
        networkCallback = callback
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }

        // 立即读一次当前网络，不触发 onStateChanged：此刻 GestureCoordinator 尚未构造完成，
        // 同步回调会踩到未初始化的字段；网络已就绪，后续条件评估直接可见。
        readActiveNetwork(connectivityManager, notify = false)
        readWifiSsid()

        context.registerReceiver(
            broadcastReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(AVRCP_CONNECTION_STATE_CHANGED)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )

        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
            false,
            airplaneObserver,
        )
        readAirplaneMode()
        readBluetoothAdapter()
        readBluetoothAudio()
        refreshHeadphones()
        runCatching { audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler) }
    }

    /**
     * 权限授予/撤销后重读一次。这里不注册新监听，只把「权限变化前读不到」的值
     * 刷新成当前真实值；最后统一通知一次，让状态条件立刻重评。
     */
    fun refresh() {
        if (!started) return
        suppressPulses = true
        try {
            readActiveNetwork(notify = true)
            readWifiSsid()
            readBluetoothAdapter()
            readBluetoothAudio()
            refreshHeadphones()
            readAirplaneMode()
        } finally {
            suppressPulses = false
        }
        onStateChanged()
    }

    fun stop() {
        if (!started) return
        started = false
        headphonesInitDone = false
        bluetoothAudioInitDone = false
        networkCallback?.let { callback ->
            runCatching {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(callback)
            }
        }
        networkCallback = null
        runCatching { context.contentResolver.unregisterContentObserver(airplaneObserver) }
        runCatching { context.unregisterReceiver(broadcastReceiver) }
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
    }

    private fun refreshHeadphones() {
        val connected = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { device -> device.type in HEADPHONE_DEVICE_TYPES }
        }.onFailure {
            // 静默失败会让耳机状态永远不刷新（条件源失效），至少留痕。
            Log.w(tag, "refreshHeadphones failed", it)
        }.getOrNull() ?: return
        if (connected != headphonesConnected) {
            headphonesConnected = connected
            if (headphonesInitDone && !suppressPulses) {
                onHeadphonesPulse(
                    if (connected) ConnectionDirection.CONNECTED else ConnectionDirection.DISCONNECTED
                )
            }
            onStateChanged()
        }
        headphonesInitDone = true
    }

    private fun readBluetoothAdapter() {
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            )
        // 权限缺失或读取异常都降级为未知（null），不要把失败当成「适配器关闭」。
        bluetoothAdapterOn = if (granted) {
            runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled }.getOrNull()
        } else {
            null
        }
    }

    private fun readBluetoothAudio() {
        if (bluetoothAdapterOn != true) {
            val hadDevice = bluetoothDeviceName != null || bluetoothDeviceAddress != null
            val wasConnected = bluetoothAudioConnected
            // 适配器明确关闭 = 已断开；权限未知 = 连接状态也未知。两者都不能当成「已连接」。
            val nextConnected = if (bluetoothAdapterOn == false) false else null
            bluetoothDeviceName = null
            bluetoothDeviceAddress = null
            if (wasConnected != nextConnected || hadDevice) {
                bluetoothAudioConnected = nextConnected
                if (nextConnected == false && wasConnected == true && bluetoothAudioInitDone && !suppressPulses) {
                    // 适配器关闭会直接摘掉已连接设备；这条边沿必须发，否则 BLUETOOTH_CHANGED 永远等不到断开。
                    onBluetoothPulse(ConnectionDirection.DISCONNECTED)
                }
                onStateChanged()
            } else {
                bluetoothAudioConnected = nextConnected
            }
            bluetoothAudioInitDone = true
            return
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val devices = runCatching {
            manager.getConnectedDevices(BluetoothProfile.A2DP) +
                manager.getConnectedDevices(BluetoothProfile.HEADSET)
        }
        if (devices.isFailure) {
            // 权限撤销/系统服务异常：降级为未知，不能伪造一次断开脉冲。
            val hadState = bluetoothAudioConnected != null ||
                bluetoothDeviceName != null ||
                bluetoothDeviceAddress != null
            bluetoothAudioConnected = null
            bluetoothDeviceName = null
            bluetoothDeviceAddress = null
            if (hadState) onStateChanged()
            bluetoothAudioInitDone = true
            return
        }
        val device = devices.getOrDefault(emptyList()).firstOrNull()
        val connected = device != null
        val name = runCatching { device?.name }.getOrNull()
        val address = runCatching { device?.address }.getOrNull()
        val changed = connected != bluetoothAudioConnected ||
            name != bluetoothDeviceName ||
            address != bluetoothDeviceAddress
        bluetoothAudioConnected = connected
        bluetoothDeviceName = name
        bluetoothDeviceAddress = address
        if (changed) {
            if (bluetoothAudioInitDone && !suppressPulses) {
                onBluetoothPulse(
                    if (connected) ConnectionDirection.CONNECTED else ConnectionDirection.DISCONNECTED
                )
            }
            onStateChanged()
        }
        bluetoothAudioInitDone = true
    }

    private fun readAirplaneMode() {
        // 读失败（极少数 ROM 会抛 SecurityException）时保留旧值，不要把失败当成「关闭」。
        val raw = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        }.getOrNull() ?: return
        val enabled = raw != 0
        if (enabled != airplaneMode) {
            airplaneMode = enabled
            onStateChanged()
        }
    }

    private fun readActiveNetwork(connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
        notify: Boolean = true,
    ) {
        val capabilities = runCatching {
            connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        }.getOrNull() ?: return
        updateNetworkType(toNetworkType(capabilities), notify)
    }

    private fun updateNetworkType(type: NetworkType, notify: Boolean) {
        val previousSsid = wifiSsid
        val typeChanged = type != networkType
        networkType = type
        readWifiSsid()
        // 同一 WiFi 下换 SSID 也要刷新；类型没变时只在名字变化时通知。
        if (notify && (typeChanged || previousSsid != wifiSsid)) onStateChanged()
    }

    /**
     * `connectionInfo.ssid` 在 Android 8.1+ 需要定位权限；无权限/已关闭定位时返回
     * `<unknown ssid>`，这里统一降级为 null，不把占位符当成真实网络名。
     */
    private fun readWifiSsid() {
        // 没有 WiFi 名称条件就不读：这个调用会拉起定位权限使用提示。
        if (!wifiSsidNeeded) {
            wifiSsid = null
            return
        }
        if (networkType != NetworkType.WIFI) {
            wifiSsid = null
            return
        }
        val manager = runCatching {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        }.getOrNull() ?: run {
            wifiSsid = null
            return
        }
        val raw = runCatching { manager.connectionInfo?.ssid }.getOrNull()
        wifiSsid = raw
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    private fun toNetworkType(capabilities: NetworkCapabilities): NetworkType = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.OTHER
        else -> NetworkType.NONE
    }
}
