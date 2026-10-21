package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserManager
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.gridpanel.geometry.GridPadding
import hunoia.luno.gridpanel.geometry.gapFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

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

/** 桌面格子边长（dp），AppWidget 尺寸协商的通用基准（Launcher3 同款）。 */
private const val StandardCellDp = 70f

/** 算占格数时补的格子内边距（dp），与 Launcher3 的 widget padding 同量级。 */
private const val CellPaddingDp = 30f

/**
 * 面板格宽（dp）。provider 声明的是 launcher 标准格（~70dp）个数，我们格子更小，
 * 默认 span 按比例缩（见 [widgetSpanFor] 的 cellWidthDp 参数）。
 */
fun panelCellWidthDp(screenWidthDp: Float, cardWidthFraction: Float, columns: Int): Float {
    // 与 GridMetrics 同源：gap 表、内边距、卡宽夹取都复用，避免选择器预览和
    // 真实网格因为两套公式而漂移。
    val cols = columns.coerceAtLeast(1)
    val gap = gapFor(cols).value
    val cardW = screenWidthDp * cardWidthFraction.coerceIn(
        GridPanelDefaults.MinCardWidthFraction,
        GridPanelDefaults.MaxCardWidthFraction,
    )
    return ((cardW - 2f * GridPadding.value - gap * (cols - 1)) / cols).coerceAtLeast(1f)
}

/**
 * 由 provider 声明的尺寸反推占格数：targetCellWidth/Height 直接用，缺失时退回
 * ceil((minWidth + 30dp) / 70dp)，再按面板列/行封顶。
 * 分母用标准 70dp 且不除 density：minWidth 本来就是 dp，再除会让 3.4x 机器上 2 格算成 1 格。
 */
fun widgetSpanFor(
    info: AppWidgetProviderInfo,
    columns: Int,
    rows: Int,
    /** 面板实际格宽（dp）；缺省按标准 70dp 算（不缩）。 */
    cellWidthDp: Float = StandardCellDp,
): WidgetSpan {
    val cols = columns.coerceAtLeast(1)
    val rowCount = rows.coerceAtLeast(1)
    val scale = (cellWidthDp / StandardCellDp).coerceIn(0.5f, 1f)
    return WidgetSpan(
        spanX = ((info.targetCellWidth.takeIf { it > 0 } ?: cellsFor(info.minWidth)) * scale)
            .roundToInt().coerceIn(1, cols),
        spanY = ((info.targetCellHeight.takeIf { it > 0 } ?: cellsFor(info.minHeight)) * scale)
            .roundToInt().coerceIn(1, rowCount),
    )
}

private fun cellsFor(minSizeDp: Int): Int =
    ceil((minSizeDp + CellPaddingDp) / StandardCellDp).toInt().coerceAtLeast(1)

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
