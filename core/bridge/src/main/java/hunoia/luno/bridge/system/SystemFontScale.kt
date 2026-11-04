package hunoia.luno.bridge.system

import android.content.Context
import android.provider.Settings

/**
 * 系统字号（`Settings.System.FONT_SCALE`）的读写口。
 *
 * 与设备上的「字体缩放器」同口径：只改全局 `font_scale`，不做单应用字号。
 * 写入需要 `WRITE_SETTINGS`（设置页授权总览已有该项）。
 */
object SystemFontScale {

    const val MIN = 0.5f
    const val MAX = 2.0f
    const val DEFAULT = 1.0f

    private const val KEY = "font_scale"

    fun canWrite(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    fun get(context: Context): Float = runCatching {
        Settings.System.getFloat(context.contentResolver, KEY, DEFAULT)
    }.getOrDefault(DEFAULT).coerceIn(MIN, MAX)

    /** 返回是否写入成功；失败时系统值不变，由调用方回退到 [get]。 */
    fun set(context: Context, scale: Float): Boolean {
        if (!canWrite(context)) return false
        val value = scale.coerceIn(MIN, MAX)
        return runCatching {
            Settings.System.putFloat(context.contentResolver, KEY, value)
        }.getOrDefault(false)
    }
}
