package hunoia.luno.floatball.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.SuggestionRule
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.suggestion.SuggestionRulePresets
import hunoia.luno.ui.component.DragReorderHandle
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.dragReorderPlacement
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.rememberDragReorderState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import kotlinx.coroutines.launch

/**
 * 建议胶囊设置页：显示/跟手/停留时长/队列 + 自定义识别规则。
 *
 * 规则表按顺序匹配（第一条命中优先展示）。行级进入编辑页，新增走右下角 FAB，
 * 预设一键并入（按正则判重）。
 */
@Composable
fun SuggestionCapsuleSettingsScreen(
    onBack: () -> Unit,
    onEditRule: (String) -> Unit = {},
    onCreateRule: () -> Unit = {},
    onNavToLinkDispatch: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val floatBall = actionSettings.floatBall
    val clipball = floatBall.clipboardBall

    var localAutoHide by remember(clipball.autoHideMs) {
        mutableFloatStateOf(clipball.autoHideMs.toFloat())
    }
    var localTtl by remember(floatBall.inbox.clipboardTtlMs) {
        mutableFloatStateOf(floatBall.inbox.clipboardTtlMs.toFloat())
    }
    var localCapacity by remember(floatBall.inbox.suggestionCapacity, floatBall.inbox.capacity) {
        mutableFloatStateOf((floatBall.inbox.suggestionCapacity ?: floatBall.inbox.capacity).toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.canDrawOverlays()
                if (granted != overlayGranted) {
                    overlayGranted = granted
                    facades.system.refreshFloatBall(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun update(transform: (FloatBallSettings) -> FloatBallSettings) {
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(floatBall = transform(current.floatBall))
            }
        }
    }

    // 规则顺序就是建议顺序：拖完松手再落盘，拖动中只改本地草稿。
    val reorder = rememberDragReorderState<SuggestionRule>(
        current = { clipball.rules },
        onCommit = { rules -> update { it.copy(clipboardBall = it.clipboardBall.copy(rules = rules)) } },
    )
    LaunchedEffect(clipball.rules) { reorder.syncWithCurrent() }
    val rules = reorder.items
    val addable = remember(clipball.rules) { SuggestionRulePresets.addable(clipball.rules) }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_suggestion_group),
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = onCreateRule,
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.float_ball_suggestion_rule_add),
            )
        },
    ) {
        if (!overlayGranted) {
            item(key = "permission") {
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_permission_required),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        shape = segmentedShape(0, 1),
                        onClick = {
                            context.gotoOverlaySettings()
                            overlayGranted = context.canDrawOverlays()
                        },
                    )
                }
            }
        }

        item(key = "behavior") {
            LunoGroup(title = stringResource(R.string.float_ball_suggestion_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_suggestion_enabled),
                    subtitle = stringResource(R.string.float_ball_suggestion_enabled_subtitle),
                    checked = clipball.enabled,
                    onCheckedChange = { checked ->
                        update { it.copy(clipboardBall = it.clipboardBall.copy(enabled = checked)) }
                        facades.system.setFloatBallEnabled(context, checked)
                    },
                    shape = segmentedShape(0, 5),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_suggestion_follow_hand),
                    subtitle = stringResource(R.string.float_ball_suggestion_follow_hand_subtitle),
                    checked = clipball.followHand,
                    onCheckedChange = { checked ->
                        update {
                            it.copy(clipboardBall = it.clipboardBall.copy(followHand = checked))
                        }
                    },
                    shape = segmentedShape(1, 5),
                )
                LunoTextSlider(
                    value = localAutoHide,
                    onValueChange = { value -> localAutoHide = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                clipboardBall = it.clipboardBall.copy(
                                    autoHideMs = localAutoHide.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_auto_hide),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.ClipboardBallAutoHideMsMin.toFloat()..
                        FloatBallDefaults.ClipboardBallAutoHideMsMax.toFloat(),
                    shape = segmentedShape(2, 5),
                )
                LunoTextSlider(
                    value = localTtl,
                    onValueChange = { value -> localTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(clipboardTtlMs = localTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_suggestion_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxSuggestionTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxSuggestionTtlMsMax.toFloat(),
                    shape = segmentedShape(3, 5),
                )
                LunoTextSlider(
                    value = localCapacity,
                    onValueChange = { value -> localCapacity = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                inbox = it.inbox.copy(
                                    suggestionCapacity = localCapacity.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_suggestion_capacity),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = FloatBallDefaults.InboxCapacityMin.toFloat()..
                        FloatBallDefaults.InboxCapacityMax.toFloat(),
                    shape = segmentedShape(4, 5),
                )
            }
        }

        item(key = "presets") {
            val presetCount = addable.size
            val addedMessage =
                stringResource(R.string.float_ball_suggestion_presets_added, presetCount)
            LunoGroup(title = stringResource(R.string.float_ball_suggestion_presets_group)) {
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_suggestion_presets_add),
                    subtitle = if (addable.isEmpty()) {
                        stringResource(R.string.float_ball_suggestion_presets_none)
                    } else {
                        stringResource(R.string.float_ball_suggestion_presets_available, presetCount)
                    },
                    shape = segmentedShape(0, 1),
                    enabled = addable.isNotEmpty(),
                    onClick = {
                        update { it.copy(clipboardBall = it.clipboardBall.copy(rules = it.clipboardBall.rules + addable)) }
                        showToast(addedMessage)
                    },
                )
            }
        }

        item(key = "rules") {
            LunoGroup(
                title = stringResource(R.string.float_ball_suggestion_rules_group),
                subtitle = stringResource(R.string.float_ball_suggestion_rules_order),
            ) {
                if (rules.isEmpty()) {
                    Surface(
                        shape = LargeShape,
                        color = MaterialTheme.colorScheme.surfaceBright,
                    ) {
                        EmptyState(message = stringResource(R.string.float_ball_suggestion_rules_empty))
                    }
                } else {
                    rules.forEachIndexed { index, rule ->
                        LunoSettingsRow(
                            icon = Icons.Default.Lightbulb,
                            title = rule.name.ifBlank { rule.pattern },
                            subtitle = stringResource(
                                R.string.float_ball_suggestion_rule_summary,
                                ruleActionLabel(rule),
                                rule.pattern,
                            ),
                            modifier = Modifier.dragReorderPlacement(reorder, index),
                            shape = segmentedShape(index, rules.size),
                            enabled = rule.enabled,
                            onClick = { onEditRule(rule.id) },
                            trailing = {
                                DragReorderHandle(
                                    reorder,
                                    index,
                                    stringResource(R.string.float_ball_suggestion_rule_drag),
                                    clipball.rules,
                                )
                            },
                        )
                    }
                }
            }
        }

        item(key = "link_dispatch") {
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.float_ball_suggestion_link_dispatch),
                    subtitle = stringResource(R.string.float_ball_suggestion_link_dispatch_hint),
                    shape = segmentedShape(0, 1),
                    onClick = onNavToLinkDispatch,
                )
            }
        }
    }
}

/** 列表副标题用的动作名：与编辑页选项保持一致。 */
@Composable
internal fun ruleActionLabel(rule: SuggestionRule): String = when (rule.action) {
    hunoia.luno.config.model.SuggestionRuleAction.COPY -> stringResource(R.string.float_ball_suggestion_rule_action_copy)
    hunoia.luno.config.model.SuggestionRuleAction.OPEN -> stringResource(R.string.float_ball_suggestion_rule_action_open)
    hunoia.luno.config.model.SuggestionRuleAction.SHARE -> stringResource(R.string.float_ball_suggestion_rule_action_share)
}
