package hunoia.luno.action.capability

import hunoia.luno.action.model.Capability

interface CapabilityChecker {
    fun isAvailable(capability: Capability): Boolean
}
