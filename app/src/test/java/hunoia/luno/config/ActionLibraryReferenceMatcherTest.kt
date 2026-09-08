package hunoia.luno.config

import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.core.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionLibraryReferenceMatcherTest {

    @Test
    fun isReferenceTo_returnsTrue_whenEntryIdMatches() {
        val action = actionRef("entry-a")

        assertEquals(true, ActionLibraryReferenceMatcher.isReferenceTo(action, "entry-a"))
    }

    @Test
    fun isReferenceTo_returnsFalse_whenEntryIdDiffers() {
        val action = actionRef("entry-a")

        assertEquals(false, ActionLibraryReferenceMatcher.isReferenceTo(action, "entry-b"))
    }

    @Test
    fun isReferenceTo_returnsFalse_whenEntryIdIsBlank() {
        val action = actionRef("")

        assertEquals(false, ActionLibraryReferenceMatcher.isReferenceTo(action, "entry-a"))
    }

    @Test
    fun isReferenceTo_returnsFalse_whenDataIsInvalid() {
        val action = Action(value = "60", data = "not-json")

        assertEquals(false, ActionLibraryReferenceMatcher.isReferenceTo(action, "entry-a"))
    }

    @Test
    fun isReferenceTo_returnsFalse_whenActionIsNotLibraryBacked() {
        val action = Action(
            value = "not-library-backed",
            data = JsonSerializer.encodeToString(ActionLibraryRefData("entry-a")),
        )

        assertEquals(false, ActionLibraryReferenceMatcher.isReferenceTo(action, "entry-a"))
    }

    private fun actionRef(entryId: String): Action {
        return Action(
            value = "60",
            data = JsonSerializer.encodeToString(ActionLibraryRefData(entryId)),
        )
    }
}
