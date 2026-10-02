package hunoia.luno.action.capability

import hunoia.luno.action.model.Capability
import hunoia.luno.shizuku.ShizukuFacade

class ShizukuCapabilityChecker : CapabilityChecker {
    override fun isAvailable(capability: Capability): Boolean {
        return if (capability == Capability.Shizuku) {
            ShizukuFacade.isReady()
        } else {
            true
        }
    }
}
