package hunoia.luno.quicklaunch.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniWindowTaskParseTest {

    @Test
    fun `分身可见任务取分身 userId`() {
        val output = """
            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              configuration={...}
              taskId=93283: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[0,0][1200,2670] userId=0 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}

            RootTask id=2 bounds=[0,0][1200,2670] displayId=0 userId=999
              configuration={...}
              taskId=93289: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[100,300][900,1500] userId=999 visible=true topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertEquals(999, Launcher.parseVisibleTaskUserId(output, "com.tencent.mm"))
    }

    @Test
    fun `只命中主用户可见任务`() {
        val output = """
            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              taskId=93283: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[0,0][1200,2670] userId=0 visible=true topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertEquals(0, Launcher.parseVisibleTaskUserId(output, "com.tencent.mm"))
    }

    @Test
    fun `不可见任务不参与判定`() {
        val output = """
            RootTask id=2 bounds=[0,0][1200,2670] displayId=0 userId=999
              taskId=93289: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[100,300][900,1500] userId=999 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertNull(Launcher.parseVisibleTaskUserId(output, "com.tencent.mm"))
    }

    @Test
    fun `包名不匹配返回空`() {
        val output = """
            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              taskId=93283: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[0,0][1200,2670] userId=0 visible=true topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertNull(Launcher.parseVisibleTaskUserId(output, "com.tencent.mobileqq"))
    }
}
