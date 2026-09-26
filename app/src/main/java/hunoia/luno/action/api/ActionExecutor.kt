package hunoia.luno.action.api

import hunoia.luno.config.model.Action

class ActionExecutor(
    handlers: List<ActionHandler>,
    private val resolveAction: suspend (Action) -> Action? = { it },
) {
    private val handlerMap: Map<String, ActionHandler> = handlers
        .flatMap { handler -> handler.supportedActions.map { it to handler } }
        .toMap()

    init {
        val seen = mutableSetOf<String>()
        for (handler in handlers) {
            for (id in handler.supportedActions) {
                check(seen.add(id)) {
                    "Duplicate actionId: $id registered by ${handler::class.simpleName}"
                }
            }
        }
    }

    fun isRegistered(actionId: String): Boolean = actionId in handlerMap

    suspend fun execute(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        val executable = resolveAction(action) ?: return ActionExecutionResult.Ignored
        val handler = handlerMap[executable.value] ?: return ActionExecutionResult.Ignored
        return runCatching {
            handler.handle(executable, context)
        }.getOrElse { e ->
            ActionExecutionResult.Failed(e.message)
        }
    }
}
