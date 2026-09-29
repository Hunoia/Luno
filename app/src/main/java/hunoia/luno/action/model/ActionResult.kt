package hunoia.luno.action.model

sealed interface ActionResult {
    data class Success(val message: String? = null) : ActionResult
    data class Failed(val reason: ActionFailure, val message: String? = null) : ActionResult
    data class RequiresCapability(val capability: Capability) : ActionResult
}
