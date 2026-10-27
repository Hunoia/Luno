package hunoia.luno.bridge.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FlashlightController {

    private var isOn = false

    fun isSupported(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun toggle(context: Context): Boolean = withContext(Dispatchers.Default) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull() ?: return@withContext false
            val target = !isOn
            manager.setTorchMode(cameraId, target)
            // 仅在 setTorchMode 成功后翻转缓存；失败时保持旧值，否则连续 toggle 会基于错误状态。
            isOn = target
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun set(context: Context, on: Boolean): Boolean = withContext(Dispatchers.Default) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull() ?: return@withContext false
            manager.setTorchMode(cameraId, on)
            isOn = on
            true
        } catch (_: Exception) {
            false
        }
    }
}
