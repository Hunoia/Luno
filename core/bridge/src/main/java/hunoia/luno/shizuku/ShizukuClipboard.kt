package hunoia.luno.shizuku

import android.content.ClipData
import android.os.IBinder
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.system.ClipDescriptionExtras
import hunoia.luno.bridge.system.SelfWriteGuard
import hunoia.luno.bridge.system.toClipboardContent
import hunoia.luno.core.LOG_TAG
import java.lang.reflect.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 经 Shizuku 以 shell 身份读剪贴板。HyperOS 等新 ROM 对普通应用既不投递
 * OnPrimaryClipChangedListener、也拒绝后台 getPrimaryClip（无障碍不豁免、AppOps 放行无效），
 * 但 shell uid 在放行名单里 —— 实测 shell 侧 `getPrimaryClip(s16 com.android.shell)`
 * 返回真实内容。调用包名必须写 shell 自己的包名：binder 调用由 shizuku_server 以
 * shell uid 发出，写应用包名会被「包不属于调用 uid」校验拒绝。
 *
 * 代理与方法按 binder 存活缓存：Shizuku 重启（服务端换新 binder）后必须重建，
 * 否则缓存里包着死 binder 的代理会让后续每次读取都抛异常，功能静默失效。
 */
object ShizukuClipboard {

    private const val TAG = LOG_TAG
    private const val SHELL_PACKAGE = "com.android.shell"

    /** 一次可用的调用链：代理对象 + 其来源 binder + 选定签名的 getPrimaryClip。 */
    private class Session(
        val proxy: Any,
        val binder: IBinder,
        val method: Method,
        val writeMethod: Method?,
    )

    @Volatile
    private var session: Session? = null

    private val iclass by lazy { Class.forName("android.content.IClipboard") }

