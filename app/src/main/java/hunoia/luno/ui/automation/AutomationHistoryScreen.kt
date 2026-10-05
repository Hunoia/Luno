package hunoia.luno.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.ConfigProvider
import hunoia.luno.runtime.automation.AutomationEvent
import hunoia.luno.runtime.automation.ExecutionLog
import hunoia.luno.runtime.automation.ExecutionReason
import hunoia.luno.runtime.automation.SkipReason
import hunoia.luno.ui.component.EmptyState
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.liquidGlassBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationHistoryScreen(
    onBack: () -> Unit,
    ruleId: String,
) {
    val events by ConfigProvider.automationLog
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val ruleEvents = remember(events, ruleId) {
        events.filter { it.ruleId == ruleId }
            .takeLast(ExecutionLog.MAX_PER_RULE_QUERY)
            .reversed()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopBar(
                onBack = onBack,
                title = context.getString(R.string.automation_history),
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
            )
        },
    ) { padding ->
        MyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topPadding = padding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            if (ruleEvents.isEmpty()) {
                EmptyState(message = context.getString(R.string.automation_history_empty))
            }
            ruleEvents.forEach { event ->
                val time = timeFormat.format(Date(event.timeMs))
                SegmentedGroup {
                    SegmentedSettingsRow(
                        title = time,
                        subtitle = historySubtitle(event) +
                            if (event.steps.isNotEmpty()) " · ${event.steps.size}步" else "",
                    )
                }
            }
        }
    }
}

@Composable
fun historySubtitle(event: AutomationEvent): String {
    if (!event.fired) {
        return stringResource(
            when (event.reason) {
                SkipReason.NO_STEPS.code -> R.string.history_no_steps
                SkipReason.THROTTLED_COOLDOWN.code -> R.string.history_throttled_cooldown
                SkipReason.THROTTLED_WINDOW.code -> R.string.history_throttled_window
                SkipReason.OUTPRIORITIZED.code -> R.string.history_outprioritized
                SkipReason.RULE_BUSY.code -> R.string.history_rule_busy
                else -> R.string.history_no_steps
            },
        )
    }
    if (event.reason == ExecutionReason.OK) return stringResource(R.string.history_ok)
    if (event.reason.startsWith(ExecutionReason.INTERRUPTED_PREFIX)) {
        return stringResource(
            R.string.history_interrupted,
            event.reason.removePrefix(ExecutionReason.INTERRUPTED_PREFIX),
        )
    }
    return event.reason
}
