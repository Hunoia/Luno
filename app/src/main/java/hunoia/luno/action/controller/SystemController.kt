package hunoia.luno.action.controller

import android.content.Context
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.bridge.FlashlightController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemController(private val context: Context) {

    suspend fun toggleFlashlight(): ActionResult = withContext(Dispatchers.Default) {
        if (!FlashlightController.isSupported(context)) {
            return@withContext ActionResult.Failed(ActionFailure.Unsupported)
        }
        if (!FlashlightController.hasPermission(context)) {
            return@withContext ActionResult.Failed(ActionFailure.PermissionDenied)
        }
        if (FlashlightController.toggle(context)) {
            ActionResult.Success
        } else {
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }
}
