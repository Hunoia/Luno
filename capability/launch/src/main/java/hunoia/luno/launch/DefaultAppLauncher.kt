package hunoia.luno.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.AppLauncher
import hunoia.luno.core.domain.AppLaunchSource
import hunoia.luno.core.domain.LaunchSpec
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.quicklaunch.launch.AppLaunchCoordinator
import hunoia.luno.quicklaunch.launch.Launcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `AppLauncher` 的默认实现（`:capability:launch`）。
 *
 * 装配参数都是调用方持有的能力：
 * - [advancedSettings]：小窗设置来源（配置快照，只读）；
 * - [unfreezePackage]：请求解冻被禁用应用（无障碍/Shizuku 侧实现，`:app` 提供）。
 *
 * 统计经 [LaunchCapability] 注入的 `LaunchTelemetry` 落库，本类不碰配置写入。
 */
class DefaultAppLauncher(
    private val context: Context,
    private val advancedSettings: () -> AdvancedSettings,
    private val unfreezePackage: suspend (String) -> Boolean,
) : AppLauncher {

    override suspend fun launch(spec: LaunchSpec): Boolean = AppLaunchBypass.launchWithAutoUnfreeze(
        context = context,
        packageName = spec.packageName,
        className = spec.className,
        miniWindow = spec.miniWindow,
        miniWindowSettings = advancedSettings().miniWindowSettings(),
        intentAction = spec.intentAction,
        data = spec.data,
        extras = spec.extras,
        source = AppLaunchSource.ACTION_DISPATCH,
        unfreezePackage = { _, pkg -> unfreezePackage(pkg) },
    )

    override suspend fun launchShortcut(dataJson: String): Boolean {
        val shortcut = runCatching {
            hunoia.luno.core.domain.JsonSerializer
                .decodeFromString<ShortcutIntents>(dataJson)
        }.getOrNull() ?: return false
        return Launcher.launchShortcutWithFallback(
            context = context,
            packageName = shortcut.packageName,
            shortcutId = shortcut.shortcutId,
            intentUris = shortcut.intents,
            label = "",
        )
    }

    override suspend fun launchInPopup(packageName: String): ActionResult {
        if (packageName.isBlank()) return ActionResult.Failed(ActionFailure.ExecutionFailed)
        val intent = Intent().apply {
            setPackage(packageName)
            setAction(Intent.ACTION_MAIN)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // MATCH_ALL 是 PMS 重查询（几十 ms 级），别占手势收尾的主线程。
        val resolveInfo = withContext(Dispatchers.IO) {
            context.packageManager
                .queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
                .firstOrNull()
        }
        val className = resolveInfo?.activityInfo?.name
        if (className.isNullOrEmpty()) return ActionResult.Success()
        Launcher.launchAppInPopup(
            context = context,
            packageName = packageName,
            className = className,
            miniWindowSettings = advancedSettings().miniWindowSettings(),
        )
        return ActionResult.Success()
    }

    override suspend fun openUrl(
        url: String,
        miniWindow: Boolean,
        queryParameters: List<QueryParam>,
    ): Boolean = Launcher.launchUrl(
        context = context,
        data = OpenAppOrUrlData(
            type = OpenAppOrUrlData.TYPE_URL,
            url = url,
            miniWindow = miniWindow,
            queryParameters = queryParameters.map {
                OpenUrlQueryParameter(name = it.name, value = it.value, enabled = it.enabled)
            },
        ),
        miniWindowSettings = advancedSettings().miniWindowSettings(),
    )

    override suspend fun openAppDetails(packageName: String): ActionResult = try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        ActionResult.Success()
    } catch (e: Exception) {
        Log.e(LOG_TAG, "openAppDetails failed", e)
        ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
    }
}

/**
 * 落库的快捷方式 JSON 里启动只需要 `intents`；`ignoreUnknownKeys` 让查询侧模型
 * （带 icon 字段）与这里解耦，不必把带 Bitmap 的模型挪进契约层。
 */
@kotlinx.serialization.Serializable
private data class ShortcutIntents(
    val packageName: String = "",
    val shortcutId: String? = null,
    val intents: List<String> = emptyList(),
)
