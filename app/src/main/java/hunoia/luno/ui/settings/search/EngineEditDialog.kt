package hunoia.luno.ui.settings.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.ui.component.row.LunoCardInput
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.TextInputDialog
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap

internal enum class EngineOpenMode { Default, App, Chooser, Share, ShareImage }

/**
 * 引擎编辑器状态：编辑页与「添加」共用一份字段与校验。
 * [targetPackage] 由 AppPickerSheet 回填、跨组合存活，单独由调用方持有传入。
 */
internal class EngineEditState(
    initial: SearchEngine?,
    private val existingTriggers: List<String>,
) {
    var name by mutableStateOf(initial?.name.orEmpty())
    var iconText by mutableStateOf(initial?.iconText.orEmpty())
    var template by mutableStateOf(
        run {
            val launchTemplate = when (val launch = initial?.effectiveLaunch()) {
                is SearchLaunch.Url -> launch.template
                is SearchLaunch.Chooser -> launch.template
                is SearchLaunch.Share -> launch.textTemplate
                is SearchLaunch.Action -> launch.copyTemplate
                is SearchLaunch.ShareImage -> ""
                null -> ""
            }
            launchTemplate.ifBlank { initial?.urlTemplate.orEmpty() }
        },
    )
    var trigger by mutableStateOf(initial?.trigger.orEmpty())
    var mode by mutableStateOf(
        when (val launch = initial?.effectiveLaunch()) {
            is SearchLaunch.Chooser -> EngineOpenMode.Chooser
            is SearchLaunch.Share -> EngineOpenMode.Share
            is SearchLaunch.ShareImage -> EngineOpenMode.ShareImage
            is SearchLaunch.Url ->
                if (launch.packageName.isNotBlank()) EngineOpenMode.App else EngineOpenMode.Default
            else -> EngineOpenMode.Default
        },
    )
    var fallback by mutableStateOf(
        (initial?.effectiveLaunch() as? SearchLaunch.Url)?.fallbackTemplate.orEmpty(),
    )

    /** 进页快照：脏检测用（targetPackage 由页面自行对比）。 */
    private data class InitialSnapshot(
        val name: String,
        val iconText: String,
        val template: String,
        val trigger: String,
        val mode: EngineOpenMode,
        val fallback: String,
    )

    private val initialSnapshot = InitialSnapshot(
        name = name,
        iconText = iconText,
        template = template,
        trigger = trigger,
        mode = mode,
        fallback = fallback,
    )

    fun isDirty(): Boolean = name != initialSnapshot.name ||
        iconText != initialSnapshot.iconText ||
        template != initialSnapshot.template ||
        trigger != initialSnapshot.trigger ||
        mode != initialSnapshot.mode ||
        fallback != initialSnapshot.fallback

    val isShare get() = mode == EngineOpenMode.Share
    val isShareImage get() = mode == EngineOpenMode.ShareImage
    val templateError: Boolean
        get() = !isShare && !isShareImage && template.isNotEmpty() && !template.contains("%s")
    val triggerClean: String get() = trigger.trim()

    /** 标识：空 = 不启用直达；否则不能含空白、不能与其它引擎重复（大小写不敏感）。 */
    val triggerError: Int? get() = when {
        triggerClean.any { it.isWhitespace() } -> R.string.search_engine_trigger_space
        triggerClean.isNotEmpty() &&
            existingTriggers.any { it.equals(triggerClean, ignoreCase = true) } ->
            R.string.search_engine_trigger_duplicate
        else -> null
    }

    fun targetMissing(targetPackage: String): Boolean =
        (mode == EngineOpenMode.App || isShare || isShareImage) && targetPackage.isBlank()

    val templateValid: Boolean
        get() = when {
            isShareImage -> true
            isShare -> name.isNotBlank() && template.isNotBlank()
            else -> name.isNotBlank() && template.contains("%s")
        }

    fun valid(targetPackage: String): Boolean =
        templateValid && triggerError == null && !targetMissing(targetPackage)

    fun buildLaunch(targetPackage: String): SearchLaunch = when (mode) {
        EngineOpenMode.Default -> SearchLaunch.Url(template.trim())
        EngineOpenMode.App -> SearchLaunch.Url(
            template = template.trim(),
            packageName = targetPackage,
            fallbackTemplate = fallback.trim(),
        )
        EngineOpenMode.Chooser -> SearchLaunch.Chooser(template.trim())
        EngineOpenMode.Share -> SearchLaunch.Share(
            textTemplate = template.trim(),
            packageName = targetPackage,
        )
        EngineOpenMode.ShareImage -> SearchLaunch.ShareImage(targetPackage)
    }

    /** 测试可用：非图片引擎且模板已可替换（测试按钮用固定词 Luno）。 */
    fun testable(targetPackage: String): Boolean =
        !isShareImage && !targetMissing(targetPackage) && (isShare || template.contains("%s"))

    /** 确认时输出：name/template/trigger/launch/iconText。 */
    fun result(targetPackage: String) = EngineEditResult(
        name = name.trim(),
        template = template.trim(),
        trigger = triggerClean,
        launch = buildLaunch(targetPackage),
        iconText = iconText.trim(),
    )
}

