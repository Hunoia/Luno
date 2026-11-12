package hunoia.luno.quickaccess.launch

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

    @Test
    fun `最近任务取第一个命中的 userId（背景里的分身）`() {
        val output = """
            RootTask id=2 bounds=[0,0][1200,2670] displayId=0 userId=999
              taskId=93289: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[100,300][900,1500] userId=999 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}

            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              taskId=93283: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[0,0][1200,2670] userId=0 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertEquals(999, Launcher.parseRecentTaskUserId(output, "com.tencent.mm"))
    }

    @Test
    fun `最近任务主应用优先时取主用户`() {
        val output = """
            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              taskId=93283: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[0,0][1200,2670] userId=0 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}

            RootTask id=2 bounds=[0,0][1200,2670] displayId=0 userId=999
              taskId=93289: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[100,300][900,1500] userId=999 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertEquals(0, Launcher.parseRecentTaskUserId(output, "com.tencent.mm"))
    }

    @Test
    fun `最近任务包名不匹配返回空`() {
        val output = """
            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              taskId=93283: com.tencent.mm/com.tencent.mm.ui.LauncherUI bounds=[0,0][1200,2670] userId=0 visible=false topActivity=ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}
        """.trimIndent()

        assertNull(Launcher.parseRecentTaskUserId(output, "com.tencent.mobileqq"))
    }

    @Test
    fun `任务解析带 RootTask 窗口模式`() {
        val output = """
            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              configuration={... mWindowingMode=fullscreen ...}
              taskId=100: com.foo/com.foo.MainActivity bounds=[0,0][1200,2670] userId=0 visible=true topActivity=ComponentInfo{com.foo/com.foo.MainActivity}

            RootTask id=2 bounds=[20,165][1000,1005] displayId=0 userId=0
              configuration={... mWindowingMode=freeform ...}
              taskId=200: com.bar/com.bar.MainActivity bounds=[20,165][1000,1005] userId=0 visible=true topActivity=ComponentInfo{com.bar/com.bar.MainActivity}
        """.trimIndent()

        val tasks = Launcher.parseStackTasks(output)
        assertEquals(2, tasks.size)
        assertEquals("fullscreen", tasks[0].windowingMode)
        assertEquals("freeform", tasks[1].windowingMode)
        assertEquals(200, tasks[1].taskId)
        assertEquals("com.bar", tasks[1].packageName)
        assertEquals(true, tasks[1].visible)
    }

    private val smallWindowOverFullscreen = """
        RootTask id=2 bounds=[20,165][1000,1005] displayId=0 userId=0
          configuration={... mWindowingMode=freeform ...}
          taskId=200: com.bar/com.bar.MainActivity bounds=[20,165][1000,1005] userId=0 visible=true topActivity=ComponentInfo{com.bar/com.bar.MainActivity}

        RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
          configuration={... mWindowingMode=fullscreen ...}
          taskId=100: com.foo/com.foo.MainActivity bounds=[0,0][1200,2670] userId=0 visible=true topActivity=ComponentInfo{com.foo/com.foo.MainActivity}
    """.trimIndent()

    @Test
    fun `当前应用是小窗时互换选它下面的全屏任务`() {
        val plan = Launcher.chooseMiniWindowTarget(
            tasks = Launcher.parseStackTasks(smallWindowOverFullscreen),
            packageName = "com.bar",
            userId = 0,
            autoSwap = true,
            selfPackage = "hunoia.luno.debug",
        )!!

        assertEquals("com.foo", plan.target.packageName)
        assertEquals("com.bar", plan.swap?.packageName)
        assertEquals(true, plan.swapped)
    }

    @Test
    fun `关闭互换后小窗应用仍是目标`() {
        val plan = Launcher.chooseMiniWindowTarget(
            tasks = Launcher.parseStackTasks(smallWindowOverFullscreen),
            packageName = "com.bar",
            userId = 0,
            autoSwap = false,
            selfPackage = "hunoia.luno.debug",
        )!!

        assertEquals("com.bar", plan.target.packageName)
        assertNull(plan.swap)
        assertEquals(false, plan.swapped)
    }

    @Test
    fun `当前应用全屏时互换旧小窗`() {
        val plan = Launcher.chooseMiniWindowTarget(
            tasks = Launcher.parseStackTasks(smallWindowOverFullscreen),
            packageName = "com.foo",
            userId = 0,
            autoSwap = true,
            selfPackage = "hunoia.luno.debug",
        )!!

        assertEquals("com.foo", plan.target.packageName)
        assertEquals("com.bar", plan.swap?.packageName)
        assertEquals(false, plan.swapped)
    }

    @Test
    fun `当前应用是小窗时不把桌面当成互换目标`() {
        val output = """
            RootTask id=2 bounds=[20,165][1000,1005] displayId=0 userId=0
              configuration={... mWindowingMode=freeform mActivityType=standard ...}
              taskId=200: com.bar/com.bar.MainActivity bounds=[20,165][1000,1005] userId=0 visible=true topActivity=ComponentInfo{com.bar/com.bar.MainActivity}

            RootTask id=1 bounds=[0,0][1200,2670] displayId=0 userId=0
              configuration={... mWindowingMode=fullscreen mActivityType=home ...}
              taskId=2: com.miui.home/com.miui.home.launcher.Launcher bounds=[0,0][1200,2670] userId=0 visible=true topActivity=ComponentInfo{com.miui.home/com.miui.home.launcher.Launcher}
        """.trimIndent()

        val plan = Launcher.chooseMiniWindowTarget(
            tasks = Launcher.parseStackTasks(output),
            packageName = "com.bar",
            userId = 0,
            autoSwap = true,
            selfPackage = "hunoia.luno.debug",
        )!!

        assertEquals("com.bar", plan.target.packageName)
        assertNull(plan.swap)
        assertEquals(false, plan.swapped)
    }

    @Test
    fun `目标应用不存在时不产生计划`() {
        val plan = Launcher.chooseMiniWindowTarget(
            tasks = Launcher.parseStackTasks(smallWindowOverFullscreen),
            packageName = "com.qux",
            userId = 0,
            autoSwap = true,
            selfPackage = "hunoia.luno.debug",
        )

        assertNull(plan)
    }
}
