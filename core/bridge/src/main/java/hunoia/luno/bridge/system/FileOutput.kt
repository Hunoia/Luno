package hunoia.luno.bridge.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog
import java.io.File

/**
 * 通用文件导出：把应用私有目录里的文件经 FileProvider 交给系统分享/保存。
 *
 * 与 `ImageOutput` 分开：图片走 MediaStore，日志等普通文件只做临时分享，
 * 不污染图库。调用方负责把文件写到已声明的 FileProvider 路径下。
 */
object FileOutput {

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** 分享一个文件；返回是否成功拉起选择器。 */
    fun share(
        context: Context,
        file: File,
        mimeType: String = "application/octet-stream",
        label: String = file.name,
    ): Boolean = runCatching {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, label, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }.onFailure {
        LunoLog.w(LOG_TAG, "share file failed: ${it.message}")
    }.isSuccess
}
