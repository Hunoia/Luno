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
            putBoolean(ClipDescriptionExtras.SELF_WRITE, true)
        }
        clipboard.setPrimaryClip(clip)
        SelfWriteGuard.markText(text)
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
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = selfWriteExtras()
    clipboard.setPrimaryClip(clip)
    SelfWriteGuard.markText(text)
    true
}.getOrDefault(false)

/** 复制图片（content URI，现为 MediaStore 图库 URI）：聊天/笔记等支持粘贴图片的应用直接用。 */
fun copyImage(context: Context, label: String, uri: android.net.Uri): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    val clip = ClipData.newUri(context.contentResolver, label, uri)
    clip.description.extras = selfWriteExtras()
    clipboard.setPrimaryClip(clip)
    SelfWriteGuard.markImage(uri)
    true
}.getOrDefault(false)

private fun selfWriteExtras(): PersistableBundle = PersistableBundle().apply {
    putBoolean(ClipDescriptionExtras.SELF_WRITE, true)
}

/** 读当前剪贴板文本；无内容/无权限时返回 null。 */
fun readClipboardText(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

/** 剪贴板内容：剪贴板球只处理纯文本与图片，其余（URI/Intent 等）不弹。 */
sealed interface ClipboardContent {
    data class Text(
        val text: String,
        val sensitive: Boolean = false,
    ) : ClipboardContent

    data class Image(
        val uri: android.net.Uri,
        val mimeType: String,
    ) : ClipboardContent
}

/** 读当前剪贴板内容（文本或图片），一次带出敏感标记；不支持的类型返回 null。 */
fun readClipboardContentDetailed(context: Context): ClipboardContent? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    clipboard.primaryClip?.toClipboardContent()
}.getOrNull()

/** ClipData → 剪贴板内容；文本优先，其次图片 URI（按 MIME 判定）。 */
internal fun ClipData.toClipboardContent(): ClipboardContent? {
    val item = getItemAt(0) ?: return null
    // 只认纯文本：coerceToText 会把图片 URI 也转成字符串，胶囊会弹一条 content:// 长串。
    val rawText = item.text?.toString() ?: item.htmlText?.toString()
    if (rawText != null) {
        return ClipboardContent.Text(
            text = rawText,
            sensitive = description.extras
                ?.getBoolean(ClipDescriptionExtras.IS_SENSITIVE) == true,
        )
    }
    val uri = item.uri ?: return null
    val mime = description?.getMimeType(0).orEmpty()
    if (!mime.startsWith("image/")) return null
    return ClipboardContent.Image(uri = uri, mimeType = mime)
}


private object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    const val SELF_WRITE = "luno.self_write"
}
