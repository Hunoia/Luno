package hunoia.luno.shizuku

import android.content.ClipData
import android.util.Log
import hunoia.luno.bridge.system.ClipboardTextInfo
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 经 Shizuku 以 shell 身份读剪贴板。HyperOS 等新 ROM 对普通应用既不投递
 * OnPrimaryClipChangedListener、也拒绝后台 getPrimaryClip（无障碍不豁免、AppOps 放行无效），
 * 但 shell uid 在放行名单里 —— 实测 shell 侧 `getPrimaryClip(s16 com.android.shell)`
 * 返回真实内容。调用包名必须写 shell 自己的包名：binder 调用由 shizuku_server 以
 * shell uid 发出，写应用包名会被「包不属于调用 uid」校验拒绝。
 */
object ShizukuClipboard {

    private const val TAG = LOG_TAG
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"

    private val iclass by lazy { Class.forName("android.content.IClipboard") }

    private val proxy by lazy {
        val stubClass = Class.forName("android.content.IClipboard\$Stub")
        val binder = SystemServiceHelper.getSystemService("clipboard")
        HiddenApiBypass.invoke(stubClass, null, "asInterface", ShizukuBinderWrapper(binder))
    }

    /** Android 15/16 签名 4 参（末位 ROM 私有 int，传 0 即验证可用），旧版 3/2 参。hidden 方法须走 HiddenApiBypass 查找。 */
    private val getPrimaryClip: java.lang.reflect.Method by lazy {
        HiddenApiBypass.getDeclaredMethods(iclass)
            .filterIsInstance<java.lang.reflect.Method>()
            .firstOrNull { it.name == "getPrimaryClip" }
            ?: throw NoSuchMethodException("getPrimaryClip not on $iclass")
    }

    /** 当前剪贴板文本 + 敏感标记；无内容/不可用（Shizuku 未连接、读取被拒等）返回 null。 */
    fun readText(): ClipboardTextInfo? = runCatching {
        val clip = when (getPrimaryClip.parameterCount) {
            4 -> getPrimaryClip.invoke(proxy, SHELL_PACKAGE, null, 0, 0)
            3 -> getPrimaryClip.invoke(proxy, SHELL_PACKAGE, null, 0)
            else -> getPrimaryClip.invoke(proxy, SHELL_PACKAGE, 0)
        } as? ClipData
        if (clip == null) {
            Log.i(TAG, "shizuku clipboard: null clip (empty or denied)")
            return@runCatching null
        }
        val text = clip.getItemAt(0)?.coerceToText(AppContext.get())?.toString()
        if (text == null) {
            Log.i(TAG, "shizuku clipboard: null item text")
            return@runCatching null
        }
        val sensitive = runCatching {
            clip.description.extras?.getBoolean(SENSITIVE_EXTRA) == true
        }.getOrDefault(false)
        ClipboardTextInfo(text, sensitive)
    }.getOrElse {
        Log.w(TAG, "shizuku clipboard read failed: ${it::class.simpleName} ${it.message}")
        null
    }
}
