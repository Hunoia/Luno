package hunoia.luno.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.core.JsonSerializer

/**
 * 真 IPC 分发口（跑在 :service 进程）：跨进程拿不到静态引用，统一走 Binder，
 * 未就绪返回 false 由调用方提示，不静默丢弃。
 * 屏幕取点留在此侧：必须盖在别的应用上，而只有 `TYPE_ACCESSIBILITY_OVERLAY`
 *（仅 AccessibilityService 可 addView）能免「显示在其他应用上层」权限，
 * 后者还要 Activity 持窗，多引一项权限。
 */
class DispatchService : Service() {

    private val binder = object : IDispatchService.Stub() {
        override fun dispatchAction(actionJson: String): Boolean {
            val action = runCatching {
                JsonSerializer.decodeFromString<StoredAction>(actionJson)
            }.getOrNull() ?: return false
            val svc = SideGestureService.current ?: return false
            if (!svc.isDispatchReady()) return false
            svc.dispatchAction(action)
            return true
        }

        override fun isReady(): Boolean =
            SideGestureService.current?.isDispatchReady() == true

        override fun showPointPicker(initialXDp: Int, initialYdp: Int, cb: IPointPickCallback) {
            val svc = SideGestureService.current
            if (svc == null) {
                // 无障碍服务没跑（常见于只给正式包开了无障碍）：必须回调失败，
                // 否则调用方只能看到「点击无反应」。
                runCatching { cb.onUnavailable() }
                return
            }
            Handler(Looper.getMainLooper()).post {
                svc.pointPickOverlay.show(
                    initialXdp = initialXDp.takeIf { it >= 0 },
                    initialYdp = initialYdp.takeIf { it >= 0 },
                    commit = { xDp, yDp -> runCatching { cb.onCommit(xDp, yDp) } },
                    onDismiss = { runCatching { cb.onDismiss() } },
                    onError = { runCatching { cb.onUnavailable() } },
                )
            }
        }

        override fun releaseOrphanAppWidgets(keepIds: IntArray?) {
            // AppWidgetHost 的创建/回收只在 :service 做，避免主进程再挂一个同 HOST_ID 的监听。
            val keep = keepIds?.toSet() ?: emptySet()
            Handler(Looper.getMainLooper()).post {
                runCatching { hunoia.luno.gridpanel.widget.PanelAppWidgetHost.get().releaseOrphans(keep) }
            }
        }

        override fun runningRuleIds(): String =
            runCatching {
                JsonSerializer.encodeToString(
                    (SideGestureService.current?.runningRuleIds() ?: emptySet()).toList(),
                )
            }.getOrDefault("[]")

        override fun stopRule(ruleId: String): Boolean =
            SideGestureService.current?.stopRule(ruleId) == true

        override fun runRuleTest(ruleJson: String, executeSteps: Boolean, cb: IRuleTestCallback): Boolean {
            val rule = runCatching {
                JsonSerializer.decodeFromString<AutomationRule>(ruleJson)
            }.getOrNull() ?: return false
            val svc = SideGestureService.current ?: return false
            if (!svc.isDispatchReady()) return false
            Handler(Looper.getMainLooper()).post {
                val started = runCatching {
                    svc.runRuleTest(rule, executeSteps) { result ->
                        // 编码失败也要回调空串，让客户端走「结果不可用」提示而不是一直等。
                        val json = runCatching {
                            JsonSerializer.encodeToString(result)
                        }.getOrDefault("")
                        runCatching { cb.onResult(json) }
                    }
                }.getOrDefault(false)
                if (!started) runCatching { cb.onResult("") }
            }
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}