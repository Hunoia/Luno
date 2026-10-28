package hunoia.luno.bridge

import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 启动信息序列化契约：`LauncherInfo.ShortcutInfo` 会被写入动作参数
 * （`app.launchShortcut` 的 `data`），模块必须应用 serialization 插件，
 * 否则运行时才抛 “Serializer for class 'ShortcutInfo' is not found”。
 */
class LauncherInfoSerializationTest {

    @Test
    fun `shortcut info round trips through json`() {
        val info = LauncherInfo(
            packageName = "com.example",
            className = "com.example.Main",
            label = "Example",
            shortcuts = listOf(
                LauncherInfo.ShortcutInfo(
                    packageName = "com.example",
                    className = "com.example.Shortcut",
                    intents = listOf("intent:#Intent;action=android.intent.action.VIEW;end"),
                    label = "Shortcut",
                )
            ),
        )

        val json = JsonSerializer.encodeToString(info)
        val decoded = JsonSerializer.decodeFromString<LauncherInfo>(json)

        assertEquals(info, decoded)
    }
}
