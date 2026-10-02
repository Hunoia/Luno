package hunoia.luno.action.capability

import hunoia.luno.action.model.Capability

class CompositeCapabilityChecker(
    private val checkers: List<CapabilityChecker>
) : CapabilityChecker {
    override fun isAvailable(capability: Capability): Boolean {
        return checkers.any { it.isAvailable(capability) }
    }
}
