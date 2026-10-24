package hunoia.luno.ui.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.quicklaunch.launchSearchAction
import hunoia.luno.quicklaunch.query.toSearchAction
import hunoia.luno.ui.component.sheet.TextInputDialog
import hunoia.luno.ui.theme.CardInnerSpacing

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
 * 引擎编辑字段（无滚动容器、无确认按钮）：编辑页整体排版，滚动由页面容器提供。
 */
@Composable
internal fun EngineEditFields(
    state: EngineEditState,
    targetPackage: String,
    onPickApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 测试用固定词：能直观看出 %s / %q 是否被正确替换。
    val testQuery = "Luno"
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_engine_name)) },
        )
        OutlinedTextField(
            value = state.iconText,
            onValueChange = { state.iconText = it.take(2) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_engine_icon_text)) },
            placeholder = { Text(stringResource(R.string.search_engine_icon_text_hint)) },
        )
        if (!state.isShareImage) {
            OutlinedTextField(
                value = state.template,
                onValueChange = { state.template = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        stringResource(
                            if (state.isShare) R.string.search_engine_share_template else R.string.search_engine_url,
                        ),
                    )
                },
                placeholder = {
                    Text(
                        if (state.isShare) "%q" else "https://www.bing.com/search?q=%s",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                isError = state.templateError,
                // supportingText 常驻占位：无错时空字符串，避免错误出现/消失时弹窗高度跳动。
                supportingText = {
                    Text(
                        text = if (state.templateError) {
                            stringResource(R.string.search_engine_url_error)
                        } else {
                            ""
                        },
                    )
                },
            )
        }
        OutlinedTextField(
            value = state.trigger,
            onValueChange = { state.trigger = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_engine_trigger)) },
            placeholder = { Text("bb") },
            isError = state.triggerError != null,
            supportingText = {
                Text(text = state.triggerError?.let { stringResource(it) } ?: "")
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_engine_open_mode),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
            TextButton(
                enabled = state.testable(targetPackage),
                onClick = {
                    val action = state.buildLaunch(targetPackage).toSearchAction(testQuery)
                    if (!launchSearchAction(context, action)) showToast(R.string.launch_no_handler)
                },
            ) {
                Text(stringResource(R.string.search_engine_test))
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            FilterChip(
                selected = state.mode == EngineOpenMode.Default,
                onClick = { state.mode = EngineOpenMode.Default },
                label = { Text(stringResource(R.string.search_engine_open_default)) },
            )
            FilterChip(
                selected = state.mode == EngineOpenMode.App,
                onClick = { state.mode = EngineOpenMode.App },
                label = { Text(stringResource(R.string.search_engine_open_app)) },
            )
            FilterChip(
                selected = state.mode == EngineOpenMode.Chooser,
                onClick = { state.mode = EngineOpenMode.Chooser },
                label = { Text(stringResource(R.string.search_engine_open_chooser)) },
            )
            FilterChip(
                selected = state.mode == EngineOpenMode.Share,
                onClick = { state.mode = EngineOpenMode.Share },
                label = { Text(stringResource(R.string.search_engine_open_share)) },
            )
            FilterChip(
                selected = state.mode == EngineOpenMode.ShareImage,
                onClick = { state.mode = EngineOpenMode.ShareImage },
                label = { Text(stringResource(R.string.search_engine_open_share_image)) },
            )
        }
        if (state.mode == EngineOpenMode.App || state.mode == EngineOpenMode.Share || state.isShareImage) {
            OutlinedTextField(
                value = targetPackage,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search_engine_target_app)) },
                placeholder = { Text(stringResource(R.string.search_engine_target_app_hint)) },
                trailingIcon = {
                    TextButton(onClick = onPickApp) {
                        Text(stringResource(R.string.search_engine_pick_app))
                    }
                },
                isError = state.targetMissing(targetPackage),
                supportingText = {
                    Text(
                        if (state.targetMissing(targetPackage)) {
                            stringResource(R.string.search_engine_target_required)
                        } else {
                            ""
                        },
                    )
                },
            )
            if (state.mode == EngineOpenMode.App) {
                OutlinedTextField(
                    value = state.fallback,
                    onValueChange = { state.fallback = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_engine_fallback)) },
                    placeholder = { Text(stringResource(R.string.search_engine_fallback_hint)) },
                )
            }
        }
        if (state.mode == EngineOpenMode.Share) {
            Text(
                text = stringResource(R.string.search_engine_share_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isShareImage) {
            Text(
                text = stringResource(R.string.search_engine_share_image_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
