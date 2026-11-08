package hunoia.luno.bridge.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.core.content.ContextCompat
import hunoia.luno.bridge.ui.assertNotMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object FlashlightController {

    private const val TAG = "FlashlightController"

    /** setTorchMode 瞬态失败（CameraService 繁忙/被占用/刚回收）的重试次数与间隔。 */
    private const val SET_TORCH_RETRY_MAX = 3
    private const val SET_TORCH_RETRY_DELAY_MS = 150L

    /** 系统真实 torch 开关状态，由 [torchCallback] 维护（API 37 移除了 getTorchState）。 */
    @Volatile
    private var torchOn = false

    /** 当前跟踪的 torch cameraId，用于回调比对与变更时重注册。 */
    @Volatile
    private var trackedCameraId: String? = null

    private var callbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == trackedCameraId) torchOn = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == trackedCameraId) torchOn = false
        }
    }

    /**
     * 设备是否存在摄像头（宽松检查，避免 CameraService 瞬态故障导致误报「设备不支持」；
     * 是否有闪光灯由 [hasTorchUnit] 精确判断，失败统一落到 [toggle] 尝试重试）。
     */
    fun isSupported(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 是否存在支持闪光灯的摄像头。
     *
     * - 任一相机明确 `FLASH_INFO_AVAILABLE` → true；
     * - 全部相机明确无闪光灯 → false；
     * - 存在特性读不到的相机（CameraService 瞬态故障）→ 保守返回 true，
     *   交给 [toggle] 去尝试，避免把瞬时异常误报成「设备不支持」。
     */
    fun hasTorchUnit(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val ids = manager.cameraIdList
            if (ids.isEmpty()) return false
            var indeterminate = false
            for (id in ids) {
                val available = try {
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                } catch (_: Exception) {
                    null
                }
                when (available) {
                    true -> return true
                    null -> indeterminate = true
                    else -> Unit
                }
            }
            indeterminate
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun toggle(context: Context): Boolean = withContext(Dispatchers.Default) {
        assertNotMainThread("flashlight.toggle")
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findTorchCameraId(manager) ?: return@withContext false
            ensureTorchCallback(context, manager, cameraId)
            // 以系统真实开关状态为基准（TorchCallback 持续校准），而不是仅靠进程内缓存：
            // 进程重启 / 系统手电筒开关 / 其他应用开灯后，缓存会与实际状态脱节，导致方向错误。
            val target = !torchOn
            if (setTorchMode(manager, cameraId, target)) {
                // setTorchMode 成功即更新本地状态；TorchCallback 随后回调再次校准。
                torchOn = target
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun set(context: Context, on: Boolean): Boolean = withContext(Dispatchers.Default) {
        assertNotMainThread("flashlight.set")
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findTorchCameraId(manager) ?: return@withContext false
            ensureTorchCallback(context, manager, cameraId)
            if (setTorchMode(manager, cameraId, on)) {
                torchOn = on
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 注册 torch 状态回调（首次或 cameraId 变更时），让 [torchOn] 与系统真实开关状态同步。
     * 注册失败不致命：仅失去外部状态校准，[setTorchMode] 的重试仍能消化瞬态异常。
     */
    private fun ensureTorchCallback(
        context: Context,
        manager: CameraManager,
        cameraId: String,
    ) {
        if (callbackRegistered && trackedCameraId == cameraId) return
        if (callbackRegistered) {
            try {
                manager.unregisterTorchCallback(torchCallback)
            } catch (_: Exception) {
            }
        }
        try {
            manager.registerTorchCallback(ContextCompat.getMainExecutor(context), torchCallback)
            callbackRegistered = true
            trackedCameraId = cameraId
        } catch (e: Exception) {
            Log.w(TAG, "registerTorchCallback failed: $e")
        }
    }

    /** 返回首个支持闪光灯的 cameraId；全部相机均无闪光灯时返回 null。 */
    private fun findTorchCameraId(manager: CameraManager): String? {
        for (id in manager.cameraIdList) {
            val available = try {
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } catch (_: Exception) {
                false
            }
            if (available) return id
        }
        return null
    }

    /**
     * 设置 torch 模式并短暂重试瞬态失败。
     *
     * CameraService 在 torch 空闲回收、被系统手电筒开关或其他应用占用时，首次调用会抛瞬态的
     * [CameraAccessException]（CAMERA_IN_USE / CAMERA_DISCONNECTED / CAMERA_DISABLED），重试几次可恢复；
     * [IllegalArgumentException]（该 camera 无 torch 单元）为永久失败，不重试。
     */
    private suspend fun setTorchMode(
        manager: CameraManager,
        cameraId: String,
        on: Boolean,
    ): Boolean {
        var last: Exception? = null
        repeat(SET_TORCH_RETRY_MAX) { attempt ->
            if (attempt > 0) delay(SET_TORCH_RETRY_DELAY_MS)
            try {
                manager.setTorchMode(cameraId, on)
                return true
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "setTorchMode($cameraId, $on) permanent failure: $e")
                return false
            } catch (e: CameraAccessException) {
                last = e
                Log.w(TAG, "setTorchMode($cameraId, $on) attempt=${attempt + 1} transient: reason=${e.reason} $e")
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "setTorchMode($cameraId, $on) attempt=${attempt + 1} failure: $e")
            }
        }
        Log.w(TAG, "setTorchMode($cameraId, $on) gave up after $SET_TORCH_RETRY_MAX attempts: $last")
        return false
    }
}
