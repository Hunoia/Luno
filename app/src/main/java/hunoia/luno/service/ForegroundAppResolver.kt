package hunoia.luno.service

/**
 * 「当前应用」严格解析：只看最上层可见的 `TYPE_APPLICATION` 窗口。
 *
 * Luno 的面板/悬浮球是 `TYPE_APPLICATION_OVERLAY` / `TYPE_ACCESSIBILITY_OVERLAY`，
 * 不会进入候选；因此面板盖在别的应用上时自然取到下层应用，而 Luno 自己的
 * Activity 在前台时仍会被识别为可为小窗的目标。
 *
 * 桌面（Launcher）视为「没有可小窗的前台应用」，不再回退最近应用历史。
 */
internal object ForegroundAppResolver {

    /**
     * 系统 UI 包：无障碍窗口列表里 systemui 的 `TYPE_APPLICATION` 窗口（如 MIUI 小窗的
     * caption/控制层）层级常**高于** freeform 应用窗口，会把「当前应用」污染成 systemui。
     * 它们没有 launcher，小窗/迷你动作取到只会失败，直接跳过。
     */
    private val systemUiPackages = setOf(
        "com.android.systemui",
    )

    /** 已过滤为 `TYPE_APPLICATION && isVisibleToUser` 的窗口；[packageName] 可能因 root 未就绪为 null。 */
    data class AppWindow(
        val packageName: String?,
        val layer: Int,
    )

    /** 返回可小窗的包名；空字符串 = 无目标（桌面 / 没有应用窗口）。 */
    fun resolve(windows: List<AppWindow>, isLauncher: (String) -> Boolean): String {
        val topPackage = windows
            .asSequence()
            .sortedByDescending { it.layer }
            .mapNotNull { it.packageName?.takeIf(String::isNotBlank) }
            .firstOrNull { it !in systemUiPackages }
            ?: return ""
        return if (isLauncher(topPackage)) "" else topPackage
    }
}
