package hunoia.luno.bridge.launcher

import android.graphics.Bitmap
import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Keep
data class LauncherInfo (
    val packageName: String,
    val className: String,
    val label: String,
    val shortcuts: List<ShortcutInfo> = emptyList(),
) {
    @Serializable
    @Keep
    data class ShortcutInfo(
        val packageName: String,
        val className: String,
        val intents: List<String>,
        val label: String,
        val iconRes: Int = 0,
        val iconPath: String? = null,
        /** 静态/动态快捷方式 id；非默认 launcher 用它走 LauncherApps 启动非导出 trampoline。 */
        val shortcutId: String? = null,
        @Transient
        val iconBitmap: Bitmap? = null
    )
}
