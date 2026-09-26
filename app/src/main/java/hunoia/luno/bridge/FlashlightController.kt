package hunoia.luno.bridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FlashlightController {

    private var isOn = false

    fun isFlashlightOn(): Boolean = isOn

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
            isOn = !isOn
            manager.setTorchMode(cameraId, isOn)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun set(context: Context, on: Boolean): Boolean = withContext(Dispatchers.Default) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull() ?: return@withContext false
            isOn = on
            manager.setTorchMode(cameraId, on)
            true
        } catch (_: Exception) {
            false
        }
    }
}