internal data class EngineEditResult(
    val name: String,
    val template: String,
    val trigger: String,
    val launch: SearchLaunch,
    val iconText: String,
)

@Composable
internal fun rememberEngineEditState(
    initial: SearchEngine?,
    existingTriggers: List<String>,
): EngineEditState = remember(initial, existingTriggers) {
    EngineEditState(initial, existingTriggers)
}

/**
 * 引擎编辑字段：分段卡片行式排版（与设置页同一套），滚动由页面容器提供。
 * [appLabel] 是 [targetPackage] 解析出的应用名，null 时目标应用行回退显示包名。
 */
@Composable
internal fun EngineEditFields(
    state: EngineEditState,
    targetPackage: String,
    appLabel: String?,
    onPickApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        // 基本信息：三行连成一张整卡（底色由外层统一铺，分割线两侧露出的也是卡色），
        // 行间用两头不接边的分割线分隔。
        LunoGroup(
            title = stringResource(R.string.search_engine_basic_group),
            contentSpacing = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ContainerRadius))
                    .background(MaterialTheme.colorScheme.surfaceBright),
            ) {
                LunoInputRow(
                    label = stringResource(R.string.search_engine_name),
                    value = state.name,
                    onValueChange = { state.name = it },
                    placeholder = stringResource(R.string.search_engine_name_hint),
                    shape = RoundedCornerShape(0.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = PageGutter))
                LunoInputRow(
                    label = stringResource(R.string.search_engine_icon_text),
                    value = state.iconText,
                    onValueChange = { state.iconText = it.take(2) },
                    placeholder = stringResource(R.string.search_engine_icon_text_hint),
                    shape = RoundedCornerShape(0.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = PageGutter))
                LunoInputRow(
                    label = stringResource(R.string.search_engine_trigger),
                    value = state.trigger,
                    onValueChange = { state.trigger = it },
                    placeholder = "bb",
                    subtitle = state.triggerError?.let { stringResource(it) }.orEmpty(),
                    isError = state.triggerError != null,
                    shape = RoundedCornerShape(0.dp),
                )
            }
        }
        OpenModeCard(state)
        if (!state.isShareImage) {
            LunoGroup(
                title = stringResource(
                    if (state.isShare) R.string.search_engine_share_template else R.string.search_engine_url,
                ),
            ) {
                LunoCardInput(
                    value = state.template,
                    onValueChange = { state.template = it },
                    placeholder = if (state.isShare) "%q" else "https://www.bing.com/search?q=%s",
                    // 分享模式常驻 %q 说明；出错时同槽位换错误文案，高度不跳。
                    supportingText = when {
                        state.templateError -> stringResource(R.string.search_engine_url_error)
                        state.isShare -> stringResource(R.string.search_engine_share_hint)
                        else -> ""
                    },
                    isError = state.templateError,
                    shape = segmentedShape(0, 1),
                )
            }
        }
        if (state.mode == EngineOpenMode.App || state.mode == EngineOpenMode.Share || state.isShareImage) {
            LunoGroup(title = stringResource(R.string.search_engine_target_app)) {
                LunoSettingsRow(
                    title = appLabel
                        ?: targetPackage.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.search_engine_target_app_hint),
                    subtitle = when {
                        state.targetMissing(targetPackage) -> stringResource(R.string.search_engine_target_required)
                        appLabel != null -> targetPackage
                        else -> ""
                    },
                    subtitleColor = if (state.targetMissing(targetPackage)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = onPickApp,
                    shape = segmentedShape(0, 1),
                )
            }
            if (state.isShareImage) {
                Text(
                    text = stringResource(R.string.search_engine_share_image_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PageGutter),
                )
            }
            if (state.mode == EngineOpenMode.App) {
                LunoGroup(title = stringResource(R.string.search_engine_fallback)) {
                    LunoCardInput(
                        value = state.fallback,
                        onValueChange = { state.fallback = it },
                        placeholder = stringResource(R.string.search_engine_fallback_hint),
                        shape = segmentedShape(0, 1),
                    )
                }
            }
        }
    }
}

