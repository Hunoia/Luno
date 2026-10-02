package hunoia.luno.action.capability

import hunoia.luno.action.model.Capability

class AccessibilityCapabilityChecker(
    private val isEnabled: () -> Boolean,
) : CapabilityChecker {
    override fun isAvailable(capability: Capability): Boolean {
        return if (capability == Capability.Accessibility) isEnabled() else true
    }
}
