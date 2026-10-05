package hunoia.luno.action.capability

import android.content.Context
import hunoia.luno.action.model.Capability
import hunoia.luno.bridge.isAccessibilitySettingsOn
import hunoia.luno.service.SideGestureService

/**
 * 独立能力闸：每个 capability 由唯一对应的检查负责。
 *
 * 曾用 CompositeCapabilityChecker(List).any { it.isAvailable(cap) }，而每个 checker 对不归
 * 自己管的 capability 都返回 true，于是 any 恒为 true，闸门从未触发，
 * ActionResult.RequiresCapability 成了死路径。改為單一 when，杜絕此類短路。
 */
class CapabilityGate(
    private val appContext: Context,
    private val isServiceAlive: () -> Boolean = {
        hunoia.luno.service.DispatchClient.isReady() ||
            SideGestureService.current != null
    },
) {
    fun isAvailable(capability: Capability): Boolean = when (capability) {
        Capability.None -> true
        Capability.Shizuku -> hunoia.luno.shizuku.ShizukuFacade.isReady()
        Capability.Accessibility ->
            appContext.isAccessibilitySettingsOn(SideGestureService::class.java) ||
                isServiceAlive()
    }
}
