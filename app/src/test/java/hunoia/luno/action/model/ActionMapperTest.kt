package hunoia.luno.action.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionMapperTest {

    @Test
    fun `app launch maps intent overrides`() {
        val action = ActionMapper.fromStored(
            StoredAction(
                "app.launch",
                buildJsonObject {
                    put("packageName", JsonPrimitive("com.example"))
                    put("className", JsonPrimitive("com.example.Main"))
                    put("intentAction", JsonPrimitive("android.intent.action.VIEW"))
                    put("data", JsonPrimitive("example://open"))
                    put("extras", buildJsonObject {
                        put("id", JsonPrimitive("42"))
                        put("", JsonPrimitive("ignored"))
                    })
                },
            ),
        ) as Action.LaunchApp

        assertEquals("com.example", action.packageName)
        assertEquals("com.example.Main", action.className)
        assertEquals("android.intent.action.VIEW", action.intentAction)
        assertEquals("example://open", action.data)
        assertEquals(mapOf("id" to "42"), action.extras)
    }

    @Test
    fun `app launch without overrides stays on defaults`() {
        val action = ActionMapper.fromStored(
            StoredAction("app.launch", buildJsonObject { put("packageName", JsonPrimitive("com.example")) }),
        ) as Action.LaunchApp

        assertEquals("", action.intentAction)
        assertEquals("", action.data)
        assertTrue(action.extras.isEmpty())
        assertFalse(action.miniWindow)
    }

    @Test
    fun `open url reads key value map and mini window`() {
        val action = ActionMapper.fromStored(
            StoredAction(
                "intent.openUrl",
                buildJsonObject {
                    put("url", JsonPrimitive("https://example.com"))
                    put("miniWindow", JsonPrimitive(true))
                    put("queryParameters", buildJsonObject {
                        put("q", JsonPrimitive("luno"))
                        put("", JsonPrimitive("ignored"))
                    })
                },
            ),
        ) as Action.OpenUrl

        assertTrue(action.miniWindow)
        assertEquals(listOf(QueryParam("q", "luno")), action.queryParameters)
    }

    @Test
    fun `shell custom maps copy output`() {
        val action = ActionMapper.fromStored(
            StoredAction(
                "shell.custom",
                buildJsonObject {
                    put("command", JsonPrimitive("id"))
                    put("copyOutput", JsonPrimitive(true))
                },
            ),
        ) as Action.CustomCommand

        assertEquals("id", action.command)
        assertTrue(action.copyOutput)
        assertFalse(action.showToast)
    }

    @Test
    fun `extras object with non primitive value falls back to empty string`() {
        val action = ActionMapper.fromStored(
            StoredAction(
                "app.launch",
                JsonObject(
                    mapOf(
                        "packageName" to JsonPrimitive("com.example"),
                        "extras" to buildJsonObject { put("nested", buildJsonObject { }) },
                    ),
                ),
            ),
        ) as Action.LaunchApp

        assertEquals(mapOf("nested" to ""), action.extras)
    }
}
