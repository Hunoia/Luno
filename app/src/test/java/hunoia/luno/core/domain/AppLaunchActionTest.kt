package hunoia.luno.core.domain

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `app.launch` 参数只允许从 [AppLaunchAction] 生成，避免各处手拼漂移。 */
class AppLaunchActionTest {

    @Test
    fun `stored keeps canonical launch parameters`() {
        val action = AppLaunchAction.stored(
            packageName = "com.example.app",
            className = "com.example.app.MainActivity",
            miniWindow = true,
            intentAction = "android.intent.action.VIEW",
            data = "https://example.com/a",
            extras = mapOf("source" to "luno"),
        )

        assertEquals(AppLaunchAction.TYPE_ID, action.typeId)
        assertEquals("com.example.app", action.params.string("packageName"))
        assertEquals("com.example.app.MainActivity", action.params.string("className"))
        assertEquals("true", action.params.string("miniWindow"))
        assertEquals("android.intent.action.VIEW", action.params.string("intentAction"))
        assertEquals("https://example.com/a", action.params.string("data"))
        assertEquals("luno", action.params["extras"]?.jsonObject?.get("source")?.jsonPrimitive?.content)
    }

    @Test
    fun `stored omits optional defaults`() {
        val action = AppLaunchAction.stored(packageName = "com.example.app")

        assertEquals("com.example.app", action.params.string("packageName"))
        assertFalse(action.params.containsKey("className"))
        assertFalse(action.params.containsKey("miniWindow"))
        assertFalse(action.params.containsKey("intentAction"))
        assertFalse(action.params.containsKey("data"))
        assertFalse(action.params.containsKey("extras"))
    }

    @Test
    fun `stored marks mini window for launcher`() {
        val action = AppLaunchAction.stored(packageName = "com.example.app", miniWindow = true)

        assertTrue(action.params.containsKey("miniWindow"))
        assertEquals("true", action.params.string("miniWindow"))
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content
}
