package hunoia.luno.action.execution

import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.Capability
import hunoia.luno.action.capability.CapabilityChecker
import hunoia.luno.action.definitions.ActionDefinitions

class ActionExecutorRegistry(
    private val executors: List<ActionExecutor>,
    private val capabilityChecker: CapabilityChecker? = null,
) {
    private val executorMap: Map<String, ActionExecutor> = executors
        .flatMap { e -> e.supportedTypes.map { it to e } }
        .toMap()

    init {
        val seen = mutableSetOf<String>()
        for (executor in executors) {
            for (typeId in executor.supportedTypes) {
                check(seen.add(typeId)) {
                    "Duplicate typeId: $typeId registered by ${executor::class.simpleName}"
                }
            }
        }
    }

    suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        val executor = executorMap[action.typeId] ?: return ActionResult.Failed(ActionFailure.Unsupported)
        val capability = ActionDefinitions.byTypeId(action.typeId)?.capability ?: Capability.None
        if (capability != Capability.None && capabilityChecker?.isAvailable(capability) == false) {
            return ActionResult.RequiresCapability(capability)
        }
        return runCatching {
            executor.execute(action, context)
        }.getOrElse { e ->
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }
}