    /**
     * 建一次会话。`getPrimaryClip` 在新旧 ROM 上签名不同（2/3/4 参），且反射返回顺序
     * 不确定 —— 按参数个数从新到旧显式挑，别用 `firstOrNull { name }` 撞运气。
     */
    private fun openSession(): Session {
        val binder = checkNotNull(SystemServiceHelper.getSystemService("clipboard")) {
            "clipboard service binder unavailable"
        }
        val stubClass = Class.forName("android.content.IClipboard\$Stub")
        val proxy = checkNotNull(
            HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder)),
        ) { "IClipboard.asInterface returned null" }
        val method = pickGetPrimaryClip()
        Log.i(TAG, "shizuku clipboard session ready (${method.parameterCount}-arg signature)")
        return Session(proxy, binder, method, pickSetPrimaryClip())
    }

    /** `setPrimaryClip` 同样有多个签名（带 attributionTag / userId）；找不到就不支持 shell 写。 */
    private fun pickSetPrimaryClip(): Method? = runCatching {
        HiddenApiBypass.getDeclaredMethods(iclass)
            .filterIsInstance<Method>()
            .filter { it.name == "setPrimaryClip" }
            .maxByOrNull { it.parameterCount }
    }.getOrNull()

    private fun pickGetPrimaryClip(): Method {
        val candidates = HiddenApiBypass.getDeclaredMethods(iclass)
            .filterIsInstance<Method>()
            .filter { it.name == "getPrimaryClip" && it.returnType == ClipData::class.java }
        if (candidates.isEmpty()) {
            throw NoSuchMethodException("getPrimaryClip not on $iclass")
        }
        // 新 ROM 在前：4 参（带 deviceId）→ 3 参（带 attributionTag）→ 2 参。
        return candidates.firstOrNull { it.parameterCount == 4 }
            ?: candidates.firstOrNull { it.parameterCount == 3 }
            ?: candidates.firstOrNull { it.parameterCount == 2 }
            ?: throw NoSuchMethodException(
                "unexpected getPrimaryClip signatures: ${candidates.map { it.parameterCount }}",
            )
    }

    private fun currentSession(): Session {
        session?.let { cached ->
            if (cached.binder.isBinderAlive) return cached
            session = null
        }
        synchronized(this) {
            session?.let { cached ->
                if (cached.binder.isBinderAlive) return cached
            }
            return openSession().also { session = it }
        }
    }

    /** 当前剪贴板内容（文本或图片）；无内容/不可用（Shizuku 未连接、读取被拒等）返回 null。 */
    fun readContent(): ClipboardContent? {
        // binder 抖动/Shizuku 重启后重建一次会话再试；业务侧读不到就按不可用处理。
        repeat(2) { attempt ->
            try {
                return readOnce()
            } catch (t: Throwable) {
                session = null
                if (attempt == 1) {
                    Log.w(TAG, "shizuku clipboard read failed: ${t::class.simpleName} ${t.message}")
                }
            }
        }
        return null
    }

    private fun readOnce(): ClipboardContent? {
        val active = currentSession()
        val clip = when (active.method.parameterCount) {
            4 -> active.method.invoke(active.proxy, SHELL_PACKAGE, null, 0, 0)
            3 -> active.method.invoke(active.proxy, SHELL_PACKAGE, null, 0)
            else -> active.method.invoke(active.proxy, SHELL_PACKAGE, 0)
        } as? ClipData
        if (clip == null) {
            Log.i(TAG, "shizuku clipboard: null clip (empty or denied)")
            return null
        }
        val content = clip.toClipboardContent()
        if (content == null) {
            Log.i(TAG, "shizuku clipboard: unsupported item")
        }
        return content
    }

    /**
     * 以 shell 身份写剪贴板（应用直写失败时的兜底）。
     *
     * 注意语义差异：写入后剪贴板 owner 是 `com.android.shell`，个别应用会拒绝粘贴
     * 非前台应用来源的内容。所以调用方必须「直写优先」，这里只兜底。
     */
    fun writeContent(content: ClipboardContent): Boolean {
        val clip = when (content) {
            is ClipboardContent.Text -> runCatching {
                ClipData.newPlainText("Luno", content.text).also { data ->
                    data.description.extras = android.os.PersistableBundle().apply {
                        putBoolean(ClipDescriptionExtras.SELF_WRITE, true)
                        if (content.sensitive) {
                            putBoolean(ClipDescriptionExtras.IS_SENSITIVE, true)
                        }
                    }
                }
            }.getOrNull()
            // 图片写入要求 URI 授权在 shell 侧，跨 uid 不可靠；这里只兜底文本。
            is ClipboardContent.Image -> null
        } ?: return false
        repeat(2) { attempt ->
            try {
                val active = currentSession()
                val method = active.writeMethod ?: return false
                val args = when (method.parameterCount) {
                    4 -> arrayOf(clip, SHELL_PACKAGE, null, 0)
                    3 -> arrayOf(clip, SHELL_PACKAGE, 0)
                    else -> arrayOf(clip, SHELL_PACKAGE)
                }
                method.invoke(active.proxy, *args)
                // shell 写入同样要打自写标记：否则监听读回这条内容会当成外部复制。
                (content as? ClipboardContent.Text)?.let { SelfWriteGuard.markText(it.text) }
                return true
            } catch (t: Throwable) {
                session = null
                if (attempt == 1) {
                    Log.w(TAG, "shizuku clipboard write failed: ${t::class.simpleName} ${t.message}")
                }
            }
        }
        return false
    }

    /**
     * 以 shell 身份读取剪贴板图片的字节。
     *
     * 后台路径的 `ClipData` 是 shell uid 读出来的，URI 读授权落在 shell 侧，本进程
     * `contentResolver` 打不开；这里用 `content read` 把字节要回来，才能存成私有副本。
     */
    suspend fun readUriBytes(uri: android.net.Uri, maxBytes: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            val quoted = uri.toString().replace("'", "'\\''")
            when (
                val opened = ShizukuShellStream.openArgv(
                    "sh",
                    "-c",
                    "content read --uri '$quoted' 2>/dev/null",
                )
            ) {
                is ShizukuShellStream.OpenResult.Failure -> {
                    Log.w(TAG, "shizuku read uri failed: ${opened.reason}")
                    null
                }
                is ShizukuShellStream.OpenResult.Success -> opened.session.use { session ->
                    runCatching { session.readBytes(maxBytes) }
                        .onFailure {
                            Log.w(TAG, "shizuku read uri bytes failed: ${it.message}")
                        }
                        .getOrNull()
                }
            }
        }
}
