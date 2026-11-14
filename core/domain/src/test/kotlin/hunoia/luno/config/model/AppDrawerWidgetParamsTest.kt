package hunoia.luno.config.model

import hunoia.luno.core.domain.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 抽屉列数参数：横竖屏各记一份，JSON 往返要稳（旧数据缺失按未配置处理）。 */
class AppDrawerWidgetParamsTest {

    @Test
    fun `columns are remembered per orientation`() {
        val params = AppDrawerWidgetParams()
            .withColumns(landscape = false, columns = 5)
            .withColumns(landscape = true, columns = 3)

        assertEquals(5, params.columns(landscape = false))
        assertEquals(3, params.columns(landscape = true))
        assertNull(AppDrawerWidgetParams().columns(landscape = false))
    }

    @Test
    fun `json round trip keeps both orientations`() {
        val params = AppDrawerWidgetParams(portraitColumns = 4, landscapeColumns = 6)
        val json = JsonSerializer.encodeToString(params)
        assertEquals(params, JsonSerializer.decodeFromString<AppDrawerWidgetParams>(json))
    }

    @Test
    fun `missing fields decode as unset`() {
        val params = JsonSerializer.decodeFromString<AppDrawerWidgetParams>("""{"portraitColumns":7}""")
        assertEquals(7, params.columns(landscape = false))
        assertNull(params.columns(landscape = true))
    }
}
