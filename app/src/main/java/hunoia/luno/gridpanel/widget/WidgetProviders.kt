package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.view.WindowManager
import hunoia.luno.core.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.ceil

/**
 * 面板的第三方小部件清单。不用系统的 ACTION_APPWIDGET_PICK：MIUI HyperOS 上它的
 * pick_item 布局会抛 UnsupportedOperationException（崩的是系统进程，用户侧表现
 * 就是「点添加小部件毫无反应」）；自己列还能显示预览图和占格数。
 */

/** 一个应用下的小部件（选择器按应用分组）。 */
data class WidgetProviderGroup(
    val appLabel: String,
    val providers: List<AppWidgetProviderInfo>,
)

data class WidgetSpan(val spanX: Int, val spanY: Int)

/** 标准 launcher 格宽（dp），AOSP 的 minWidth/minHeight 就是按这个单位给的。 */
private const val StandardCellDp = 70f

/** 算占格数时补的格子内边距（dp），与 Launcher3 的 widget padding 同量级。 */
private const val CellPaddingDp = 30f

/** MIUI 桌面默认列数：minWidth/minHeight（px）按这个格宽取值。 */
private const val LauncherColumns = 4

/**
 * MIUI/HyperOS 把 minWidth/minHeight 存成 px（按桌面 4 列格宽），AOSP 则是 dp。
 * 只在小米系按 px 解释，其他 ROM 保留标准 dp 换算，避免占格数整体塌成 1 格。
 */
private fun usesPixelWidgetSize(): Boolean {
    val brand = Build.BRAND.lowercase(Locale.ROOT)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return manufacturer == "xiaomi" || brand == "xiaomi" ||
        brand == "redmi" || brand == "poco"
}

/**
 * 由 provider 声明的尺寸反推占格数：targetCellWidth/Height 直接用，缺失时退回
 * min 尺寸换算。注意 MIUI/HyperOS 的 minWidth/minHeight 单位是 **px**，且按
 * MIUI 桌面默认 4 列的格宽取值（实测：4x2 部件 minW≈1024、2x1≈375、1x1≈171，
 * 屏宽 1200px → 格宽≈300px，ceil(px/300) 与真实占格全部吻合）；不能按 AOSP
 * 文档当 dp 除以标准 70dp，否则行数会翻倍（4x2 算成 4x4）。
 * 非小米 ROM 仍按 AOSP 的 dp 语义换算。
 * 选择页标签与落格占格数都要求与桌面真实占格一致，不做面板格宽的比例缩放；
 * 仅按面板列/行封顶。
 */
fun widgetSpanFor(
    info: AppWidgetProviderInfo,
    columns: Int,
    rows: Int,
): WidgetSpan {
    val cols = columns.coerceAtLeast(1)
    val rowCount = rows.coerceAtLeast(1)
    val pixelRule = usesPixelWidgetSize()
    val screenPx = if (pixelRule) currentScreenWidthPx() else 0
    return WidgetSpan(
        spanX = (info.targetCellWidth.takeIf { it > 0 }
            ?: cellsFor(info.minWidth, screenPx, pixelRule))
            .coerceIn(1, cols),
        spanY = (info.targetCellHeight.takeIf { it > 0 }
            ?: cellsFor(info.minHeight, screenPx, pixelRule))
            .coerceIn(1, rowCount),
    )
}

private fun currentScreenWidthPx(): Int = runCatching {
    AppContext.get().getSystemService(WindowManager::class.java)
        ?.currentWindowMetrics?.bounds?.width()
}.getOrNull() ?: AppContext.get().resources.displayMetrics.widthPixels

private fun cellsFor(minSize: Int, screenPx: Int, pixelRule: Boolean): Int {
    if (minSize <= 0) return 1
    val cells = if (pixelRule) {
        val cellPx = (screenPx.toFloat() / LauncherColumns).coerceAtLeast(1f)
        ceil(minSize.toFloat() / cellPx)
    } else {
        ceil((minSize + CellPaddingDp) / StandardCellDp)
    }
    return cells.toInt().coerceAtLeast(1)
}

/**
 * 读出设备上所有可用的小部件 provider，按应用分组。
 * 走全部用户 profile：工作资料里装的小部件在主用户下一个都列不出来，拿不到就退回普通接口。
 * 按 ComponentName 去重（多 profile 会把同一 provider 收好几遍）；分组用中文 Collator 排序。
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
 * 按关键字过滤分组清单，匹配应用名、小部件名、包名三处。
 * 放在这里而不是组合里：搜索在 `remember` 里算，组合里直接调 `loadLabel` 会把
 * PackageManager 拖进 UI 层，而且那是一次跨进程查询。
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

/** provider 没给预览图时的回落图标。 */
fun applicationIconOf(info: AppWidgetProviderInfo, packageManager: PackageManager) =
    runCatching { packageManager.getApplicationIcon(info.provider.packageName) }.getOrNull()

/**
 * 预览图：provider 自带的 previewImage 按当前屏密解出为 Drawable；
 * 没有或解失败返 null，由调用方继续回落应用图标。
 */
fun widgetPreviewOf(info: AppWidgetProviderInfo, context: Context): Drawable? =
    runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
