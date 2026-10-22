package hunoia.luno.ui.automation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.automation.AutomationEvent
import hunoia.luno.ui.component.feedback.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationHistoryScreen(
    onBack: () -> Unit,
    ruleId: String,
) {
    val events by AutomationCenter.log
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val ruleEvents = remember(events, ruleId) {
        // 无墙钟的老事件直接丢弃（timeMs 是开机时长，格式化出来是错的）。
        events.filter { it.ruleId == ruleId && it.wallTimeMs > 0L }
            .takeLast(AutomationCenter.maxEventsPerRuleQuery)
            .reversed()
    }
    LunoScreen(
        title = stringResource(R.string.automation_history),
        onBack = onBack,
    ) {

            if (ruleEvents.isEmpty()) {
                EmptyState(message = context.getString(R.string.automation_history_empty))
            }
            ruleEvents.forEach { event ->
                val time = timeFormat.format(Date(event.wallTimeMs))
                LunoGroup {
                    LunoSettingsRow(
                        title = time,
                        subtitle = historySubtitle(event) +
                            if (event.steps.isNotEmpty()) " · ${event.steps.size}步" else "",
                    )
                }
            }
        
    }
}
@Composable
fun historySubtitle(event: AutomationEvent): String {
    if (!event.fired) {
        return stringResource(
            when (event.reason) {
                AutomationCenter.skipNoSteps.code -> R.string.history_no_steps
                AutomationCenter.skipThrottledCooldown.code -> R.string.history_throttled_cooldown
                AutomationCenter.skipThrottledWindow.code -> R.string.history_throttled_window
                AutomationCenter.skipOutprioritized.code -> R.string.history_outprioritized
                AutomationCenter.skipConstraintUnmet.code -> R.string.history_constraint_unmet
                AutomationCenter.skipRuleBusy.code -> R.string.history_rule_busy
                else -> R.string.history_no_steps
            },
        )
    }
    if (AutomationCenter.isOkReason(event.reason)) return stringResource(R.string.history_ok)
    if (AutomationCenter.isInterruptedReason(event.reason)) {
        return stringResource(
            R.string.history_interrupted,
            AutomationCenter.stripInterruptedPrefix(event.reason),
        )
    }
    return event.reason
}