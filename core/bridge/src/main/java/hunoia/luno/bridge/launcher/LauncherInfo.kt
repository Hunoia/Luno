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
        /**
         * 动态快捷方式所属资料空间的用户序列号（`UserManager.getSerialNumberForUser`）。
         * 仅动态导入的入口会填；静态 XML 快捷方式与旧落库 JSON 为 -1（主用户）。
         */
        val userSerial: Long = -1L,
        /**
         * 资料空间的 Android 用户 id（`UserHandle.hashCode()`，0 = 主用户）。
         * 静态 XML 快捷方式与旧落库 JSON 为 -1（无资料空间，随包挂到所有同包行）；
         * 选择页用它把动态入口只挂到对应资料空间的应用行。
         */
        val userId: Int = -1,
        @Transient
        val iconBitmap: Bitmap? = null
    )
}

/** 动态导入的引用 → 选择器/搜索共用的启动信息形态。 */
fun hunoia.luno.core.domain.DynamicShortcutRef.toShortcutInfo(): LauncherInfo.ShortcutInfo =
    LauncherInfo.ShortcutInfo(
        packageName = packageName,
        // 动态快捷方式没有可启动的 Activity 类名，启动一律走 LauncherApps。
        className = "",
        intents = emptyList(),
        label = label,
        iconPath = iconPath,
        shortcutId = shortcutId,
        userSerial = userSerial,
        userId = userId,
    )
