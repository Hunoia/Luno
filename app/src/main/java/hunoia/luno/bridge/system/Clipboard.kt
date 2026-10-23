package hunoia.luno.bridge.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

fun copySensitiveText(
    context: Context,
    label: String,
    text: String,
): Boolean {
    return runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescriptionExtras.IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        true
    }.getOrDefault(false)
}

/** 普通剪贴板文本（带预览），用于命令输出等非敏感内容。 */
fun copyText(
    context: Context,
    label: String,
    text: String,
): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrDefault(false)

/** 读当前剪贴板文本；无内容/无权限时返回 null。 */
fun readClipboardText(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

private object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
