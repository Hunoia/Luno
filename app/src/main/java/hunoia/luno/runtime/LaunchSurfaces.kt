package hunoia.luno.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import hunoia.luno.action.controller.IntentController
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.execute.LaunchSpec
import hunoia.luno.launch.AppLaunchBypass
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.LauncherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 执行口「启动基础设施」端口在 `:app` 的实现。
 *
 * `:feature:execute` 不持有 `AppLaunchBypass` / `QuickLaunchFacade` / 小窗设置，只声明
 * 需要「起哪个应用 / 开哪个链接」；具体启动机制（解冻、小窗、弹窗、快捷方式）留在这里。
 * Phase 3 搜索模块拆出后再决定这些实现是否整体搬进 execute。
 */
class LaunchSurfaces(
    private val context: Context,
    private val advancedSettings: () -> AdvancedSettings,
    private val enableDisabledPackage: suspend (String) -> Boolean,
) {
    private val intentController = IntentController(context)

    suspend fun launch(spec: LaunchSpec): Boolean = AppLaunchBypass.launchWithAutoUnfreeze(
        context = context,
        packageName = spec.packageName,
        className = spec.className,
        miniWindow = spec.miniWindow,
        miniWindowSettings = advancedSettings().miniWindowSettings(),
        intentAction = spec.intentAction,
        data = spec.data,
        extras = spec.extras,
        unfreezePackage = { _, pkg -> enableDisabledPackage(pkg) },
    )

    suspend fun openUrl(
        url: String,
        miniWindow: Boolean,
        queryParameters: List<hunoia.luno.action.model.QueryParam>,
    ): Boolean = QuickLaunchFacade.launchUrlDirect(
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

    suspend fun launchShortcut(dataJson: String): Boolean {
        val info = runCatching {
            JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(dataJson)
        }.getOrNull() ?: return false
        QuickLaunchFacade.launchShortcutInfo(context, info)
        return true
    }

    suspend fun openAppDetails(packageName: String): ActionResult =
        intentController.openAppDetails(packageName)

    /** 当前应用以小窗打开：包名/Activity 解析与全局小窗设置都在这一侧。 */
    suspend fun launchInPopup(
        packageName: String,
        currentPackage: () -> String?,
        nowInLauncher: () -> Boolean,
    ): ActionResult {
        val pkgName = currentPackage() ?: return ActionResult.Failed(ActionFailure.ExecutionFailed)
        if (nowInLauncher() || pkgName.isEmpty()) return ActionResult.Success()
        val intent = Intent().apply {
            setPackage(pkgName)
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
        if (!className.isNullOrEmpty()) {
            QuickLaunchFacade.launchAppInPopup(
                context, pkgName, className,
                advancedSettings().miniWindowSettings(),
            )
        }
        return ActionResult.Success()
    }
}
