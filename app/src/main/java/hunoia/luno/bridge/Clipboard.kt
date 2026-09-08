package hunoia.luno.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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

private object ClipDescriptionExtras {
    const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
}
