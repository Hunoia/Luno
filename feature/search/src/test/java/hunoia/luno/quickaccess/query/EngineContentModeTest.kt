package hunoia.luno.quickaccess.query

import hunoia.luno.config.model.EngineContentMode
import hunoia.luno.config.model.EngineIconType
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎点击主动作（[enginePrimaryAction]）与图标（[engineRowIcon]）的回归。
 *
 * contentMode 是设置页给出的承诺：选「只分享图片」点引擎就该选图，选「每次询问」
 * 就该弹选择器。这里锁住三种取向的实际行为，避免再次变成只存不用的死字段。
 */
class EngineContentModeTest {

    private fun engine(
        contentMode: EngineContentMode = EngineContentMode.Text,
        imageShareEnabled: Boolean = false,
        imageTargetPackage: String = "",
        launch: SearchLaunch? = null,
    ) = SearchEngine(
        id = "e",
        name = "E",
        urlTemplate = "https://example.com/?q=%s",
        launch = launch,
        imageShareEnabled = imageShareEnabled,
        imageTargetPackage = imageTargetPackage,
        contentMode = contentMode,
    )

    @Test
    fun `text mode searches text even when image sharing is enabled`() {
        val e = engine(
            contentMode = EngineContentMode.Text,
            imageShareEnabled = true,
            imageTargetPackage = "com.example.target",
        )
        assertTrue(enginePrimaryAction(e, "q") is SearchAction.OpenUrl)
    }

    @Test
    fun `image mode picks image instead of searching`() {
        val e = engine(
            contentMode = EngineContentMode.Image,
            imageShareEnabled = true,
            imageTargetPackage = "com.example.target",
        )
        val action = enginePrimaryAction(e, "q") as SearchAction.PickImage
        assertEquals("com.example.target", action.packageName)
    }

    @Test
    fun `ask mode defers the choice to the panel`() {
        val e = engine(
            contentMode = EngineContentMode.Ask,
            imageShareEnabled = true,
            imageTargetPackage = "com.example.target",
        )
        val action = enginePrimaryAction(e, "q") as SearchAction.AskContent
        assertEquals("com.example.target", action.imagePackageName)
        assertTrue(action.textAction is SearchAction.OpenUrl)
    }

    @Test
    fun `image and ask fall back to text without an image target`() {
        // 双模式没开（或目标包名为空）：contentMode 残留 Image/Ask 也不能阻止搜索。
        val e = engine(contentMode = EngineContentMode.Image, imageShareEnabled = false)
        assertTrue(enginePrimaryAction(e, "q") is SearchAction.OpenUrl)
    }

    @Test
    fun `pure image engine ignores content mode`() {
        val e = engine(
            contentMode = EngineContentMode.Text,
            launch = SearchLaunch.ShareImage("com.example.target"),
        )
        val action = enginePrimaryAction(e, "q") as SearchAction.PickImage
        assertEquals("com.example.target", action.packageName)
    }

    @Test
    fun `engine row primary action follows content mode`() {
        val e = engine(
            contentMode = EngineContentMode.Image,
            imageShareEnabled = true,
            imageTargetPackage = "com.example.target",
        )
        val row = engineResultRow("搜索 E", "q", e)
        assertTrue(row.primary is SearchAction.PickImage)
        // 长按动作仍然提供分享图片入口。
        assertTrue(row.actions.any { it.action is SearchAction.PickImage })
    }

    @Test
    fun `engine icon follows icon type and falls back to badge text`() {
        assertEquals(
            SearchIcon.Text("E"),
            engineRowIcon(engine().copy(iconType = EngineIconType.Text)),
        )
        assertEquals(
            SearchIcon.AppIcon("com.example.app"),
            engineRowIcon(engine().copy(iconType = EngineIconType.App, icon = "com.example.app")),
        )
        assertEquals(
            SearchIcon.Request("content://img/1"),
            engineRowIcon(engine().copy(iconType = EngineIconType.Image, icon = "content://img/1")),
        )
        // 来源缺失回退文字徽标，不渲染空图标。
        assertEquals(
            SearchIcon.Text("E"),
            engineRowIcon(engine().copy(iconType = EngineIconType.App, icon = "")),
        )
        assertEquals(
            SearchIcon.Text("E"),
            engineRowIcon(engine().copy(iconType = EngineIconType.Image, icon = "")),
        )
    }

    @Test
    fun `activity launch maps to inject query action`() {
        val action = SearchLaunch.Activity(
            targetPackage = "com.example.app",
            targetActivity = "com.example.app.SearchActivity",
            autoInputEnter = true,
        ).toSearchAction("查询 词") as SearchAction.InjectQuery
        assertEquals("com.example.app", action.targetPackage)
        assertEquals("com.example.app.SearchActivity", action.targetActivity)
        assertEquals("查询 词", action.query)
        assertTrue(action.autoInputEnter)
    }

    @Test
    fun `activity launch prefers extern link when activity is blank`() {
        val action = SearchLaunch.Activity(
            targetPackage = "com.example.app",
            externLink = "https://example.com/s?q=%s",
        ).toSearchAction("q") as SearchAction.InjectQuery
        assertEquals("", action.targetActivity)
        assertEquals("https://example.com/s?q=%s", action.externLink)
    }
}
