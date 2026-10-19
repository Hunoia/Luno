package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.ceil

/**
 * 面板里挑第三方小部件的清单（docs/grid-panel.md）。
 *
 * ## 不用系统的 ACTION_APPWIDGET_PICK
 *
 * MIUI HyperOS 上 `com.android.settings/.AppWidgetPickActivity` inflate 它自己的
 * `pick_item` 布局会抛 `UnsupportedOperationException: Failed to resolve attribute
 * … Theme.DeviceDefault.Dialog.Alert.DayNight`，崩的是系统进程，用户侧表现就是
 * 「点添加小部件毫无反应」。自己列，顺带能把预览图和占格数一起显示出来。
 */

/** 一个应用下的小部件（选择器按应用分组，参考一墨的分组方式）。 */
data class WidgetProviderGroup(
    val appLabel: String,
    val providers: List<AppWidgetProviderInfo>,
)

/** 小部件在面板网格里占几格。 */
data class WidgetSpan(val spanX: Int, val spanY: Int)

/** 桌面格子边长（dp）。AppWidget 尺寸协商的通用基准，Launcher3 与一墨都用这个值。 */
private const val StandardCellDp = 70f

/** 格子四周给内容留的内边距（dp）。Launcher3 的 widget padding 也是这个量级。 */
private const val CellPaddingDp = 30f

/**
 * 算这个小部件该占几格（一墨 `ComponentWidgetPickerOverlay` 的同一套算法）。
 *
 * 思路：**由 provider 自己声明的尺寸反推占格数**，而不是让用户去拖
 * （一墨全代码库没有 resize，它的 span 就是这么定的、之后不可改）。
 *
 * 分支：
 * - `targetCellWidth/Height`（API 31+，本项目 minSdk 33 必有）是 provider 声明的
 *   **格数**，直接用 —— 这个字段就是按「格」定义的，跨宿主可移植。
 * - 少数 provider 不填，退回 `ceil((minWidth + 30dp) / 70dp)`，再按面板列/行封顶。
 *
 * **和一墨的一处刻意偏差：分母不除 density。**
 * 一墨写的是 `(minWidth / density + 30) / 70`，但 `minWidth` 的单位本来就是 dp
 * （平台就是这么定义的），再除一次 density 等于把尺寸缩小一个密度比 —— 在 3.4x
 * 的机器上一个 2 格小部件会被算成 1 格。除掉这一步后结果才和 provider 的声明吻合。
 *
 * 分母用**标准** 70dp 而不是本面板的实际格宽：provider 是照着桌面格子声明尺寸的，
 * 「它在桌面上占几格」是它的固有属性；我们格子更小（卡片只占 72% 屏宽）时，
 * 结果就是这一项在我们网格里被压缩，而不是变成一个占满整张卡的庞然大物。
 */
fun widgetSpanFor(
    info: AppWidgetProviderInfo,
    columns: Int,
    rows: Int,
): WidgetSpan {
    val cols = columns.coerceAtLeast(1)
    val rowCount = rows.coerceAtLeast(1)
    return WidgetSpan(
        spanX = (info.targetCellWidth.takeIf { it > 0 } ?: cellsFor(info.minWidth)).coerceIn(1, cols),
        spanY = (info.targetCellHeight.takeIf { it > 0 } ?: cellsFor(info.minHeight)).coerceIn(1, rowCount),
    )
}

private fun cellsFor(minSizeDp: Int): Int =
    ceil((minSizeDp + CellPaddingDp) / StandardCellDp).toInt().coerceAtLeast(1)

/**
 * 读出设备上所有可用的小部件 provider，按应用分组。
 *
 * 走全部用户 profile（`getInstalledProvidersForProfile`）：工作资料里装的小部件在
 * 主用户下**一个都列不出来**，而它是当前用户角色的标准 API。拿不到就退回普通接口。
 *
 * 按 ComponentName 去重 —— 多 profile 遍历会把同一个 provider 收进来好几遍。
 * 分组顺序用中文 Collator，否则「微信」会排到「阿里」后面。
 */
suspend fun loadWidgetProviderGroups(context: Context): List<WidgetProviderGroup> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val manager = AppWidgetManager.getInstance(context)
        val providers = runCatching {
            val userManager = context.getSystemService(UserManager::class.java)
            userManager.userProfiles.flatMap { manager.getInstalledProvidersForProfile(it) }
        }.getOrElse { manager.installedProviders }

        val seen = HashSet<ComponentName>()
        val grouped = LinkedHashMap<String, MutableList<AppWidgetProviderInfo>>()
        for (info in providers) {
            val component = info.provider ?: continue
            if (!seen.add(component)) continue
            val pkg = component.packageName
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrElse { pkg }
            grouped.getOrPut(label) { mutableListOf() }.add(info)
        }
        val collator = Collator.getInstance(Locale.CHINA)
        grouped.entries
            .sortedWith { a, b -> collator.compare(a.key, b.key) }
            .map { (label, list) -> WidgetProviderGroup(label, list) }
    }
/**
 * 按关键字过滤分组清单，匹配应用名、小部件名、包名三处（一墨同款）。
 *
 * 放在这里而不是组合里：搜索要在 `remember` 里算，组合里直接调
 * `AppWidgetProviderInfo.loadLabel(PackageManager)` 会把 PackageManager 拖进 UI 层，
 * 而且那是一次跨进程查询（provider 可能是别的应用的）。
 */
fun List<WidgetProviderGroup>.searchBy(query: String, packageManager: PackageManager): List<WidgetProviderGroup> {
    if (query.isBlank()) return this
    return mapNotNull { group ->
        val hit = group.providers.filter { info ->
            runCatching { info.loadLabel(packageManager).toString() }
                .getOrDefault("")
                .contains(query, ignoreCase = true) ||
                group.appLabel.contains(query, ignoreCase = true) ||
                info.provider?.packageName?.contains(query, ignoreCase = true) == true
        }
        if (hit.isEmpty()) null else WidgetProviderGroup(group.appLabel, hit)
    }
}

/** 应用图标（provider 没给预览图时当锚点）。 */
fun applicationIconOf(info: AppWidgetProviderInfo, packageManager: PackageManager) =
    runCatching { packageManager.getApplicationIcon(info.provider.packageName) }.getOrNull()

/**
 * 预览图（一墨选择器的三级回落第一级）：provider 自带的 previewImage 按当前屏密
 * 解出为 Drawable；没有或解失败返 null，由调用方继续回落应用图标。
 */
fun widgetPreviewOf(info: AppWidgetProviderInfo, context: Context): Drawable? =
    runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
