package hunoia.luno.bridge.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock

/** 最近一次 Luno 自写剪贴板的时刻（uptimeMillis）：剪贴板球用它过滤自家复制。 */
@Volatile
private var lastSelfCopyAtMs = 0L

/** [windowMs] 内 Luno 自己写过剪贴板视为「自复制」。 */
fun isSelfCopyRecent(windowMs: Long = 1_500L): Boolean =
    SystemClock.uptimeMillis() - lastSelfCopyAtMs < windowMs

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
        lastSelfCopyAtMs = SystemClock.uptimeMillis()
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
    lastSelfCopyAtMs = SystemClock.uptimeMillis()
    true
}.getOrDefault(false)

/** 复制图片（FileProvider content URI）：聊天/笔记等支持粘贴图片的应用直接用。 */
fun copyImage(context: Context, label: String, uri: android.net.Uri): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching false
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
    lastSelfCopyAtMs = SystemClock.uptimeMillis()
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

/** 读当前剪贴板文本 + 敏感标记；无内容/无权限（Android 10+ 后台读被限）返回 null。 */
fun readClipboardTextDetailed(context: Context): ClipboardTextInfo? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return@runCatching null
    if (!clipboard.hasPrimaryClip()) return@runCatching null
    val clip = clipboard.primaryClip ?: return@runCatching null
    val text = clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    ClipboardTextInfo(
        text = text,
        sensitive = clip.description.extras
            ?.getBoolean(ClipDescriptionExtras.IS_SENSITIVE) == true,
    )
}.getOrNull()

private object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
