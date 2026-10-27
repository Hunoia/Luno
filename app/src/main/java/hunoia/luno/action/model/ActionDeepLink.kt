package hunoia.luno.action.model

import android.net.Uri
import android.util.Base64
import hunoia.luno.core.domain.JsonSerializer

/**
 * 动作外部调用入口：
 * - 动作库条目：`luno://action/{entryId}`
 * - 任意动作：`luno://action?data=<base64url(JSON)>`
 *
 * 其他应用或 adb 可用 VIEW 打开，由 ActionDeepLinkActivity 交给 :service 分发。
 */
object ActionDeepLink {
    const val SCHEME = "luno"
    const val HOST_ACTION = "action"
    private const val QUERY_DATA = "data"
    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun ofEntry(entryId: String): String = "$SCHEME://$HOST_ACTION/$entryId"

    fun ofAction(action: StoredAction): String {
        val json = JsonSerializer.encodeToString(action)
        val data = Base64.encodeToString(json.toByteArray(), BASE64_FLAGS)
        return "$SCHEME://$HOST_ACTION?$QUERY_DATA=$data"
    }

    fun entryId(uri: Uri?): String? {
        if (!isActionUri(uri)) return null
        // data 形式走 [action]，不再当 entryId。
        if (uri?.getQueryParameter(QUERY_DATA) != null) return null
        return uri?.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    fun action(uri: Uri?): StoredAction? {
        if (!isActionUri(uri)) return null
        val data = uri?.getQueryParameter(QUERY_DATA) ?: return null
        return runCatching {
            val json = String(Base64.decode(data, BASE64_FLAGS))
            JsonSerializer.decodeFromString<StoredAction>(json)
        }.getOrNull()
    }

    private fun isActionUri(uri: Uri?): Boolean =
        uri != null && uri.scheme == SCHEME && uri.host == HOST_ACTION
}
