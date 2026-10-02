package hunoia.luno.action.controller

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToast

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
        data?.data?.let { uri ->
            if (requestCode == REQUEST_PICK && resultCode == RESULT_OK) {
                perform(uri)
            }
        }
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
            showToast(R.string.launch_no_handler)
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
