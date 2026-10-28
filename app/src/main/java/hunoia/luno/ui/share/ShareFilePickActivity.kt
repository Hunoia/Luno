package hunoia.luno.ui.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast

class ShareFilePickActivity : Activity() {

    companion object {
        const val ACTION_SHARE = "hunoia.luno.action.SHARE_FILE"

        private const val REQUEST_PICK = 1
    }

    private var dispatched = false
    private var lostFocusAfterDispatch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatch(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK) {
            // 用户在文件选择器里直接返回：必须收掉本 Activity，否则会留下一个
            // 没有内容、却占着窗口焦点的透明 Activity，表现为屏幕触摸无效。
            finish()
            return
        }
        data?.data?.let(::perform) ?: finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!dispatched) return
        if (hasFocus && lostFocusAfterDispatch) finish()
        else if (!hasFocus) lostFocusAfterDispatch = true
    }

    private fun dispatch(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHARE -> startActivityForResult(pickIntent(), REQUEST_PICK)
            else -> finish()
        }
    }

    private fun pickIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }

    private fun perform(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = resolveMime(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, null))
            dispatched = true
            lostFocusAfterDispatch = false
        } catch (ignored: ActivityNotFoundException) {
            showToast(hunoia.luno.core.bridge.R.string.launch_no_handler)
            finish()
        }
    }

    private fun resolveMime(uri: Uri): String? {
        contentResolver.getType(uri)?.takeIf { it.isNotBlank() && it != "*/*" }?.let { return it }
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: Uri.decode(uri.lastPathSegment)
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (ext.isEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
