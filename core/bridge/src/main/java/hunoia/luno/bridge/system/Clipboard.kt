package hunoia.luno.bridge.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle

/**
 * 系统剪贴板的平台原语。
 *
 * **单一入口约定**：业务代码不直接调用本文件的读写函数，一律经
 * `hunoia.luno.clipboard.ClipboardCenter`（读取要 Shizuku 回退、写入要打自写标记、
 * 记录要按规则入库）。`ClipboardSinglePortTest` 守着这条边界。
 */
fun readClipboardContentDetailed(context: Context): ClipboardContent? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    clipboard.primaryClip?.toClipboardContent()
}.getOrNull()

/**
 * 写文本到系统剪贴板。
 *
 * [sensitive] 为 true 时打系统敏感标记，并让 `ClipboardManager` 预遮蔽预览；
 * 无论敏感与否都打 Luno 自写标记（`SelfWriteGuard`），避免复制动作自己弹气泡。
 * 返回是否写入成功。
 */
fun writeTextToClipboard(
    context: Context,
    label: String,
    text: String,
    sensitive: Boolean,
): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = writeExtras(sensitive)
    clipboard.setPrimaryClip(clip)
    SelfWriteGuard.markText(text)
    true
}.getOrDefault(false)

/** 写图片（content URI）到系统剪贴板：聊天 / 笔记等支持粘贴图片的应用可直接使用。 */
fun writeImageToClipboard(
    context: Context,
    label: String,
    uri: Uri,
): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    val clip = ClipData.newUri(context.contentResolver, label, uri)
    clip.description.extras = writeExtras(sensitive = false)
    clipboard.setPrimaryClip(clip)
    SelfWriteGuard.markImage(uri)
    true
}.getOrDefault(false)

private fun writeExtras(sensitive: Boolean): PersistableBundle = PersistableBundle().apply {
    putBoolean(ClipDescriptionExtras.SELF_WRITE, true)
    if (sensitive) putBoolean(ClipDescriptionExtras.IS_SENSITIVE, true)
}

/** 剪贴板内容：只处理纯文本与图片，其余（URI / Intent 等）不参与捕获。 */
sealed interface ClipboardContent {
    data class Text(
        val text: String,
        val sensitive: Boolean = false,
    ) : ClipboardContent

    data class Image(
        val uri: Uri,
        val mimeType: String,
    ) : ClipboardContent
}

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

internal object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    const val SELF_WRITE = "luno.self_write"
}
