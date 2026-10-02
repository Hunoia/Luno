package hunoia.luno.runtime.environment

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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import hunoia.luno.config.model.NetworkType

/** 自动化条件所需的系统电平状态：网络 / 耳机 / 蓝牙 / 飞行模式 */
class ConditionSources(
    private val context: Context,
    private val onStateChanged: () -> Unit = {},
) {
    var networkType: NetworkType = NetworkType.NONE
        private set

    var headphonesConnected: Boolean = false
        private set

    var bluetoothAdapterOn: Boolean? = null
        private set

    var bluetoothAudioConnected: Boolean? = null
        private set

    var airplaneMode: Boolean = false
        private set

    private var started = false

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
                    bluetoothAdapterOn = when (
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    ) {
                        BluetoothAdapter.STATE_ON -> true
                        BluetoothAdapter.STATE_OFF -> false
                        BluetoothAdapter.STATE_TURNING_ON,
                        BluetoothAdapter.STATE_TURNING_OFF -> null
                        else -> bluetoothAdapterOn
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
                readActiveNetwork()
            }

            override fun onLost(network: Network) {
                networkType = NetworkType.NONE
                onStateChanged()
            }
        }
        networkCallback = callback
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }

        // 立即读一次当前网络，不触发 onStateChanged：此刻 GestureCoordinator 尚未构造完成，
        // 同步回调会踩到未初始化的字段；网络已就绪，后续条件评估直接可见。
        readActiveNetwork(connectivityManager)

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

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(networkCallback!!)
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
        }.getOrNull() ?: return
        if (connected != headphonesConnected) {
            headphonesConnected = connected
            onStateChanged()
        }
    }

    private fun readBluetoothAdapter() {
        runCatching {
            val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                )
            bluetoothAdapterOn = if (granted) BluetoothAdapter.getDefaultAdapter().isEnabled else null
        }
    }

    private fun readBluetoothAudio() {
        if (bluetoothAdapterOn != true) {
            if (bluetoothAudioConnected != false) {
                bluetoothAudioConnected = false
                onStateChanged()
            }
            return
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val connected = runCatching {
            manager.getConnectedDevices(BluetoothProfile.A2DP).isNotEmpty() ||
                manager.getConnectedDevices(BluetoothProfile.HEADSET).isNotEmpty()
        }.getOrNull() ?: false
        if (connected != bluetoothAudioConnected) {
            bluetoothAudioConnected = connected
            onStateChanged()
        }
    }

    private fun readAirplaneMode() {
        val enabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        }.getOrNull() != null
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
        if (type == networkType) return
        networkType = type
        if (notify) onStateChanged()
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
