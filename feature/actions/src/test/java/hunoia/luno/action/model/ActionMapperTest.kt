package hunoia.luno.action.model

import hunoia.luno.core.domain.QueryParam
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
                    put("userId", JsonPrimitive("999"))
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
        assertEquals(999, action.userId)
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
    fun `recents picker maps layout param with default`() {
        val horizontal = ActionMapper.fromStored(
            StoredAction(
                "internal.recentsPicker",
                buildJsonObject { put("layout", JsonPrimitive("HORIZONTAL")) },
            ),
        ) as Action.RecentsPicker
        assertEquals(RecentsPickerLayout.HORIZONTAL, horizontal.layout)

        // 未写 layout 落默认竖向；值存在却非法按枚举约定抛受控异常（不裸 valueOf 崩协程）。
        val fallback = ActionMapper.fromStored(
            StoredAction("internal.recentsPicker", JsonObject(emptyMap())),
        ) as Action.RecentsPicker
        assertEquals(RecentsPickerLayout.VERTICAL, fallback.layout)

        try {
            ActionMapper.fromStored(
                StoredAction(
                    "internal.recentsPicker",
                    buildJsonObject { put("layout", JsonPrimitive("NOPE")) },
                ),
            )
            fail("脏 layout 应抛 InvalidActionParameterException")
        } catch (expected: InvalidActionParameterException) {
            assertEquals("layout", expected.key)
        }
    }

    @Test
    fun `recents picker config decode falls back to vertical`() {
        val horizontal = StoredAction(
            "internal.recentsPicker",
            buildJsonObject { put("layout", JsonPrimitive("HORIZONTAL")) },
        )
        assertEquals(RecentsPickerLayout.HORIZONTAL, horizontal.recentsPickerConfigOrDefault().layout)

        val default = StoredAction("internal.recentsPicker", JsonObject(emptyMap()))
        assertEquals(RecentsPickerLayout.VERTICAL, default.recentsPickerConfigOrDefault().layout)

        // 非本动作类型读不到参数，也落默认。
        val other = StoredAction("internal.volumeScrub", JsonObject(emptyMap()))
        assertEquals(RecentsPickerLayout.VERTICAL, other.recentsPickerConfigOrDefault().layout)
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
