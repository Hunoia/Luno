package hunoia.luno.quickaccess.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.EngineContentMode
import hunoia.luno.config.model.EngineIconType
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.quickaccess.R

internal enum class EngineOpenMode { Default, App, Chooser, Share, ShareImage, Activity }

/** 引擎的可编辑模板：各打开方式共用的那份（无模板的方式回退旧 urlTemplate / 空串）。 */
private fun SearchEngine.editableTemplate(): String {
    val launchTemplate = when (val launch = effectiveLaunch()) {
        is SearchLaunch.Url -> launch.template
        is SearchLaunch.Chooser -> launch.template
        is SearchLaunch.Share -> launch.textTemplate
        is SearchLaunch.Action -> launch.copyTemplate
        is SearchLaunch.ShareImage -> ""
        is SearchLaunch.Activity -> ""
        null -> ""
    }
    return launchTemplate.ifBlank { urlTemplate }
}

/** 标识撞了谁：报错要指名道姓，用户才知道去哪改。 */
internal sealed interface TriggerConflict {
    /** 与其它引擎撞标识：值 = 对方引擎名。 */
    data class Engine(val name: String) : TriggerConflict

    /** 与来源标识撞：值 = 来源 key（UI 用 SearchSourceRegistry 翻译成来源标题）。 */
    data class Source(val key: String) : TriggerConflict
}

/**
 * 引擎编辑器状态：编辑页与「添加」共用一份字段与校验。
 * [existing] 是其它引擎（编辑时已排除自己），标识判重与模板判重都用它；
 * [sourceTriggers] 是来源标识（lowercase 标识 → 来源 key）——引擎标识撞来源标识会
 * 静默顶掉那个来源的直达入口，必须当场拦下。
 * [targetPackage] 由 AppPickerSheet 回填、跨组合存活，单独由调用方持有传入。
 */
