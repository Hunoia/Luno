package hunoia.luno.runtime.condition

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * 手电筒状态：CameraManager torch 回调（注册即回报当前状态，首读不脉冲）。
 * 只跟第一个摄像头；系统快捷设置/其它 App 的开关也能观测到。
 * 个别 ROM 不支持回调时静默降级（状态恒为关，不崩）。
 */
class TorchStateTracker(
    private val context: Context,
    private val onChanged: (Boolean) -> Unit = {},
) {
    var isOn: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var initialized = false
    private var cameraId: String? = null

    private fun manager(): CameraManager? =
        runCatching { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }.getOrNull()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) apply(enabled)
        }
    }

    fun sync(needed: Boolean) {
        if (needed == registered) return
        if (needed) {
            val cameraManager = manager() ?: return
            val id = runCatching { cameraManager.cameraIdList.firstOrNull() }.getOrNull() ?: return
            initialized = false
            cameraId = id
            val ok = runCatching { cameraManager.registerTorchCallback(callback, handler) }.isSuccess
            if (!ok) {
                cameraId = null
                return
            }
            registered = true
        } else {
            manager()?.let { cameraManager ->
                runCatching { cameraManager.unregisterTorchCallback(callback) }
            }
            registered = false
            cameraId = null
            initialized = false
        }
    }

    fun unregister() {
        if (!registered) return
        registered = false
        manager()?.let { cameraManager ->
            runCatching { cameraManager.unregisterTorchCallback(callback) }
        }
    }

    private fun apply(enabled: Boolean) {
        if (!initialized) {
            initialized = true
            isOn = enabled
            return
        }
        if (enabled == isOn) return
        isOn = enabled
        onChanged(enabled)
    }
}