/** 打开方式可展开卡：头部显示当前模式，展开列全部分模式（观感同手势按钮的动作卡）。 */
@Composable
private fun OpenModeCard(state: EngineEditState) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(
        Triple(EngineOpenMode.Default, R.string.search_engine_open_default, R.string.search_engine_open_default_desc),
        Triple(EngineOpenMode.App, R.string.search_engine_open_app, R.string.search_engine_open_app_desc),
        Triple(EngineOpenMode.Chooser, R.string.search_engine_open_chooser, R.string.search_engine_open_chooser_desc),
        Triple(EngineOpenMode.Share, R.string.search_engine_open_share, R.string.search_engine_open_share_desc),
        Triple(
            EngineOpenMode.ShareImage,
            R.string.search_engine_open_share_image,
            R.string.search_engine_open_share_image_desc,
        ),
    )
    val current = modes.first { it.first == state.mode }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "engineModeArrowRotation")
    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else ContainerRadius,
        label = "engineModeHeaderRadius",
    )
    val headerShape = RoundedCornerShape(
        topStart = ContainerRadius,
        topEnd = ContainerRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )
    val contentShape = RoundedCornerShape(
        topStart = ConnectionRadius,
        topEnd = ConnectionRadius,
        bottomStart = ContainerRadius,
        bottomEnd = ContainerRadius,
    )
    LunoGroup(title = stringResource(R.string.search_engine_open_mode)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ContainerRadius)),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            LunoSettingsRow(
                title = stringResource(current.second),
                subtitle = stringResource(current.third),
                onClick = { expanded = !expanded },
                shape = headerShape,
                trailing = {
                    Icon(
                        modifier = Modifier
                            .size(RowIconSize)
                            .graphicsLayer { rotationZ = rotation },
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(contentShape),
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap),
                ) {
                    modes.forEachIndexed { index, (mode, labelRes, descRes) ->
                        val bottom = if (index == modes.lastIndex) ContainerRadius else ConnectionRadius
                        LunoSelectableRow(
                            title = stringResource(labelRes),
                            subtitle = stringResource(descRes),
                            selected = state.mode == mode,
                            onSelectedChange = {
                                state.mode = mode
                                expanded = false
                            },
                            singleSelect = true,
                            shape = RoundedCornerShape(
                                topStart = ConnectionRadius,
                                topEnd = ConnectionRadius,
                                bottomStart = bottom,
                                bottomEnd = bottom,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 来源标识编辑：空 = 关闭该源；不能含空白，不能撞引擎或其他来源；不合法时确认键禁用。
 */
@Composable
internal fun SourceTriggerDialog(
    title: String,
    current: String,
    existingTriggers: List<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var trigger by remember(current) { mutableStateOf(current) }
    val triggerClean = trigger.trim().lowercase()
    val triggerError: Int? = when {
        triggerClean.any { it.isWhitespace() } -> R.string.search_engine_trigger_space
        triggerClean.isNotEmpty() && existingTriggers.any { it.equals(triggerClean, ignoreCase = true) } ->
            R.string.search_engine_trigger_duplicate
        else -> null
    }
    TextInputDialog(
        title = title,
        label = stringResource(R.string.search_engine_trigger),
        initialValue = current,
        isError = triggerError != null,
        supportingText = triggerError?.let { stringResource(it) }
            ?: stringResource(R.string.quick_search_trigger_off),
        confirmEnabled = triggerError == null,
        onDismissRequest = onDismissRequest,
        onConfirm = { onConfirm(it.trim().lowercase()) },
    )
}
