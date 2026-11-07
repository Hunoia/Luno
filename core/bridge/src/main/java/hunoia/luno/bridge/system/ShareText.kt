package hunoia.luno.bridge.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog

/**
 * 纯文本分享：链接分发、识屏取词、剪贴板管理页共用。
 *
 * 必须先写 `clipData`：部分接收方只给 `EXTRA_TEXT` 时拿不到内容（与图片分享同口径）。
 * 平台 Intent 收口在 bridge，业务页不自己拼。
 */
fun shareText(context: Context, text: String, label: String = "Luno"): Boolean = runCatching {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newPlainText(label, text)
    }
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}.onFailure {
    LunoLog.w(LOG_TAG, "share text failed: ${it.message}")
}.isSuccess
