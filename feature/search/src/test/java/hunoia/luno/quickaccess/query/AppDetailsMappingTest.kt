package hunoia.luno.quickaccess.query

import hunoia.luno.core.domain.AppInfo
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 分身应用详情必须携带 userId，否则系统设置页按 `package:` 只认主用户实例，
 * 长按分身会打开主应用详情（见 [QuickSearchResultMapping.toSearchResult]）。
 */
class AppDetailsMappingTest {

    private fun detailsAction(appInfo: AppInfo): StoredAction {
        val result = QuickSearchResult.App(appInfo).toSearchResult()
        val item = result.actions.single()
        return (item.action as SearchAction.Run).action
    }

    @Test
    fun `clone app details carries userId`() {
        val action = detailsAction(AppInfo("com.example", "Main", "示例", userId = 999))
        assertEquals("999", action.params["userId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `main app details omits userId`() {
        val action = detailsAction(AppInfo("com.example", "Main", "示例"))
        assertFalse(action.params.containsKey("userId"))
    }
}
