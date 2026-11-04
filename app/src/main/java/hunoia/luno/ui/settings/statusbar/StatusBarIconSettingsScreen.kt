package hunoia.luno.ui.settings.statusbar

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.system.StatusBarIcons
import hunoia.luno.domain.system.StatusBarIconHidingUseCase
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.component.feedback.NoticeBanner
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 状态栏图标隐藏设置页。
 *
 * 读写 `Settings.Secure.icon_blacklist`：SystemUI 按 slot 名过滤系统图标区，
 * 改动即时生效、重启后保留。页面不持有配置副本，进页 / 回前台一律重读。
 *
 * 降级：没有 `WRITE_SECURE_SETTINGS` 时开关只读，顶部提示条区分
 * 「Shizuku 未运行」与「已运行但未授权」两种原因，授权入口都在本页。
 */
@Composable
fun StatusBarIconSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(StatusBarIconHidingUseCase.PageState()) }
    var tick by remember { mutableIntStateOf(0) }
    var showMore by rememberSaveable { mutableStateOf(false) }

    val shizukuStatus by ShizukuManager.statusFlow.collectAsStateWithLifecycle()

    LaunchedEffect(tick) {
        state = withContext(Dispatchers.IO) {
            runCatching { StatusBarIconHidingUseCase.load(context) }
                .getOrDefault(StatusBarIconHidingUseCase.PageState())
        }
    }

    // 从系统调节工具 / 授权页回来时重读：那边的改动与这里共用同一项系统设置。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ShizukuManager.updateStatus()
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** 先改本地再落盘；失败按系统真值回滚（重新读一次）。 */
    fun toggle(slot: String, hidden: Boolean) {
        val before = state.hidden
        state = state.copy(
            hidden = if (hidden) before + slot else before - slot,
        )
        scope.launch {
            val ok = runCatching {
                StatusBarIconHidingUseCase.setHidden(context, slot, hidden)
            }.getOrDefault(false)
            if (!ok) {
                Toast.makeText(context, R.string.status_bar_icons_write_failed, Toast.LENGTH_SHORT).show()
                tick++
            }
        }
    }

    fun showAll() {
        scope.launch {
            val ok = runCatching { StatusBarIconHidingUseCase.showAll(context) }
                .getOrDefault(false)
            if (!ok) {
                Toast.makeText(context, R.string.status_bar_icons_write_failed, Toast.LENGTH_SHORT).show()
            }
            tick++
        }
    }

    val common = StatusBarIconCatalog.common
    val more = StatusBarIconCatalog.more

    LunoScreen(
        title = stringResource(R.string.status_bar_icons_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
    ) {
        when {
            !shizukuStatus.isReady -> NoticeBanner(
                message = stringResource(R.string.status_bar_icons_banner_shizuku),
                actionLabel = stringResource(R.string.status_bar_icons_banner_shizuku_action),
                onAction = {
                    scope.launch {
                        runCatching { ShizukuManager.autoRequestPermissionIfNeeded() }
                        ShizukuManager.updateStatus()
                        tick++
                    }
                },
            )
            !state.canWrite -> NoticeBanner(
                message = stringResource(R.string.status_bar_icons_banner_write_secure),
                actionLabel = stringResource(R.string.status_bar_icons_banner_write_secure_action),
                onAction = {
                    scope.launch {
                        runCatching { ShizukuManager.ensureWriteSecureSettings() }
                        tick++
                    }
                },
            )
        }

        LunoGroup(
            title = stringResource(R.string.status_bar_icons_common_group),
            subtitle = stringResource(R.string.status_bar_icons_scope_hint),
        ) {
            common.forEachIndexed { index, slot ->
                LunoSwitchRow(
                    title = stringResource(slot.labelRes),
                    subtitle = slot.hintRes?.let { stringResource(it) }.orEmpty(),
                    checked = slot.key in state.hidden,
                    enabled = state.canWrite,
                    onCheckedChange = { hidden -> toggle(slot.key, hidden) },
                    shape = segmentedShape(index, common.size),
                )
            }
        }

        // 「更多（MIUI）」默认折叠：展开行本身就是组首行，展开后的开关同组往下接。
        LunoGroup(title = stringResource(R.string.status_bar_icons_more_group)) {
            val rowCount = if (showMore) more.size + 1 else 1
            LunoSettingsRow(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.status_bar_icons_more_entry),
                subtitle = stringResource(R.string.status_bar_icons_more_entry_hint),
                shape = segmentedShape(0, rowCount),
                onClick = { showMore = !showMore },
                trailing = {
                    Icon(
                        imageVector = if (showMore) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(RowIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (showMore) {
                more.forEachIndexed { index, slot ->
                    LunoSwitchRow(
                        title = stringResource(slot.labelRes),
                        subtitle = slot.hintRes?.let { stringResource(it) }.orEmpty(),
                        checked = slot.key in state.hidden,
                        enabled = state.canWrite,
                        onCheckedChange = { hidden -> toggle(slot.key, hidden) },
                        shape = segmentedShape(index + 1, rowCount),
                    )
                }
            }
        }

        LunoGroup(title = stringResource(R.string.status_bar_icons_actions_group)) {
            LunoSettingsRow(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.status_bar_icons_show_all),
                subtitle = stringResource(R.string.status_bar_icons_show_all_hint),
                shape = segmentedShape(0, 2),
                enabled = state.canWrite,
                onClick = ::showAll,
            )
            LunoSettingsRow(
                icon = Icons.Default.OpenInNew,
                title = stringResource(R.string.status_bar_icons_open_tuner),
                subtitle = stringResource(R.string.status_bar_icons_open_tuner_hint),
                shape = segmentedShape(1, 2),
                onClick = {
                    val intent = StatusBarIcons.tunerIntent(context)
                    if (intent == null) {
                        Toast.makeText(
                            context,
                            R.string.status_bar_icons_tuner_missing,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.status_bar_icons_tuner_missing,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                },
            )
        }
    }
}
