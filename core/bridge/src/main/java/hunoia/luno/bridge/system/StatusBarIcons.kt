package hunoia.luno.bridge.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 状态栏图标隐藏的读写口：`Settings.Secure.icon_blacklist`（逗号分隔的 slot 名）。
 *
 * SystemUI 的 `StatusBarIconController` 按 slot 名过滤系统图标区，写的是系统设置，
 * 因此真相在系统侧，这里不缓存、不做降级。写入需要 `WRITE_SECURE_SETTINGS`，
 * 没有权限时 [write] 返回 false，不抛异常。
 */
object StatusBarIcons {

    /** `Settings.Secure.ICON_BLACKLIST` 是 @hide 常量，这里写字面量。 */
    private const val ICON_BLACKLIST = "icon_blacklist"

    /** SystemUI Tuner 入口（HyperOS 上是「系统界面调节工具」）。 */
    private val TUNER_COMPONENT =
        ComponentName("com.android.systemui", "com.android.systemui.DemoMode")

    fun read(context: Context): Set<String> = runCatching {
        Settings.Secure.getString(context.contentResolver, ICON_BLACKLIST)
    }.getOrNull()
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()

    /** 写入隐藏列表（空集合写空串 = 全部显示）；无权限返回 false。 */
    fun write(context: Context, slots: Collection<String>): Boolean = runCatching {
        Settings.Secure.putString(
            context.contentResolver,
            ICON_BLACKLIST,
            slots.joinToString(","),
        )
    }.getOrDefault(false)

    /** 系统调节工具入口；组件解析不到时返回 null。 */
    fun tunerIntent(context: Context): Intent? {
        val intent = Intent()
            .setComponent(TUNER_COMPONENT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}
