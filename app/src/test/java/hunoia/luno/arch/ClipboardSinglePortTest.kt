package hunoia.luno.arch

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剪贴板单口门禁：业务代码不得直接碰 `ClipboardManager` / `ClipData`。
 *
 * 平台原语只能留在 `:core:bridge`；捕获监听是唯一例外（必须在 `:core:data` 注册
 * `OnPrimaryClipChangedListener`，但它同样只转发给 `ClipboardCenter`）。
 * 页面 / feature / 面板一旦重新直连平台剪贴板，这里直接失败。
 */
class ClipboardSinglePortTest {

    private val platformImports = setOf(
        "android.content.ClipboardManager",
        "android.content.ClipData",
    )

    /** 允许出现的文件（相对仓库根）：bridge 是平台边界；捕获监听是唯一例外。 */
    private val allowedSites = setOf(
        "core/data/src/main/java/hunoia/luno/clipboard/ClipboardCapture.kt",
    )

    private fun KoFileDeclaration.isBridge(): Boolean = repoPath().startsWith("core/bridge/")

    @Test
    fun `only bridge and the capture host may import platform clipboard types`() {
        assertTrue("生产源码为空，Konsist scope 解析失败", productionFiles.isNotEmpty())

        val offenders = productionFiles
            .filterNot { it.isBridge() || it.repoPath() in allowedSites }
            .flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it in platformImports }
                    .map { name -> "${file.repoPath()} 引用了 $name" }
            }

        assertTrue(
            "剪贴板平台 API 只能经 ClipboardCenter（architecture §8.3 / §1.5）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `capture host still exists so the gate cannot silently pass`() {
        val missing = allowedSites.filterNot { site ->
            productionFiles.any { it.repoPath() == site }
        }
        assertTrue("捕获宿主文件不存在，请同步本测试：${missing.joinToString()}", missing.isEmpty())
    }
}
