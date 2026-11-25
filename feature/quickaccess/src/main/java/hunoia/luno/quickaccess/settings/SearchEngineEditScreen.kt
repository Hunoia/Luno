package hunoia.luno.quickaccess.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.quickaccess.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.effectiveLaunch
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.quickaccess.launchSearchAction
import hunoia.luno.quickaccess.query.SearchSourceRegistry
import hunoia.luno.quickaccess.query.toSearchAction
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.sheet.AppPickerRequest
import hunoia.luno.ui.component.sheet.AppPickerSlot
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/** 测试按钮用固定词：能直观看出 %s / %q 是否被正确替换。 */
private const val EDIT_TEST_QUERY = "Luno"

/** 进页一次性快照：目标引擎 + 判重输入（其它引擎 + 来源标识）；之后配置流变化不重建草稿。 */
private data class EngineEditSnapshot(
    val engine: SearchEngine?,
    val existing: List<SearchEngine>,
    /** 来源标识（lowercase 标识 → 来源 key），标识撞来源时报错指名道姓。 */
    val sourceTriggers: Map<String, String>,
)

/**
 * 引擎编辑页（引擎设置页的下级页）：字段与校验复用 [EngineEditFields]。
 * 右下角 FAB 保存（无效草稿 toast），有未保存改动时返回弹确认；
 * 保存以最新配置为基础只覆盖本页字段。engineId 空 = 新增。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineEditScreen(
    engineId: String,
    onBack: () -> Unit,
    /** 应用选择器由 `:app` 注入（与动作库 / 链接设置同一插槽）。 */
    appPicker: AppPickerSlot? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addMode = engineId.isBlank()
    var snapshot by remember(engineId) { mutableStateOf<EngineEditSnapshot?>(null) }
    LaunchedEffect(engineId) {
        SearchCenter.search.collect { settings ->
            if (snapshot == null) {
                snapshot = EngineEditSnapshot(
                    engine = settings.allEngines().firstOrNull { it.id == engineId },
                    existing = settings.allEngines().filter { it.id != engineId },
                    // 来源标识撞引擎标识会被引擎直达顶掉，这里一并交给编辑状态判重。
                    sourceTriggers = SearchSourceRegistry.all
                        .mapNotNull { source ->
                            val trigger = settings.effectiveTrigger(source.key).trim().lowercase()
                            if (trigger.isEmpty()) null else trigger to source.key
                        }
                        .toMap(),
                )
            }
        }
    }
    val snap = snapshot
    if (snap == null) return
    if (!addMode && snap.engine == null) {
        // 编辑期间引擎在别处被删：给明确落点，不静默弹回。
        hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.search_engine_edit),