internal class EngineEditState(
    initial: SearchEngine?,
    private val existing: List<SearchEngine>,
    private val sourceTriggers: Map<String, String>,
) {
    var name by mutableStateOf(initial?.name.orEmpty())
    var iconText by mutableStateOf(initial?.iconText.orEmpty())
    var template by mutableStateOf(initial?.editableTemplate().orEmpty())
    var trigger by mutableStateOf(initial?.trigger.orEmpty())
    var mode by mutableStateOf(
        when (val launch = initial?.effectiveLaunch()) {
            is SearchLaunch.Chooser -> EngineOpenMode.Chooser
            is SearchLaunch.Share -> EngineOpenMode.Share
            is SearchLaunch.ShareImage -> EngineOpenMode.ShareImage
            is SearchLaunch.Activity -> EngineOpenMode.Activity
            is SearchLaunch.Url ->
                if (launch.packageName.isNotBlank()) EngineOpenMode.App else EngineOpenMode.Default
            else -> EngineOpenMode.Default
        },
    )
    var fallback by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Url)?.fallbackTemplate.orEmpty(),
    )
    // 引擎图标（参考 GestureEVO IconType）。
    var iconType by mutableStateOf(initial?.iconType ?: EngineIconType.Text)
    var icon by mutableStateOf(initial?.icon.orEmpty())
    // 图片双模式（参考 GestureEVO imageShareEnabled / imageTargetPackage / contentMode）。
    var imageShareEnabled by mutableStateOf(initial?.imageShareEnabled ?: false)
    var imageTargetPackage by mutableStateOf(initial?.imageTargetPackage.orEmpty())
    var contentMode by mutableStateOf(initial?.contentMode ?: EngineContentMode.Text)
    // URL 打开方式：Custom Tabs（参考 GestureEVO useCustomTabs）。
    var useCustomTabs by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Url)?.useCustomTabs ?: false,
    )
    // URL 打开方式：Luno 小窗卡片（优先级高于 Custom Tabs）。
    var useSmallWindow by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Url)?.useSmallWindow ?: false,
    )
    // 跳转并注入查询词（参考 GestureEVO JUMP_TO_ACTIVITY / EXTERN_JUMP_LINK + autoInputEnter）。
    var targetActivity by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Activity)?.targetActivity.orEmpty(),
    )
    var externLink by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Activity)?.externLink.orEmpty(),
    )
    var autoInputEnter by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Activity)?.autoInputEnter ?: false,
    )

    /** 进页快照：脏检测用（targetPackage 由页面自行对比）。 */
    private data class InitialSnapshot(
        val name: String,
        val iconText: String,
        val template: String,
        val trigger: String,
        val mode: EngineOpenMode,
        val fallback: String,
        val iconType: EngineIconType,
        val icon: String,
        val imageShareEnabled: Boolean,
        val imageTargetPackage: String,
        val contentMode: EngineContentMode,
        val useCustomTabs: Boolean,
        val useSmallWindow: Boolean,
        val targetActivity: String,
        val externLink: String,
        val autoInputEnter: Boolean,
    )

    private val initialSnapshot = InitialSnapshot(
        name = name,
        iconText = iconText,
        template = template,
        trigger = trigger,
        mode = mode,
        fallback = fallback,
        iconType = iconType,
        icon = icon,
        imageShareEnabled = imageShareEnabled,
        imageTargetPackage = imageTargetPackage,
        contentMode = contentMode,
        useCustomTabs = useCustomTabs,
        useSmallWindow = useSmallWindow,
        targetActivity = targetActivity,
        externLink = externLink,
        autoInputEnter = autoInputEnter,
    )

    fun isDirty(): Boolean = name != initialSnapshot.name ||
        iconText != initialSnapshot.iconText ||
        template != initialSnapshot.template ||
        trigger != initialSnapshot.trigger ||
        mode != initialSnapshot.mode ||
        fallback != initialSnapshot.fallback ||
        iconType != initialSnapshot.iconType ||
        icon != initialSnapshot.icon ||
        imageShareEnabled != initialSnapshot.imageShareEnabled ||
        imageTargetPackage != initialSnapshot.imageTargetPackage ||
        contentMode != initialSnapshot.contentMode ||
        useCustomTabs != initialSnapshot.useCustomTabs ||
        useSmallWindow != initialSnapshot.useSmallWindow ||
        targetActivity != initialSnapshot.targetActivity ||
        externLink != initialSnapshot.externLink ||
        autoInputEnter != initialSnapshot.autoInputEnter

    val isShare get() = mode == EngineOpenMode.Share
    val isShareImage get() = mode == EngineOpenMode.ShareImage
    val isActivity get() = mode == EngineOpenMode.Activity
    val templateError: Boolean
        get() = !isShare && !isShareImage && template.isNotEmpty() && !template.contains("%s")
    val triggerClean: String get() = trigger.trim()

    /** 标识格式错误：空 = 没问题。撞车单独给 [triggerConflict]，能指名道姓。 */
    val triggerError: Int?
        get() = if (triggerClean.any { it.isWhitespace() }) R.string.search_engine_trigger_space else null

    /** 标识撞了谁：null = 没撞。撞引擎给对方名字，撞来源给来源 key。 */
    val triggerConflict: TriggerConflict?
        get() = when {
            triggerClean.isEmpty() || triggerError != null -> null
            else -> existing.firstOrNull { it.trigger.trim().equals(triggerClean, ignoreCase = true) }
                ?.let { TriggerConflict.Engine(it.name) }
                ?: sourceTriggers[triggerClean.lowercase()]?.let { TriggerConflict.Source(it) }
        }

    /** 模板撞车：与其它引擎的可编辑模板相同（同一 URL 存两份引擎只会打架）。 */
    val templateDuplicate: Boolean
        get() = !isShareImage && !isActivity &&
            template.isNotBlank() &&
            existing.any { it.editableTemplate().trim() == template.trim() }

    fun targetMissing(targetPackage: String): Boolean =
        (mode == EngineOpenMode.App || isShare || isShareImage || isActivity) && targetPackage.isBlank()

    /** 跳转模式要求目标 Activity 或外部链接至少给一个。 */
    val activityTargetMissing: Boolean
        get() = isActivity && targetActivity.isBlank() && externLink.isBlank()

    /** 图片双模式开启时必须选了图片目标应用。 */
    val imageTargetMissing: Boolean
        get() = imageShareEnabled && imageTargetPackage.isBlank()

    val templateValid: Boolean
        get() = when {
            isShareImage -> true
            isActivity -> !activityTargetMissing
            isShare -> name.isNotBlank() && template.isNotBlank()
            else -> name.isNotBlank() && template.contains("%s")
        }

    fun valid(targetPackage: String): Boolean =
        templateValid && triggerError == null && triggerConflict == null && !templateDuplicate &&
            !targetMissing(targetPackage) && !imageTargetMissing

    fun buildLaunch(targetPackage: String): SearchLaunch = when (mode) {
        EngineOpenMode.Default -> SearchLaunch.Url(
            template = template.trim(),
            useCustomTabs = useCustomTabs,
            useSmallWindow = useSmallWindow,
        )
        EngineOpenMode.App -> SearchLaunch.Url(
            template = template.trim(),
            packageName = targetPackage,
            fallbackTemplate = fallback.trim(),
            useCustomTabs = useCustomTabs,
            useSmallWindow = useSmallWindow,
        )
        EngineOpenMode.Chooser -> SearchLaunch.Chooser(template.trim())
        EngineOpenMode.Share -> SearchLaunch.Share(
            textTemplate = template.trim(),
            packageName = targetPackage,
        )
        EngineOpenMode.ShareImage -> SearchLaunch.ShareImage(targetPackage)
        EngineOpenMode.Activity -> SearchLaunch.Activity(
            targetPackage = targetPackage,
            targetActivity = targetActivity.trim(),
            externLink = externLink.trim(),
            autoInputEnter = autoInputEnter,
        )
    }

    /** 测试可用：非图片引擎且模板已可替换（测试按钮用固定词 Luno）。 */
    fun testable(targetPackage: String): Boolean =
        !isShareImage && !targetMissing(targetPackage) &&
            (isShare || isActivity || template.contains("%s"))

    /** 确认时输出：name/template/trigger/launch/iconText + 图标/图片双模式字段。 */
    fun result(targetPackage: String) = EngineEditResult(
        name = name.trim(),
        template = template.trim(),
        trigger = triggerClean,
        launch = buildLaunch(targetPackage),
        iconText = iconText.trim(),
        iconType = iconType,
        icon = icon.trim(),
        imageShareEnabled = imageShareEnabled,
        imageTargetPackage = imageTargetPackage.trim(),
        // 双模式关闭时 contentMode 无意义：强制回 Text，避免存下「Image/Ask 但没目标应用」的矛盾态。
        contentMode = if (imageShareEnabled) contentMode else EngineContentMode.Text,
    )
}

internal data class EngineEditResult(
    val name: String,
    val template: String,
    val trigger: String,
    val launch: SearchLaunch,
    val iconText: String,
    val iconType: EngineIconType,
    val icon: String,
    val imageShareEnabled: Boolean,
    val imageTargetPackage: String,
    val contentMode: EngineContentMode,
)

@Composable
internal fun rememberEngineEditState(
    initial: SearchEngine?,
    existing: List<SearchEngine>,
    sourceTriggers: Map<String, String>,
): EngineEditState = remember(initial, existing, sourceTriggers) {
    EngineEditState(initial, existing, sourceTriggers)
}
