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

/** 复制图片（content URI，现为 MediaStore 图库 URI）：聊天/笔记等支持粘贴图片的应用直接用。 */
fun copyImage(context: Context, label: String, uri: android.net.Uri): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
    true
}.getOrDefault(false)

/** 读当前剪贴板文本；无内容/无权限时返回 null。 */
fun readClipboardText(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

/** 剪贴板文本及其敏感标记，一次读出（剪贴板球用）。 */
data class ClipboardTextInfo(
    val text: String,
    val sensitive: Boolean,
)

/** 读当前剪贴板**文本**，一次带出敏感标记（剪贴板球用）；图片/URI 等非文本返回 null。 */
fun readClipboardTextDetailed(context: Context): ClipboardTextInfo? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    val clip = clipboard.primaryClip ?: return@runCatching null
    val item = clip.getItemAt(0) ?: return@runCatching null
    // 只认纯文本：coerceToText 会把图片 URI 也转成字符串，胶囊会弹出一条 content:// 长串。
    val text = item.text?.toString() ?: item.htmlText?.toString() ?: return@runCatching null
    ClipboardTextInfo(
        text = text,
        sensitive = clip.description.extras
            ?.getBoolean(ClipDescriptionExtras.IS_SENSITIVE) == true,
    )
}.getOrNull()

private object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