onBack = onBack) {
hunoia.luno.ui.component.page.LunoPageColumn() {
    block {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(hunoia.luno.ui.theme.ListSpacing),
        ) {

            EmptyState(message = stringResource(R.string.search_engine_missing))

        }
    }
}
}
        return
    }
    val editState = rememberEngineEditState(
        initial = snap.engine,
        existing = snap.existing,
        sourceTriggers = snap.sourceTriggers,
    )
    var targetPackage by remember(engineId) {
        mutableStateOf(
            (snap.engine?.effectiveLaunch() as? SearchLaunch.Url)?.packageName.orEmpty(),
        )
    }
    // 目标应用显示名：包名解析失败（已卸载等）回退显示包名本身。
    val appLabel by produceState<String?>(null, targetPackage) {
        value = targetPackage.takeIf { it.isNotBlank() }?.let { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrNull()
        }
    }
    val initialPackage = remember(engineId) {
        (snap.engine?.effectiveLaunch() as? SearchLaunch.Url)?.packageName.orEmpty()
    }
    // 图片双模式目标应用（独立于主目标应用）：以编辑状态为唯一来源，选择器直接回写。
    val imageTargetPackage = editState.imageTargetPackage
    val imageAppLabel by produceState<String?>(null, imageTargetPackage) {
        value = imageTargetPackage.takeIf { it.isNotBlank() }?.let { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrNull()
        }
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var showImageAppPicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val valid = editState.valid(targetPackage)
    val dirty = editState.isDirty() || targetPackage != initialPackage
    val guardedBack = { if (dirty) showDiscardConfirm = true else onBack() }
    fun save() {
        val result = editState.result(targetPackage)
        scope.launch {
            SearchCenter.updateSearch { current ->
                if (addMode) {
                    current.copy(
                        customEngines = current.customEngines +
                            SearchEngine(
                                name = result.name,
                                urlTemplate = result.template,
                                trigger = result.trigger,
                                launch = result.launch,
                                iconText = result.iconText,
                                iconType = result.iconType,
                                icon = result.icon,
                                imageShareEnabled = result.imageShareEnabled,
                                imageTargetPackage = result.imageTargetPackage,
                                contentMode = result.contentMode,
                            ),
                    )
                } else {
                    current.copy(
                        customEngines = current.customEngines.map {
                            if (it.id == engineId) {
                                it.copy(
                                    name = result.name,
                                    urlTemplate = result.template,
                                    trigger = result.trigger,
                                    launch = result.launch,
                                    iconText = result.iconText,
                                    iconType = result.iconType,
                                    icon = result.icon,
                                    imageShareEnabled = result.imageShareEnabled,
                                    imageTargetPackage = result.imageTargetPackage,
                                    contentMode = result.contentMode,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            }
            onBack()
        }
    }
    hunoia.luno.ui.component.page.LunoPage(title = stringResource(
            if (addMode) R.string.search_engine_add else R.string.search_engine_edit,
        ),
onBack = guardedBack,
actions = {
            // 测试放顶栏（与动作库编辑页一致）：模板/目标应用就绪即可试，名称不拦。
            LunoIconButton(
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.search_engine_test),
                enabled = editState.testable(targetPackage),
                onClick = {
                    val action = editState.buildLaunch(targetPackage).toSearchAction(EDIT_TEST_QUERY)
                    if (!launchSearchAction(context, action)) showToast(hunoia.luno.core.bridge.R.string.launch_no_handler)
                },
            )
        },
floatingContent = { _ ->
            // 点击直接保存（无效草稿 toast 提示）；无效态换灰底提示不可保存。
            LunoFab(
                onClick = {
                    if (valid) save() else showToast(R.string.search_engine_invalid_hint)
                },
                icon = Icons.Default.Save,
                contentDescription = stringResource(hunoia.luno.design.R.string.save),
                containerColor = if (valid) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
dialogs = {
            if (showDiscardConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = stringResource(hunoia.luno.actions.R.string.action_library_discard_title),
                    text = stringResource(hunoia.luno.actions.R.string.action_library_discard_desc),
                    onConfirm = {
                        showDiscardConfirm = false
                        onBack()
                    },
                )
            }
        },
sheets = {
            if (showAppPicker) {
                val picker = appPicker
                if (picker == null) {
                    showAppPicker = false
                } else {
                    picker(
                        AppPickerRequest(
                            selectedPackageNames = listOf(targetPackage),
                            selectedUserId = null,
                            onDismiss = { showAppPicker = false },
                            onConfirm = { packages ->
                                targetPackage = packages.firstOrNull().orEmpty()
                                showAppPicker = false
                            },
                            onSelect = null,
                        ),
                    )
                }
            }
            if (showImageAppPicker) {
                val picker = appPicker
                if (picker == null) {
                    showImageAppPicker = false
                } else {
                    picker(
                        AppPickerRequest(
                            selectedPackageNames = listOf(imageTargetPackage),
                            selectedUserId = null,
                            onDismiss = { showImageAppPicker = false },
                            onConfirm = { packages ->
                                editState.imageTargetPackage = packages.firstOrNull().orEmpty()
                                showImageAppPicker = false
                            },
                            onSelect = null,
                        ),
                    )
                }
            }
        }) {
hunoia.luno.ui.component.page.LunoPageColumn(bottomContentPadding = FloatingContentBottom) {
    block {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(hunoia.luno.ui.theme.ListSpacing),
        ) {

        EngineEditFields(
            state = editState,
            targetPackage = targetPackage,
            appLabel = appLabel,
            onPickApp = { showAppPicker = true },
            imageAppLabel = imageAppLabel,
            onPickImageApp = { showImageAppPicker = true },
        )

        }
    }
}
}
}
