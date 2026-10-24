package hunoia.luno.action.controller

import android.content.Context
import android.util.Log
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.quicklaunch.launch.AppLaunchBypass
import hunoia.luno.ui.share.ShareFilePickActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hunoia.luno.core.LOG_TAG

class IntentController(private val context: Context) {

    suspend fun launchApp(packageName: String, className: String, miniWindow: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            AppLaunchBypass.launchWithAutoUnfreeze(
                context = context,
                packageName = packageName,
                className = className,
                miniWindow = miniWindow,
            )
        }

    fun openAppDetails(packageName: String): ActionResult {
        return try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "openAppDetails failed", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }

    fun shareText(text: String, mimeType: String = "text/plain"): ActionResult {
        return try {
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startChooser(share)
            ActionResult.Success()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "shareText failed", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }

    fun shareFile(): ActionResult = launchFilePicker(ShareFilePickActivity.ACTION_SHARE)

    private fun startChooser(target: android.content.Intent) {
        val chooser = android.content.Intent.createChooser(target, null)
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun launchFilePicker(action: String): ActionResult {
        return try {
            context.startActivity(
                android.content.Intent(context, ShareFilePickActivity::class.java).apply {
                    setAction(action)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            ActionResult.Success()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "launchFilePicker failed", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }

    fun launchAssist(): ActionResult {
        return try {
            val intent = android.content.Intent().apply {
                setAction(android.content.Intent.ACTION_ASSIST)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "launchAssist failed", e)
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }
    }
}
