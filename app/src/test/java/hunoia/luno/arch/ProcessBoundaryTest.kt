package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 进程边界门禁（architecture §1.5 第 7、8 条 / §12）。
 *
 * 两个进程的职责边界靠 Manifest 保证：
 * - 只允许出现主进程与 `:service`；出现第三个 `android:process` 即违反「常驻不增殖」；
 * - 主进程入口与常规页面不得挪进 `:service`（任务栈、返回键与锁屏收口都会变）。
 */
class ProcessBoundaryTest {

    private val manifest = File("src/main/AndroidManifest.xml")

    private val allowedProcesses = setOf(":service")

    /** 按 §12 必须留在主进程的入口。 */
    private val mainProcessComponents = listOf(
        ".ui.MainActivity",
        ".ui.link.LinkSelectionActivity",
        ".ui.ActionDeepLinkActivity",
    )

    @Test
    fun `只允许主进程与 service 进程`() {
        val text = manifestText()
        val declared = Regex("""android:process="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()

        assertFalse(
            "Manifest 出现未评审的进程：${declared - allowedProcesses}；" +
                "常驻能力必须并入既有常驻进程（architecture §1.5 第 8 条）",
            declared.any { it !in allowedProcesses },
        )
        assertTrue("`:service` 进程应至少有一个组件声明", ":service" in declared)
    }

    @Test
    fun `主进程入口与常规页面不声明 service 进程`() {
        val text = manifestText()
        val offenders = mainProcessComponents.filter { component ->
            val block = componentBlock(text, component)
            block != null && ":service" in block
        }
        assertTrue(
            "以下入口必须留在主进程（architecture §12）：${offenders.joinToString()}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `主进程只保留入口页面 service 承载运行时与浮层`() {
        val text = manifestText()
        // 反向检查：示例中若 :service 页面全部消失，说明规则被整体改坏而不是单点调整。
        val serviceActivities = Regex("""<activity[^>]*android:process=":service"""")
            .findAll(text)
            .count()

        assertTrue(
            "`:service` 应承载共享运行时状态的页面（§12），当前为 $serviceActivities 个",
            serviceActivities >= 3,
        )
    }

    private fun manifestText(): String {
        assertTrue("缺少 Manifest：${manifest.absolutePath}", manifest.isFile)
        return manifest.readText()
    }

    /** 取组件声明的起始标签（`<activity ...>`），用于判断该组件是否声明了进程。 */
    private fun componentBlock(text: String, component: String): String? {
        val at = text.indexOf("android:name=\"$component\"")
        if (at < 0) return null
        val start = text.lastIndexOf('<', at)
        val end = text.indexOf('>', at)
        if (start < 0 || end < 0) return null
        return text.substring(start, end)
    }
}
