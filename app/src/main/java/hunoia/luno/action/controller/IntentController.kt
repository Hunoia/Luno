package hunoia.luno.action.controller

import android.content.Context
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.SettingsNamespace
import hunoia.luno.bridge.intent.launchAssist
import hunoia.luno.quicklaunch.launch.AppLaunchBypass
import hunoia.luno.quicklaunch.QuickLaunchFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun openActivity(packageName: String, activityClassName: String): Boolean =
        withContext(Dispatchers.IO) {
            AppLaunchBypass.launchActivityWithAutoUnfreeze(
                context = context,
                packageName = packageName,
                className = activityClassName,
            )
        }

    fun openAppDetails(packageName: String): Boolean {
        return try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchUrl(url: String): Boolean = context.launchUrlCompat(url)

    fun shareText(text: String, mimeType: String = "text/plain"): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, null))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareFile(filePath: String, mimeType: String = ""): Boolean {
        return try {
            val uri = android.net.Uri.fromFile(java.io.File(filePath))
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { context.contentResolver.getType(uri) ?: "*/*" }
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, null))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openFile(filePath: String, mimeType: String = ""): Boolean {
        return try {
            val uri = android.net.Uri.fromFile(java.io.File(filePath))
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { context.contentResolver.getType(uri) ?: "*/*" })
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchAssist(): Boolean {
        return try {
            val intent = android.content.Intent().apply {
                setAction(android.content.Intent.ACTION_ASSIST)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.launchUrlCompat(url: String): Boolean {
        val normalizedUrl = hunoia.luno.bridge.intent.normalizeOpenAppOrUrl(url)
            ?: return false
        return try {
            val intent = hunoia.luno.bridge.intent.buildViewIntent(normalizedUrl)
            if (packageManager.queryIntentActivities(intent, 0).isEmpty()) return false
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
