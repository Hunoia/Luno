package hunoia.luno.arch

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * 架构门禁共享的生产源码集合（Konsist / Kotlin PSI）。
 *
 * 各测试不再自己 `File("..").walkTopDown()`，统一从 Konsist 的 project scope 取文件：
 * - 只看 `src/main`（发布源码），排除 debug/release 与测试源码；
 * - `repoPath()` 统一成仓库相对路径（如 `app/src/...`），跨平台分隔符一致；
 * - `codeText()` 去掉注释，供表达式级检查（构造点、字面量）使用——Konsist 不建模函数调用。
 */
internal val productionFiles: List<KoFileDeclaration> by lazy {
    Konsist.scopeFromProduction().files
        .filter { "/src/main/" in it.projectPath.replace('\\', '/') }
}

internal fun KoFileDeclaration.repoPath(): String =
    projectPath.replace('\\', '/').trimStart('/')

internal fun KoFileDeclaration.codeText(): String =
    text
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

internal fun KoFileDeclaration.importsName(predicate: (String) -> Boolean): Boolean =
    imports.any { predicate(it.name) }
