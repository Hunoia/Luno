package hunoia.luno.action

import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.ActionLibraryType
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.core.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionLibraryResolverTest {

    @Test
    fun `parseReference returns null for nonLibraryAction`() {
        assertNull(ActionLibraryResolver.parseReference(Action("back")))
    }

    @Test
    fun `parseReference returns ref for shellAction`() {
        val ref = ActionLibraryRefData("e1")
        val action = Action(ActionFacade.EXECUTE_SHELL_COMMAND, JsonSerializer.encodeToString(ref))
        assertEquals(ref, ActionLibraryResolver.parseReference(action))
    }

    @Test
    fun `parseReference returns null for invalidJson`() {
        assertNull(ActionLibraryResolver.parseReference(Action(ActionFacade.EXECUTE_SHELL_COMMAND, "not-json")))
    }

    @Test
    fun `nonReferenceAction returns original`() {
        val action = Action("back")
        val result = ActionLibraryResolver.resolveReference(action, emptyList())
        assertEquals(action, result)
    }

    @Test
    fun `invalidRefJson returns original`() {
        val action = Action(ActionFacade.EXECUTE_SHELL_COMMAND, "not-json")
        val result = ActionLibraryResolver.resolveReference(action, emptyList())
        assertEquals(action, result)
    }

    @Test
    fun `blankEntryId returns original`() {
        val action = Action(ActionFacade.EXECUTE_SHELL_COMMAND, JsonSerializer.encodeToString(ActionLibraryRefData("")))
        val result = ActionLibraryResolver.resolveReference(action, emptyList())
        assertEquals(action, result)
    }

    @Test
    fun `entryNotFound returns null`() {
        val action = Action(ActionFacade.EXECUTE_SHELL_COMMAND, JsonSerializer.encodeToString(ActionLibraryRefData("e1")))
        val result = ActionLibraryResolver.resolveReference(action, emptyList())
        assertNull(result)
    }

    @Test
    fun `shellEntryResolvesCorrectly`() {
        val entry = ActionLibraryEntry(id = "e1", type = ActionLibraryType.Shell, shellCommand = ShellCommandData(command = "echo hi"))
        val action = Action(ActionFacade.EXECUTE_SHELL_COMMAND, JsonSerializer.encodeToString(ActionLibraryRefData("e1")))
        val result = ActionLibraryResolver.resolveReference(action, listOf(entry))
        assertEquals(ActionFacade.EXECUTE_SHELL_COMMAND, result?.value)
        assertEquals(JsonSerializer.encodeToString(entry.shellCommand), result?.data)
    }

    @Test
    fun `urlEntryResolvesCorrectly`() {
        val entry = ActionLibraryEntry(id = "e2", type = ActionLibraryType.Url, openAppOrUrl = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, url = "https://example.com", miniWindow = false))
        val action = Action(ActionFacade.OPEN_URL, JsonSerializer.encodeToString(ActionLibraryRefData("e2")))
        val result = ActionLibraryResolver.resolveReference(action, listOf(entry))
        assertEquals(ActionFacade.OPEN_URL, result?.value)
        assertEquals(JsonSerializer.encodeToString(entry.openAppOrUrl), result?.data)
    }

    @Test
    fun `activityEntryResolvesCorrectly`() {
        val entry = ActionLibraryEntry(id = "e3", type = ActionLibraryType.Activity, openAppOrUrl = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_ACTIVITY, packageName = "com.example", activityClassName = ".Main"))
        val action = Action(ActionFacade.OPEN_APP_ACTIVITY, JsonSerializer.encodeToString(ActionLibraryRefData("e3")))
        val result = ActionLibraryResolver.resolveReference(action, listOf(entry))
        assertEquals(ActionFacade.OPEN_APP_ACTIVITY, result?.value)
        assertEquals(JsonSerializer.encodeToString(entry.openAppOrUrl), result?.data)
    }
}
