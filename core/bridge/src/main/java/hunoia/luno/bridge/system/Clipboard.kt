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
 * 一律打 `IS_SENSITIVE` 系统标记：挡住了其他应用后台静默读取，也让系统预览遮蔽。
 * Luno 自己读回来时不再据此区分处理（历史一律进库，见 `ClipboardCenter`）。
 * 同时打 Luno 自写标记（`SelfWriteGuard`），避免复制动作自己弹气泡。
 * 返回是否写入成功。
 */
fun writeTextToClipboard(
    context: Context,
    label: String,
    text: String,
): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = writeExtras(sensitive = true)
    clipboard.setPrimaryClip(clip)
    SelfWriteGuard.markText(text)
    true
}.getOrDefault(false)

/**
 * 若系统剪贴板当前**仍是** [expected] 这段文本，则清空它；否则什么都不做。
 *
 * 用于「复制后自动清除」：用户在这段时间里又复制了别的内容时，绝不能把别人的东西擦掉。
 * 返回是否真的清空了。
 */
fun clearTextIfUnchanged(context: Context, expected: String): Boolean = runCatching {
    if (expected.isEmpty()) return@runCatching false
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    if (!clipboard.hasPrimaryClip()) return@runCatching false
    val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return@runCatching false
    if (current != expected) return@runCatching false
    // 写一段空文本再清：部分 ROM 不接受 setPrimaryClip(null)。
    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    clipboard.clearPrimaryClip()
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
    data class Text(val text: String) : ClipboardContent

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
    if (rawText != null) return ClipboardContent.Text(rawText)
    val uri = item.uri ?: return null
    val mime = description?.getMimeType(0).orEmpty()
    if (!mime.startsWith("image/")) return null
    return ClipboardContent.Image(uri = uri, mimeType = mime)
}

internal object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    const val SELF_WRITE = "luno.self_write"
}
