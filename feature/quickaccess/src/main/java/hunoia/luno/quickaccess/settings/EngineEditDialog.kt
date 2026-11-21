package hunoia.luno.quickaccess.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.EngineContentMode
import hunoia.luno.config.model.EngineIconType
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.quickaccess.R
import hunoia.luno.ui.component.row.LunoCardInput
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.groupItemClip
import hunoia.luno.ui.component.row.groupItemSpacing
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.TextInputDialog
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.containerShape

internal enum class EngineOpenMode { Default, App, Chooser, Share, ShareImage, Activity }

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
                is SearchLaunch.Activity -> ""
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
        targetActivity != initialSnapshot.targetActivity ||
        externLink != initialSnapshot.externLink ||
        autoInputEnter != initialSnapshot.autoInputEnter

    val isShare get() = mode == EngineOpenMode.Share
    val isShareImage get() = mode == EngineOpenMode.ShareImage
    val isActivity get() = mode == EngineOpenMode.Activity
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
        templateValid && triggerError == null && !targetMissing(targetPackage) && !imageTargetMissing

    fun buildLaunch(targetPackage: String): SearchLaunch = when (mode) {
        EngineOpenMode.Default -> SearchLaunch.Url(template.trim(), useCustomTabs = useCustomTabs)
        EngineOpenMode.App -> SearchLaunch.Url(
            template = template.trim(),
            packageName = targetPackage,
            fallbackTemplate = fallback.trim(),
            useCustomTabs = useCustomTabs,
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
    existingTriggers: List<String>,
): EngineEditState = remember(initial, existingTriggers) {
    EngineEditState(initial, existingTriggers)
}

/**
 * 引擎编辑字段：分段卡片行式排版（与设置页同一套），滚动由页面容器提供。
 * [appLabel] 是 [targetPackage] 解析出的应用名，null 时目标应用行回退显示包名；
 * [imageAppLabel] / [onPickImageApp] 是图片双模式目标应用的解析名与选择入口。
 */
@Composable
internal fun EngineEditFields(
    state: EngineEditState,
    targetPackage: String,
    appLabel: String?,
    onPickApp: () -> Unit,
    imageAppLabel: String?,
    onPickImageApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        // 基本信息：三行同属一组，分段风格是三个圆角块，大卡风格是一张卡 + 行间分割线。
        LunoGroup(title = stringResource(R.string.search_engine_basic_group)) {
            LunoInputRow(
                label = stringResource(R.string.search_engine_name),
                value = state.name,
                onValueChange = { state.name = it },
                placeholder = stringResource(R.string.search_engine_name_hint),
                shape = segmentedShape(0, 3),
            )
            LunoInputRow(
                label = stringResource(R.string.search_engine_icon_text),
                value = state.iconText,
                onValueChange = { state.iconText = it.take(2) },
                placeholder = stringResource(R.string.search_engine_icon_text_hint),
                shape = segmentedShape(1, 3),
            )
            LunoInputRow(
                label = stringResource(R.string.search_engine_trigger),
                value = state.trigger,
                onValueChange = { state.trigger = it },
                placeholder = "bb",
                subtitle = state.triggerError?.let { stringResource(it) }.orEmpty(),
                isError = state.triggerError != null,
                shape = segmentedShape(2, 3),
            )
        }
        EngineIconFields(state)
        OpenModeCard(state)
        if (!state.isShareImage && !state.isActivity) {
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
        // Custom Tabs 打开：仅 Default（无指定包）。App 模式指定了包名，
        // Custom Tabs 只会拉起默认浏览器，两者互斥，所以不在 App 模式展示。
        if (state.mode == EngineOpenMode.Default) {
            LunoGroup(title = stringResource(R.string.search_engine_url_options)) {
                LunoSwitchRow(
                    title = stringResource(R.string.search_engine_custom_tabs),
                    subtitle = stringResource(R.string.search_engine_custom_tabs_desc),
                    checked = state.useCustomTabs,
                    onCheckedChange = { state.useCustomTabs = it },
                    shape = segmentedShape(0, 1),
                )
            }
        }
        if (state.mode == EngineOpenMode.App || state.mode == EngineOpenMode.Share || state.isShareImage || state.isActivity) {
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
            if (state.isActivity) {
                LunoGroup(title = stringResource(R.string.search_engine_activity_target)) {
                    LunoInputRow(
                        label = stringResource(R.string.search_engine_target_activity),
                        value = state.targetActivity,
                        onValueChange = { state.targetActivity = it },
                        placeholder = stringResource(R.string.search_engine_target_activity_hint),
                        shape = segmentedShape(0, 2),
                    )
                    LunoInputRow(
                        label = stringResource(R.string.search_engine_extern_link),
                        value = state.externLink,
                        onValueChange = { state.externLink = it },
                        placeholder = "https://…",
                        subtitle = if (state.activityTargetMissing) {
                            stringResource(R.string.search_engine_activity_required)
                        } else {
                            ""
                        },
                        isError = state.activityTargetMissing,
                        shape = segmentedShape(1, 2),
                    )
                }
                LunoGroup(title = stringResource(R.string.search_engine_activity_options)) {
                    LunoSwitchRow(
                        title = stringResource(R.string.search_engine_auto_enter),
                        subtitle = stringResource(R.string.search_engine_auto_enter_desc),
                        checked = state.autoInputEnter,
                        onCheckedChange = { state.autoInputEnter = it },
                        shape = segmentedShape(0, 1),
                    )
                }
            }
        }
        // 图片双模式：纯图片引擎（ShareImage）本身就是分享图片，不再重复展示。
        if (!state.isShareImage) EngineImageFields(state, imageAppLabel, onPickImageApp)
    }
}

/** 引擎图标：类型（文字/应用/图片 URI）+ 来源。参考 GestureEVO IconType。 */
@Composable
private fun EngineIconFields(state: EngineEditState) {
    val types = listOf(
        Triple(EngineIconType.Text, R.string.search_engine_icon_type_text, R.string.search_engine_icon_type_text_desc),
        Triple(EngineIconType.App, R.string.search_engine_icon_type_app, R.string.search_engine_icon_type_app_desc),
        Triple(EngineIconType.Image, R.string.search_engine_icon_type_image, R.string.search_engine_icon_type_image_desc),
    )
    LunoGroup(title = stringResource(R.string.search_engine_icon_group)) {
        Column(
            modifier = Modifier.fillMaxWidth().groupItemClip(
                containerShape(
                    topStart = ContainerRadius,
                    topEnd = ContainerRadius,
                    bottomStart = ContainerRadius,
                    bottomEnd = ContainerRadius,
                ),
            ),
            verticalArrangement = Arrangement.spacedBy(groupItemSpacing()),
        ) {
            types.forEachIndexed { index, (type, labelRes, descRes) ->
                val bottom = if (index == types.lastIndex) ContainerRadius else ConnectionRadius
                LunoSelectableRow(
                    title = stringResource(labelRes),
                    subtitle = stringResource(descRes),
                    selected = state.iconType == type,
                    onSelectedChange = { state.iconType = type },
                    singleSelect = true,
                    shape = containerShape(
                        topStart = ConnectionRadius,
                        topEnd = ConnectionRadius,
                        bottomStart = bottom,
                        bottomEnd = bottom,
                    ),
                )
            }
        }
    }
    when (state.iconType) {
        EngineIconType.Text -> Unit
        EngineIconType.App -> LunoGroup(title = stringResource(R.string.search_engine_icon_app_source)) {
            LunoInputRow(
                label = stringResource(R.string.search_engine_icon_package),
                value = state.icon,
                onValueChange = { state.icon = it },
                placeholder = "com.example.app",
                shape = segmentedShape(0, 1),
            )
        }
        EngineIconType.Image -> LunoGroup(title = stringResource(R.string.search_engine_icon_image_source)) {
            LunoInputRow(
                label = stringResource(R.string.search_engine_icon_uri),
                value = state.icon,
                onValueChange = { state.icon = it },
                placeholder = "content://… / file://…",
                shape = segmentedShape(0, 1),
            )
        }
    }
}

/** 图片双模式：引擎在文字搜索之外支持把图片分享给目标应用。 */
@Composable
private fun EngineImageFields(
    state: EngineEditState,
    imageAppLabel: String?,
    onPickImageApp: () -> Unit,
) {
    LunoGroup(title = stringResource(R.string.search_engine_image_group)) {
        LunoSwitchRow(
            title = stringResource(R.string.search_engine_image_dual),
            subtitle = stringResource(R.string.search_engine_image_dual_desc),
            checked = state.imageShareEnabled,
            onCheckedChange = { state.imageShareEnabled = it },
            shape = segmentedShape(0, 1),
        )
    }
    if (!state.imageShareEnabled) return
    LunoGroup(title = stringResource(R.string.search_engine_image_target)) {
        LunoSettingsRow(
            title = imageAppLabel
                ?: state.imageTargetPackage.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.search_engine_target_app_hint),
            subtitle = when {
                state.imageTargetMissing -> stringResource(R.string.search_engine_target_required)
                else -> state.imageTargetPackage
            },
            subtitleColor = if (state.imageTargetMissing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = onPickImageApp,
            shape = segmentedShape(0, 1),
        )
    }
    // 内容取向：文字 / 图片 / 每次询问。
    val modes = listOf(
        Triple(EngineContentMode.Text, R.string.search_engine_content_text, R.string.search_engine_content_text_desc),
        Triple(EngineContentMode.Image, R.string.search_engine_content_image, R.string.search_engine_content_image_desc),
        Triple(EngineContentMode.Ask, R.string.search_engine_content_ask, R.string.search_engine_content_ask_desc),
    )
    LunoGroup(title = stringResource(R.string.search_engine_content_mode)) {
        Column(
            modifier = Modifier.fillMaxWidth().groupItemClip(
                containerShape(
                    topStart = ContainerRadius,
                    topEnd = ContainerRadius,
                    bottomStart = ContainerRadius,
                    bottomEnd = ContainerRadius,
                ),
            ),
            verticalArrangement = Arrangement.spacedBy(groupItemSpacing()),
        ) {
            modes.forEachIndexed { index, (mode, labelRes, descRes) ->
                val bottom = if (index == modes.lastIndex) ContainerRadius else ConnectionRadius
                LunoSelectableRow(
                    title = stringResource(labelRes),
                    subtitle = stringResource(descRes),
                    selected = state.contentMode == mode,
                    onSelectedChange = { state.contentMode = mode },
                    singleSelect = true,
                    shape = containerShape(
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
        Triple(
            EngineOpenMode.Activity,
            R.string.search_engine_open_activity,
            R.string.search_engine_open_activity_desc,
        ),
    )
    val current = modes.first { it.first == state.mode }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "engineModeArrowRotation")
    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else ContainerRadius,
        label = "engineModeHeaderRadius",
    )
    val headerShape = containerShape(
        topStart = ContainerRadius,
        topEnd = ContainerRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )
    val contentShape = containerShape(
        topStart = ConnectionRadius,
        topEnd = ConnectionRadius,
        bottomStart = ContainerRadius,
        bottomEnd = ContainerRadius,
    )
    val spacing = groupItemSpacing()
    LunoGroup(title = stringResource(R.string.search_engine_open_mode)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .groupItemClip(
                    containerShape(
                        topStart = ContainerRadius,
                        topEnd = ContainerRadius,
                        bottomStart = ContainerRadius,
                        bottomEnd = ContainerRadius,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(spacing),
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
                        .groupItemClip(contentShape),
                    verticalArrangement = Arrangement.spacedBy(spacing),
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
                            shape = containerShape(
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
