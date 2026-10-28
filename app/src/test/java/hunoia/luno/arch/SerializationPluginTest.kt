package hunoia.luno.arch

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 序列化插件门禁：源码里出现 `@Serializable` 的模块必须应用
 * `kotlinx.serialization` 插件，否则运行时才抛
 * “Serializer for class ... is not found”。
 *
 * 这类问题单测编译期发现不了，拆模块后尤其容易漏。
 */
class SerializationPluginTest {

    private val modulesRoot = File("..")

    @Test
    fun `modules using Serializable apply the serialization plugin`() {
        val problems = moduleBuildFiles().mapNotNull { buildFile ->
            val moduleDir = requireNotNull(buildFile.parentFile)
            val usesSerializable = File(moduleDir, "src/main").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .any { file ->
                    val text = file.readText()
                    "kotlinx.serialization.Serializable" in text || "@Serializable" in text
                }
            if (!usesSerializable) return@mapNotNull null
            val appliesPlugin = buildFile.readText().contains("kotlin.serialization")
            if (appliesPlugin) null else moduleDir.name
        }
        assertTrue(
            "以下模块使用了 @Serializable 但未应用 serialization 插件：${problems.joinToString()}",
            problems.isEmpty(),
        )
    }

    private fun moduleBuildFiles(): List<File> {
        val roots = listOf("core", "feature", "ui", "capability").map { File(modulesRoot, it) }
        return roots.filter { it.isDirectory }.flatMap { root ->
            root.listFiles()
                ?.filter { it.isDirectory }
                ?.map { File(it, "build.gradle.kts") }
                ?.filter { it.isFile }
                ?: emptyList()
        }
    }
}
