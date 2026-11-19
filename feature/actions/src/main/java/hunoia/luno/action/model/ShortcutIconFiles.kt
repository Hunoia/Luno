package hunoia.luno.action.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.longPressVariant

/**
 * 快捷方式位图图标的落盘与清理。图标必须存文件（`iconPath` 会被序列化进动作），
 * 但不属于任何一轮配置写入——只有面板/动作真正删除时才该删，集中在此避免漏删。
 */
object ShortcutIconFiles {

    private const val SHORTCUT_TYPE = "app.launchShortcut"
    private const val ICON_DIR = "shortcut_icons"

    fun directory(context: android.content.Context): File =
        File(context.filesDir, ICON_DIR)

    fun iconPathOf(action: StoredAction?): String? {
        if (action == null || action.typeId != SHORTCUT_TYPE) return null
        val data = (action.params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(data)
                .let { it as kotlinx.serialization.json.JsonObject }
                .get("iconPath")
                ?.let { element -> (element as? JsonPrimitive)?.contentOrNull }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 动作本体 + 长按变体里的图标路径（一个动作最多两份）。 */
    fun iconPathsOf(action: StoredAction?): List<String> {
        if (action == null) return emptyList()
        return listOfNotNull(
            iconPathOf(action),
            iconPathOf(action.longPressVariant()),
        )
    }

    fun delete(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching { File(path).delete() }
    }

    fun deleteFor(actions: Iterable<StoredAction>) {
        actions.forEach { action -> iconPathsOf(action).forEach(::delete) }
    }

    /** 清空整个图标目录（全量 reset 用）。 */
    fun deleteAll(context: android.content.Context) {
        runCatching { directory(context).listFiles()?.forEach { it.delete() } }
    }

    /**
     * 把动态快捷方式图标落成 PNG，返回绝对路径。
     *
     * 文件名由稳定 key 决定：同一入口重新导入时覆盖旧文件，不会在图标目录里留下副本。
     * 失败返回 null，调用方保留旧 `iconPath`。
     */
    fun saveBitmap(
        context: android.content.Context,
        bitmap: android.graphics.Bitmap?,
        key: String,
    ): String? {
        if (bitmap == null || key.isBlank()) return null
        return runCatching {
            val dir = directory(context)
            dir.mkdirs()
            val file = File(dir, "${safeName(key)}.png")
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun safeName(key: String): String {
        val cleaned = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
        // 截断后可能撞名（长包名 / 长 shortcut id）：附稳定哈希保证一一对应。
        return "${cleaned}_${key.hashCode().toUInt().toString(16)}"
    }

    /** 只保留仍被引用的路径（恢复备份后清掉上一份配置遗留的孤儿文件）。 */
    fun deleteUnreferenced(context: android.content.Context, referenced: Set<String>) {
        // 同时按文件名匹配：备份跨安装/跨设备恢复时，JSON 里的绝对路径前缀
        // 可能与当前 filesDir 不同，仅按全路径比较会把刚恢复的图标误删。
        val referencedNames = referenced.mapTo(HashSet()) { File(it).name }
        runCatching {
            directory(context).listFiles()?.forEach { file ->
                if (file.absolutePath !in referenced && file.name !in referencedNames) file.delete()
            }
        }
    }
}
