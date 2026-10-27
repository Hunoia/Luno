package hunoia.luno.ui.settings.link

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.LinkMatchType
import hunoia.luno.config.model.LinkMissMode
import hunoia.luno.config.model.LinkTapMode
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.link.LinkDispatcher
import hunoia.luno.link.LinkPresets
import hunoia.luno.ui.component.DragReorderHandle
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.dragReorderPlacement
import hunoia.luno.ui.component.rememberDragReorderState
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.ChoiceRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/**
 * 链接分发设置：规则表 + 胶囊/未命中行为 + 默认浏览器入口。
 * 规则按顺序匹配（第一行优先），行级进入编辑页，新增走右下角 FAB。
 */
@Composable
fun LinkDispatchSettingsScreen(
    onBack: () -> Unit,
    onEditRule: (String) -> Unit,
    onCreateRule: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val linkSettings = actionSettings.linkDispatch
    val listState = rememberLazyListState()

    fun update(transform: (LinkDispatchSettings) -> LinkDispatchSettings) {
        scope.launch {
            TuneConfig.updateAction { it.copy(linkDispatch = transform(it.linkDispatch)) }
        }
    }
    // 规则顺序即优先级（第一条命中生效），所以必须能拖；草稿松手才落盘。
    val reorder = rememberDragReorderState<LinkDispatchRule>(
        current = { linkSettings.rules },
        onCommit = { finalRules -> update { it.copy(rules = finalRules) } },
    )
    LaunchedEffect(linkSettings.rules) { reorder.syncWithCurrent() }
    val rules = reorder.items

    LunoScreenLazy(
        title = stringResource(R.string.link_dispatch),
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = onCreateRule,
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.link_dispatch_rule_add),
            )
        },
    ) {
        item {
            LunoGroup(title = stringResource(R.string.link_dispatch_global_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.link_dispatch_enabled),
                    subtitle = stringResource(R.string.link_dispatch_enabled_hint),
                    checked = linkSettings.enabled,
                    onCheckedChange = { checked -> update { it.copy(enabled = checked) } },
                    shape = segmentedShape(0, 3),
                )
                ChoiceRow(
                    title = stringResource(R.string.link_dispatch_capsule_tap),
                    subtitle = stringResource(R.string.link_dispatch_capsule_tap_hint),
                    options = listOf(
                        stringResource(R.string.link_dispatch_tap_dispatch),
                        stringResource(R.string.link_dispatch_tap_share),
                    ),
                    selectedIndex = if (linkSettings.capsuleTap == LinkTapMode.DISPATCH) 0 else 1,
                    onSelect = { index ->
                        update {
                            it.copy(
                                capsuleTap = if (index == 0) LinkTapMode.DISPATCH else LinkTapMode.SHARE,
                            )
                        }
                    },
                    shape = segmentedShape(1, 3),
                )
                ChoiceRow(
                    title = stringResource(R.string.link_dispatch_miss),
                    subtitle = stringResource(R.string.link_dispatch_miss_hint),
                    options = listOf(
                        stringResource(R.string.link_dispatch_miss_share),
                        stringResource(R.string.link_dispatch_miss_browser),
                        stringResource(R.string.link_dispatch_miss_picker),
                    ),
                    selectedIndex = when (linkSettings.missAction) {
                        LinkMissMode.SHARE -> 0
                        LinkMissMode.BROWSER -> 1
                        LinkMissMode.PICKER -> 2
                    },
                    onSelect = { index ->
                        update {
                            it.copy(
                                missAction = when (index) {
                                    0 -> LinkMissMode.SHARE
                                    1 -> LinkMissMode.BROWSER
                                    else -> LinkMissMode.PICKER
                                },
                            )
                        }
                    },
                    shape = segmentedShape(2, 3),
                )
            }
        }
        item {
            // PM 查询很贵：只在规则表变化时重算，开关/滚动不重复扫包。
            val addable = remember(linkSettings.rules) {
                LinkPresets.addable(context, linkSettings.rules)
            }
            LunoGroup(title = stringResource(R.string.link_dispatch_presets_group)) {
                LunoSettingsRow(
                    title = stringResource(R.string.link_dispatch_presets_add),
                    subtitle = if (addable.isEmpty()) {
                        stringResource(R.string.link_dispatch_presets_none)
                    } else {
                        stringResource(R.string.link_dispatch_presets_available, addable.size)
                    },
                    shape = segmentedShape(0, 1),
                    enabled = addable.isNotEmpty(),
                    onClick = {
                        update { it.copy(rules = it.rules + addable) }
                        showToast(
                            context.getString(R.string.link_dispatch_presets_added, addable.size),
                        )
                    },
                )
            }
        }
        item {
            LunoGroup(title = stringResource(R.string.link_dispatch_rules_group)) {
                if (rules.isEmpty()) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(ContainerRadius),
                        color = MaterialTheme.colorScheme.surfaceBright,
                    ) {
                        EmptyState(message = stringResource(R.string.link_dispatch_rules_empty))
                    }
                } else {
                    rules.forEachIndexed { index, rule ->
                        // key 必须带上目标应用/浏览器开关：改完目标返回列表时才能刷新副标题。
                        val targetLabel by produceState(
                            initialValue = null as String?,
                            rule.id,
                            rule.targetPackage,
                            rule.viaBrowser,
                        ) {
                            value = LinkDispatcher.targetOf(context, rule)?.label
                        }
                        LunoSettingsRow(
                            icon = Icons.Default.Link,
                            title = rule.name.ifBlank { rule.pattern },
                            subtitle = linkRuleSummary(
                                type = rule.matchType,
                                pattern = rule.pattern,
                                targetLabel = targetLabel,
                                viaBrowser = rule.viaBrowser,
                                resolveShortLink = rule.resolveShortLink,
                            ),
                            modifier = Modifier.dragReorderPlacement(reorder, index),
                            shape = segmentedShape(index, rules.size),
                            enabled = rule.enabled,
                            onClick = { onEditRule(rule.id) },
                            trailing = {
                                DragReorderHandle(
                                    reorder,
                                    index,
                                    stringResource(R.string.link_dispatch_rule_drag),
                                    linkSettings.rules,
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            LunoGroup(title = stringResource(R.string.link_dispatch_browser_group)) {
                LunoSettingsRow(
                    title = stringResource(R.string.link_dispatch_default_browser),
                    subtitle = stringResource(R.string.link_dispatch_default_browser_hint),
                    shape = segmentedShape(0, 1),
                    onClick = {
                        val launched = runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        if (launched.isFailure) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun linkRuleSummary(
    type: LinkMatchType,
    pattern: String,
    targetLabel: String?,
    viaBrowser: Boolean,
    resolveShortLink: Boolean,
): String {
    val typeLabel = stringResource(
        when (type) {
            LinkMatchType.CONTAINS -> R.string.link_dispatch_match_contains
            LinkMatchType.DOMAIN -> R.string.link_dispatch_match_domain
            LinkMatchType.REGEX -> R.string.link_dispatch_match_regex
            LinkMatchType.EXACT -> R.string.link_dispatch_match_exact
        },
    )
    val target = if (viaBrowser && targetLabel == null) {
        stringResource(R.string.link_dispatch_rule_browser_auto)
    } else {
        targetLabel ?: stringResource(R.string.link_dispatch_rule_no_target)
    }
    val summary = stringResource(R.string.link_dispatch_rule_summary, typeLabel, pattern, target)
    return if (resolveShortLink) {
        summary + stringResource(R.string.link_dispatch_rule_resolve_tag)
    } else {
        summary
    }
}
