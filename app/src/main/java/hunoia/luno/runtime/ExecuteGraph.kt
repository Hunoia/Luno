package hunoia.luno.runtime

import android.content.Context
import hunoia.luno.R
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.Capability
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages

/**
 * 执行口端口在 `:app` 的实现。
 *
 * `:feature:execute` 只声明契约（catalog / factory / messages / environment），
 * 知识留在各自的归属层：动作目录在 `:feature:actions`、文案与资源在 `:app`、
 * 宿主能力在 [GestureHost]。
 */

/** 三态反馈文案：资源留在 `:app`，执行口只拿本地化后的字符串。 */
class LunoExecuteMessages(private val context: Context) : ExecuteMessages {
    override fun failureText(reason: ActionFailure): String = context.getString(
        when (reason) {
            ActionFailure.InvalidParameter -> R.string.toast_invalid_parameter
            ActionFailure.ExecutionFailed -> R.string.toast_execution_failed
            ActionFailure.PermissionDenied -> R.string.toast_permission_denied
            ActionFailure.Unsupported -> R.string.toast_unsupported_action
        }
    )

    override fun capabilityText(capability: Capability): String = context.getString(
        when (capability) {
            Capability.Accessibility -> R.string.toast_accessibility_required
            Capability.Shizuku -> R.string.toast_shizuku_required
            Capability.None -> R.string.toast_execution_failed
            Capability.Notification -> R.string.toast_notification_required
        }
    )

    override fun showToast(text: String) = hunoia.luno.bridge.ui.showToast(text)

    override fun showLongToast(text: String) = hunoia.luno.bridge.ui.showToastLong(text)
}

/** 宿主能力：直接转发 [GestureHost]，执行口不 import 宿主实现。 */
class LunoExecuteEnvironment(private val host: GestureHost) : ExecuteEnvironment {
    override val appContext: Context get() = host.context.applicationContext
    override val accessibilityService get() = host.accessibilityService
    override fun currentPackageName(): String? = host.getCurrentPackageName()
    override fun nowInLauncher(): Boolean = host.nowInLauncher()
    override fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit) =
        host.requestEnableDisabledPackage(packageName, onResult)
}
