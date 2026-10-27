package hunoia.luno.action.executor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import hunoia.luno.actions.R
import hunoia.luno.action.controller.ClipboardController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** P2-5：HTTP / Toast / 通知 / 写剪贴板。HTTP 用 HttpURLConnection，不额外引依赖。 */
class AutomationActionExecutors(
    private val clipboardController: ClipboardController,
) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "http.request", "system.toast", "system.notify", "system.clipboardSet",
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult = when (action) {
        is Action.HttpRequest -> executeHttp(action)
        is Action.Toast -> {
            // 分发线程不保证有 Looper，Toast 走宿主注入的主线程回调。
            context.showToast(action.text)
            ActionResult.Success()
        }
        is Action.Notify -> executeNotify(action, context.appContext)
        is Action.SetClipboard -> {
            val ok = clipboardController.copyText(action.text)
            if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
        else -> ActionResult.Failed(ActionFailure.Unsupported)
    }

    private suspend fun executeHttp(action: Action.HttpRequest): ActionResult = withContext(Dispatchers.IO) {
        val method = action.method.trim().uppercase().ifBlank { "GET" }
        if (method !in HTTP_METHODS) {
            return@withContext ActionResult.Failed(ActionFailure.InvalidParameter, "http_method_invalid")
        }
        val url = runCatching { URL(action.url.trim()) }.getOrNull()
        if (url == null ||
            url.protocol.lowercase() !in HTTP_SCHEMES ||
            url.host.isNullOrBlank()
        ) {
            return@withContext ActionResult.Failed(ActionFailure.InvalidParameter, "http_url_invalid")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                instanceFollowRedirects = true
                action.headers.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.contains(':') }
                    .forEach { line ->
                        val key = line.substringBefore(':').trim()
                        val value = line.substringAfter(':').trim()
                        if (key.isNotEmpty()) setRequestProperty(key, value)
                    }
                if (method != "GET" && method != "HEAD" && action.body.isNotEmpty()) {
                    doOutput = true
                    outputStream.use { it.write(action.body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            // 不把响应正文塞进历史或 Toast：只回状态码，正文留给后续变量能力再做。
            if (code in 200..399) {
                ActionResult.Success()
            } else {
                ActionResult.Failed(ActionFailure.ExecutionFailed, "HTTP $code")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeNotify(action: Action.Notify, context: Context): ActionResult {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return ActionResult.RequiresCapability(hunoia.luno.core.domain.Capability.Notification)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val system = context.getSystemService(NotificationManager::class.java)
            if (system.getNotificationChannel(CHANNEL_ID) == null) {
                system.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(action.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(action.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(action.text))
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
            ActionResult.Success()
        } catch (e: SecurityException) {
            ActionResult.RequiresCapability(hunoia.luno.core.domain.Capability.Notification)
        }
    }

    private companion object {
        const val HTTP_TIMEOUT_MS = 15_000
        const val CHANNEL_ID = "luno_automation_actions"
        const val CHANNEL_NAME = "自动化动作"
        val HTTP_METHODS = setOf("GET", "POST", "PUT", "DELETE", "HEAD")
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
