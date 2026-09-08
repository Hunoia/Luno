package hunoia.luno.action

import hunoia.luno.action.api.ActionIds
import hunoia.luno.action.api.ActionRegistry
import hunoia.luno.action.definition.ActionCatalog
import hunoia.luno.action.handlers.allHandlers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRegistryConsistencyTest {

    @Test
    fun catalog_displayedExecutableActions_areRegistered() {
        val intentionallyUnregistered = setOf(
            ActionIds.NONE,
            ActionIds.SUB_GESTURE,
        )

        val missing = ActionCatalog.definitions
            .filter { it.isDisplayed }
            .map { it.actionId }
            .filterNot { it in intentionallyUnregistered }
            .filterNot { ActionRegistry.isRegistered(it) }

        assertTrue("Missing handlers for action ids: $missing", missing.isEmpty())
    }

    @Test
    fun handler_registeredActions_areCataloguedOrInternal() {
        val catalogIds = ActionCatalog.definitions.map { it.actionId }.toSet()
        val internalRuntimeActions = setOf(
            ActionIds.EXTRA_LAUNCH_APP,
            ActionIds.EXTRA_LAUNCH_SHORTCUT,
        )

        val unknown = allHandlers
            .flatMap { it.supportedActions }
            .filterNot { it in catalogIds || it in internalRuntimeActions }

        assertTrue("Handlers expose unknown action ids: $unknown", unknown.isEmpty())
    }

    @Test
    fun handler_registeredActions_haveNoDuplicates() {
        val ids = allHandlers.flatMap { it.supportedActions }
        val duplicates = ids
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        assertTrue("Duplicate handler action ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun catalog_actionIds_haveNoDuplicates() {
        val ids = ActionCatalog.definitions.map { it.actionId }
        assertEquals(ids.toSet().size, ids.size)
    }
}
