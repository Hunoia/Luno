package hunoia.luno.shizuku

import android.content.ClipData
import android.os.IBinder
import android.util.Log
import hunoia.luno.bridge.system.ClipboardTextInfo
import hunoia.luno.core.LOG_TAG
import java.lang.reflect.Method
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
    private const val SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"

    /** 一次可用的调用链：代理对象 + 其来源 binder + 选定签名的 getPrimaryClip。 */
    private class Session(
        val proxy: Any,
        val binder: IBinder,
        val method: Method,
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
        return Session(proxy, binder, method)
    }

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

    /** 当前剪贴板文本 + 敏感标记；无内容/不可用（Shizuku 未连接、读取被拒等）返回 null。 */
    fun readText(): ClipboardTextInfo? {
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

    private fun readOnce(): ClipboardTextInfo? {
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
        // 只认纯文本：coerceToText 会把图片 URI 也转成字符串，胶囊会弹一条 content:// 长串。
        val item = clip.getItemAt(0)
        val text = item?.text?.toString() ?: item?.htmlText?.toString()
        if (text == null) {
            Log.i(TAG, "shizuku clipboard: non-text item")
            return null
        }
        val sensitive = runCatching {
            clip.description.extras?.getBoolean(SENSITIVE_EXTRA) == true
        }.getOrDefault(false)
        return ClipboardTextInfo(text, sensitive)
    }
}
