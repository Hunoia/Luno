package hunoia.luno.action.model

sealed interface ActionResult {
    data object Success : ActionResult
    data class Failed(val reason: ActionFailure) : ActionResult
    data class RequiresCapability(val capability: Capability) : ActionResult
}
