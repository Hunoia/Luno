package hunoia.luno.ui.actionselect

import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.action.model.hasPointParams
import hunoia.luno.action.model.launchAppRef
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.action.model.withLongPressVariant
import hunoia.luno.domain.action.ActionCatalog
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.effectiveGridRows
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.core.AppContext
import hunoia.luno.gridpanel.geometry.gridPanelVisibleRows
import hunoia.luno.core.JsonSerializer
import hunoia.luno.gridpanel.widget.PanelAppWidgetHost
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import hunoia.luno.ui.automation.AutomationDraftHolder
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.FloatBallSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun StoredAction.shortcutInfo(): LauncherInfo.ShortcutInfo? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching { JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(data) }.getOrNull()
}

internal fun List<StoredAction>.shortcutIconPaths(): List<String> =
    flatMap { ShortcutIconFiles.iconPathsOf(it) }

internal fun StoredAction.shortcutIconPaths(): List<String> = ShortcutIconFiles.iconPathsOf(this)

internal fun tryDeleteShortcutIcons(old: StoredAction?, new: StoredAction?) {
    val newPaths = listOfNotNull(new).flatMap { it.shortcutIconPaths() }.toSet()
    listOfNotNull(old).flatMap { it.shortcutIconPaths() }
        .filter { it !in newPaths }
        .forEach(ShortcutIconFiles::delete)
}

/**
 * 选择页的统一落盘入口。返回 false = 软失败（目前只有网格面板页满一种），
 * 调用方按「这一页放不下」提示而不是「保存成功」。
 */
internal suspend fun saveSettingsAction(
    actionSelect: ActionSelect,
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
): Boolean {
    // 单值语义：选择页给的就是最终结果，没有「占位 vs 有效」之分 ——
    // 选「无动作」传 null（= 删键/存 null），选别的传那一个。
    val picked = getUiState().selectedRecord.action
    if (actionSelect.actionPanelId.isNotBlank()) {
        saveActionPanelItem(actionSelect, picked)
        return true
    }
    if (actionSelect.gridPanelId.isNotBlank()) {
        return saveGridPanelItem(actionSelect, picked)
    }
    if (actionSelect.floatBallSlot.isNotBlank()) {
        saveFloatBallSlot(actionSelect.floatBallSlot, picked)
        return true
    }
    if (actionSelect.automationRuleId.isNotBlank()) {
        saveAutomationEffect(actionSelect, picked)
        return true
    }
    if (actionSelect.subGestureId.isNotBlank()) {
        GestureConfig.updateSubGestures { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { gesture ->
                    if (gesture.id != actionSelect.subGestureId) return@map gesture
                    val oldAction = gesture.actionFor(actionSelect.triggerType, actionSelect.direction)
                    tryDeleteShortcutIcons(oldAction, picked)
                    gesture.withAction(actionSelect.triggerType, actionSelect.direction, picked)
                }
            )
        }
        return true
    }
    val buttonsUpdater = GestureConfig::updateButtons
    buttonsUpdater { list ->
        val mutableList = list.toMutableList()
        var button: GestureButton? = null
        var index = -1
        for (i in mutableList.indices) {
            index = i
            val b = mutableList[i]
            if (b.id == actionSelect.gestureButtonId) {
                button = b
                break
            }
        }
        if (button == null) {
            return@buttonsUpdater mutableList
        }
        val oldAction = button.actionFor(actionSelect.triggerType, actionSelect.direction)
        tryDeleteShortcutIcons(oldAction, picked)
        button = button.withAction(actionSelect.triggerType, actionSelect.direction, picked)
        mutableList.apply {
            set(index, button)
        }
    }
    return true
}

/**
 * 面板项落盘：`actionPanelItemIndex < 0` 是新增一项（追加），>=0 是替换该项的长按变体。
 *
 * 走的是选择页而不是独立的参数编辑器 —— 面板项本来就是一整个动作，
 * 复用 `ActionSelect` 的分类/搜索/应用/快捷方式全部分组最省事。
 */
private suspend fun saveActionPanelItem(actionSelect: ActionSelect, picked: StoredAction?) {
    val panelId = actionSelect.actionPanelId
    val index = actionSelect.actionPanelItemIndex
    TuneConfig.updateAction { settings ->
        val panel = settings.actionPanels.panelById(panelId) ?: return@updateAction settings
        val items = when {
            // 新增：选了「无动作」等于放弃本次添加，不改面板。
            picked == null || picked.typeId == "internal.none" -> return@updateAction settings
            // 改长按变体：变体本身不带变体（防止套娃）。
            index >= 0 -> panel.items.mapIndexed { i, item ->
                if (i == index) item.withLongPressVariant(picked.withoutVariantKey()) else item
            }
            else -> panel.items + picked
        }
        settings.copy(
            actionPanels = settings.actionPanels.withPanel(panel.copy(items = items))
        )
    }
}

/**
 * 网格面板项落盘：新增一项，落到 [ActionSelect.gridPanelPageIndex] 指定页里
 * 顺延首个空位（只在本页内扫，跨页不占位）。面板项只进不出 —— 想换动作就删了
 * 重加，没有「替换已有项」分支，也没有长按变体。
 *
 * 页满返回 false，由调用方明确提示。静默丢弃 + 照报「保存成功」就是真机上
 * 「添加没反应」的元凶，动作项和小部件项都不能再犯。
 */
private suspend fun saveGridPanelItem(actionSelect: ActionSelect, picked: StoredAction?): Boolean {
    // 选了「无动作」等于放弃本次操作，不改面板，也不当失败提示。
    if (picked == null || picked.typeId == "internal.none") return true
    var saved = false
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId)
            ?: return@updateAction settings
        val landscape = AppContext.get().resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val geo = panel.geometry(landscape)
        val columns = geo.columnCount.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        val pageIndex = actionSelect.gridPanelPageIndex.coerceIn(0, (panel.pageCount - 1).coerceAtLeast(0))
        val rows = panel.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = AppContext.get().resources.configuration.screenWidthDp.toFloat(),
                screenHdp = AppContext.get().resources.configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
                pageCount = panel.pageCount,
            ),
        )
        val free = GridPanelItemLayout.firstFreeSlot(
            items = panel.items.filter { it.pageIndex == pageIndex },
            columns = columns,
            rows = rows,
            spanX = 1,
            spanY = 1,
        ) ?: return@updateAction settings
        saved = true
        settings.copy(
            actionGridPanels = settings.actionGridPanels.withPanel(
                panel.copy(
                    items = panel.items + GridPanelItem(
                        x = free.x,
                        y = free.y,
                        pageIndex = pageIndex,
                        kind = GridPanelItem.Kind.Action(listOf(picked.withoutVariantKey())),
                    ),
                ),
            ),
        )
    }
    return saved
}

/**
 * 网格面板的第三方小部件落盘（docs/grid-panel.md）。
 *
 * 落点：先按 provider 算出的 span 在**加项那一页**扫首个放得下的空位；放不下就
 * **自动降格**（面积从大到小逐档收缩，一墨选择器的占格数badge也是给用户预期用的）
 * —— 都塞不下才返回 false，由调用方 Toast 并回收 id（**不再静默丢弃**：真机上
 * 表现是「添加没反应」，其实是在偷偷失败）。
 *
 * 返回 true = 落盘成功（含降格）；false = 页满放弃，调用方必须回收 appWidgetId。
 */
/** 网格面板落盘结果：区分「面板不存在」和「真放不下」——两者给用户的提示不同。 */
/**
 * 追加语义的落点：新项放到本页已占内容的**正下方**（y = max(bottom)）。
 * 宽度不满整行时在那一行内从左往右找能容下的 x；被同排项挡住就顺延下一行。
 * 全都放不下（理论上行数无限，基本只会是 spanX > 列数）返回 null，走降档兜底。
 */
private fun bottomSlot(
    items: List<GridPanelItem>,
    columns: Int,
    rows: Int,
    spanX: Int,
    spanY: Int,
): GridCell? {
    var y = items.maxOfOrNull { it.bottom } ?: 0
    while (y + spanY <= rows) {
        for (x in 0..(columns - spanX)) {
            val clash = items.any {
                it.x < x + spanX && x < it.right && it.y < y + spanY && y < it.bottom
            }
            if (!clash) return GridCell(x, y)
        }
        y++
    }
    return null
}

internal enum class GridPanelSaveResult { Saved, PageFull, NoPanel }

internal suspend fun saveGridPanelWidget(
    actionSelect: ActionSelect,
    provider: String,
    appWidgetId: Int,
    spanX: Int,
    spanY: Int,
): GridPanelSaveResult {
    var result = GridPanelSaveResult.NoPanel
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            android.util.Log.w("GridPanelSave", "widget: panel not found requested=${actionSelect.gridPanelId} " +
            "available=${settings.actionGridPanels.panels.map { it.id to it.name }} select=${actionSelect}")
            return@updateAction settings
        }
        val landscape = AppContext.get().resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val geo = panel.geometry(landscape)
        val columns = geo.columnCount
            .coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        val pageIndex = actionSelect.gridPanelPageIndex.coerceIn(0, (panel.pageCount - 1).coerceAtLeast(0))
        val rows = panel.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = AppContext.get().resources.configuration.screenWidthDp.toFloat(),
                screenHdp = AppContext.get().resources.configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
                pageCount = panel.pageCount,
            ),
        )
        val pageItems = panel.items.filter { it.pageIndex == pageIndex }
        // 追加优先：原尺寸先试「放到最下面」；实在不行才走降档扫空位兜底。
        bottomSlot(pageItems, columns, rows, spanX, spanY)?.let { cell ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = spanX,
                            spanY = spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.AppWidget(
                                appWidgetId = appWidgetId,
                                provider = provider,
                                minSpanX = spanX,
                                minSpanY = spanY,
                            ),
                        ),
                    ),
                ),
            )
        }
        // 降格序列与拖拽的重排思路分开：加项只填空（firstFreeSlot），放不下自动降档
        // 占更小的空位 —— 重排是编辑态拖拽的语义，加项不该惊动已有项。
        val candidates = buildList {
            for (h in spanY downTo 1) {
                for (w in spanX downTo 1) {
                    add(WidgetSpan(w, h))
                }
            }
        }.sortedByDescending { it.spanX * it.spanY }
            .distinct()
        for (span in candidates) {
            val cell = GridPanelItemLayout.firstFreeSlot(
                items = pageItems,
                columns = columns,
                rows = rows,
                spanX = span.spanX,
                spanY = span.spanY,
            ) ?: continue
            result = GridPanelSaveResult.Saved
            // 命中即返回新 settings —— 这里写的必须是 copy 的结果本身，
            // 曾经把 copy 结果丢弃、return 了原 settings，落盘形同没写。
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = span.spanX,
                            spanY = span.spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.AppWidget(
                                appWidgetId = appWidgetId,
                                provider = provider,
                                // 冻结 provider 声明的最小 span：resize 时不再查
                                // AppWidgetManager（手势回调里做跨进程 IPC 会掉帧，
                                // 且 provider 被卸载后查不到会退化成 1×1）。
                                minSpanX = span.spanX,
                                minSpanY = span.spanY,
                            ),
                        ),
                    ),
                ),
            )
        }
        settings
    }
    if (result != GridPanelSaveResult.Saved) {
        android.util.Log.w("GridPanelSave", "widget not placed: result=$result span=$spanX x $spanY")
        PanelAppWidgetHost.get().release(appWidgetId)
    }
    return result
}

/**
 * 内置小部件落盘：同款「只填空 + 放不下降档」序列，无宿主 id 可回收。
 * 默认 span 4×1（def 声明），列数不够时降格到 3×1 → … → 1×1。
 */
internal suspend fun saveGridPanelInternalWidget(
    actionSelect: ActionSelect,
    widgetId: String,
): GridPanelSaveResult {
    val def = hunoia.luno.gridpanel.internal.InternalWidgetDefs.firstOrNull { it.id == widgetId }
    if (def == null) {
        android.util.Log.w("GridPanelSave", "internal widget: unknown id=$widgetId")
        return GridPanelSaveResult.NoPanel
    }
    var result = GridPanelSaveResult.NoPanel
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            android.util.Log.w("GridPanelSave", "internal: panel not found id=${actionSelect.gridPanelId}")
            return@updateAction settings
        }
        val landscape = AppContext.get().resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val geo = panel.geometry(landscape)
        val columns = geo.columnCount
            .coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
        val pageIndex = actionSelect.gridPanelPageIndex.coerceIn(0, (panel.pageCount - 1).coerceAtLeast(0))
        val rows = panel.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = AppContext.get().resources.configuration.screenWidthDp.toFloat(),
                screenHdp = AppContext.get().resources.configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
                pageCount = panel.pageCount,
            ),
        )
        val pageItems = panel.items.filter { it.pageIndex == pageIndex }
        // 追加优先：原尺寸先试「放到最下面」。
        bottomSlot(pageItems, columns, rows, def.spanX, def.spanY)?.let { cell ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = def.spanX,
                            spanY = def.spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.Internal(widgetId = widgetId),
                        ),
                    ),
                ),
            )
        }
        val candidates = buildList {
            for (h in def.spanY downTo 1) {
                for (w in def.spanX downTo 1) {
                    add(WidgetSpan(w, h))
                }
            }
        }.sortedByDescending { it.spanX * it.spanY }.distinct()
        for (span in candidates) {
            val cell = GridPanelItemLayout.firstFreeSlot(
                items = pageItems,
                columns = columns,
                rows = rows,
                spanX = span.spanX,
                spanY = span.spanY,
            ) ?: continue
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.copy(
                        items = panel.items + GridPanelItem(
                            x = cell.x,
                            y = cell.y,
                            spanX = span.spanX,
                            spanY = span.spanY,
                            pageIndex = pageIndex,
                            kind = GridPanelItem.Kind.Internal(widgetId = widgetId),
                        ),
                    ),
                ),
            )
        }
        settings
    }
    return result
}

/**
 * 悬浮球槽位落盘。
 *
 * 目前只有单击一个槽，但按槽名分发而不是写死 —— 长按/滑动若后续要开放成
 * 可配动作（而不是现在的固定语义），这里加一个 case 即可，不必再改选择页。
 */
private suspend fun saveFloatBallSlot(slot: String, picked: StoredAction?) {
    if (!FloatBallSlot.isValid(slot)) return
    TuneConfig.updateAction { settings ->
        settings.copy(
            floatBall = settings.floatBall.copy(
                tapAction = picked?.takeIf { it.typeId != "internal.none" },
            ),
        )
    }
}

private suspend fun saveAutomationEffect(actionSelect: ActionSelect, picked: StoredAction?) {
    val current = loadAutomationAction(actionSelect)
    tryDeleteShortcutIcons(current, picked)
    // 动作库条目存的就是引用本身（internal.libraryRef），运行时由分发器就地解析成条目动作。
    // 以前引用另存 RuleStep.entryId、action 只放"真动作"，字段删掉后引用即步骤的 action。
    val action = picked?.withoutVariantKey()
    val path = actionSelect.automationStepPath
    fun applyTo(rule: AutomationRule): AutomationRule {
        if (rule.effect.stepAt(path) == null) {
            android.util.Log.w(
                "ActionSelect",
                "automation step path missing, rule=${rule.id} path=$path",
            )
        }
        return rule.copy(effect = rule.effect.withStepAt(path) { step ->
            step.copy(kind = hunoia.luno.config.model.RuleStepKind.ACTION, action = action)
        })
    }
    // 草稿优先：编辑页跳转时只暂存内存，未保存前不写库。
    // 无暂存时以库中规则为底新建暂存——选择页永不直写库，落盘统一由编辑页保存键负责。
    val base = AutomationDraftHolder.take(actionSelect.automationRuleId)
        ?: AutomationCenter.getRules().find { it.id == actionSelect.automationRuleId }
        ?: return
    AutomationDraftHolder.stage(applyTo(base))
}

private suspend fun loadAutomationAction(actionSelect: ActionSelect): StoredAction? {
    val rules = AutomationCenter.getRules()
    val rule = AutomationDraftHolder.take(actionSelect.automationRuleId)
        ?: rules.find { it.id == actionSelect.automationRuleId } ?: return null
    return rule.effect.stepAt(actionSelect.automationStepPath)?.action
}

internal suspend fun updateShortcutInfosBody(
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit,
    addNewShortcut: (LauncherInfo, LauncherInfo.ShortcutInfo) -> Unit
) {
    val createLauncherInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryShortcutActivities(AppContext.get())
    }
    val launchLauncherInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryShortcuts(AppContext.get())
    }
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { record ->
            // 单值：选中项若是已卸载的快捷方式，整个槽清空。
            val selected = record.action?.shortcutInfo()
            if (selected == null) {
                record
            } else {
                val stillThere = createLauncherInfos.any { it.qualifiedName == selected.qualifiedName } ||
                        launchLauncherInfos.any { launcher ->
                            launcher.shortcuts.any { it.qualifiedNameWithIntents == selected.qualifiedNameWithIntents }
                        }
                if (stillThere) record else UiState.SelectedRecord()
            }
        }
    }
    val finalCreateList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfo = selectedRecord.action?.shortcutInfo()
        createLauncherInfos.forEach { launcherInfo ->
            val isSelected = selectedShortcutInfo?.packageName == launcherInfo.packageName
            if (isSelected) list1.add(launcherInfo) else list2.add(launcherInfo)
        }
        list1 + list2
    }
    val finalLaunchList = withContext(Dispatchers.Default) {
        val list1 = mutableListOf<LauncherInfo>()
        val list2 = mutableListOf<LauncherInfo>()
        val selectedShortcutInfos = selectedRecord.action?.shortcutInfo()
        launchLauncherInfos.forEach { launcherInfo ->
            val isSelected = selectedShortcutInfos?.packageName == launcherInfo.packageName
            if (isSelected) list1.add(launcherInfo) else list2.add(launcherInfo)
        }
        list1 + list2
    }
    updateUiState {
        it.copy(
            createShortcuts = finalCreateList,
            launchShortcuts = finalLaunchList,
            selectedRecord = selectedRecord
        )
    }
    getUiState()
        .selectedRecord
        .action
        ?.shortcutInfo()
        ?.let { shortcut ->
            createLauncherInfos.find { it.qualifiedName == shortcut.qualifiedName }
                ?.let { addNewShortcut(it, shortcut) }
        }
}

internal suspend fun updateAppInfosBody(
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
) {
    val appInfos = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryApps(AppContext.get())
    }
    val disabledApps = DisabledAppQuery.queryDisabledApplicationsOnIo(AppContext.get())
    val normalPackageNames = appInfos.map { it.packageName }.toSet()
    val filteredDisabledApps = disabledApps.filter { it.packageName !in normalPackageNames }
    val mergedApps = mutableListOf<AppInfo>()
    mergedApps.addAll(appInfos)
    mergedApps.addAll(filteredDisabledApps)
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { record ->
            val selectedApp = record.action?.launchAppRef()
            if (selectedApp == null) {
                record
            } else if (mergedApps.any { it.qualifiedName == selectedApp.qualifiedName }) {
                record
            } else {
                UiState.SelectedRecord()   // 已卸载：槽清空
            }
        }
    }
    val finalList = withContext(Dispatchers.Default) {
        // 选中的应用置顶（沿用既有排序），单值下最多只有一个。
        val selectedRef = selectedRecord.action?.launchAppRef()
        val (selected, rest) = mergedApps.partition { appInfo ->
            selectedRef != null && selectedRef.qualifiedName == appInfo.qualifiedName
        }
        selected.map { it.copy(miniWindow = selectedRef?.miniWindow ?: it.miniWindow) } + rest
    }
    updateUiState {
        it.copy(
            apps = finalList,
            selectedRecord = selectedRecord
        )
    }
}

internal suspend fun loadDataBody(
    actionSelect: ActionSelect,
    onUpdateState: (update: (UiState) -> UiState) -> Unit
) {
    val buttons = GestureConfig.getButtons()
    val gestureSettings = TuneConfig.getGesture()
    val subGestures = GestureConfig.getSubGestures().subGestures
    val actionLibraryEntries = ActionLibrary.getSettings().entries
    // 全局动作面板列表：选择页一行一个具名面板。
    val actionSettings = TuneConfig.getAction()
    val actionPanels = actionSettings.actionPanels.panels
    val gridPanels = actionSettings.actionGridPanels.panels
    val isAutomation = actionSelect.automationRuleId.isNotBlank()
    // 规则即动作：选择页专属分组（只留启用；自动化模式剔除正在编辑的自身，禁自引用）。
    val rules = ActionCatalog.rulesForPicker(
        AutomationCenter.getRules(),
        excludeRuleId = if (isAutomation) actionSelect.automationRuleId else "",
    )

    // 两个排除集必须分开,它们管的不是同一件事:
    // - pickerExcluded 决定「有哪些动作类型可以被选」,必须恒非空。传空集合会让
    //   `typeId !in excluded` 恒真,整张定义表都变成可选行。
    // - libraryExcluded 只过滤动作库里已保存的条目。触钮路径保持不额外过滤,否则
    //   pickerExcluded 多排掉的 app.launch / accessibility.tap / accessibility.longPress
    //   是允许存进动作库的类型,会把用户已保存的条目从列表里藏掉。
    val pickerExcluded = if (isAutomation) {
        ActionDefinitions.automationExcludedTypeIds()
    } else {
        ActionDefinitions.pickerExcludedTypeIds()
    }
    val libraryExcluded = if (isAutomation) ActionDefinitions.automationExcludedTypeIds() else emptySet()
    val button = if (isAutomation) null else buttons.find { it.id == actionSelect.gestureButtonId }
    val subGesture = if (isAutomation) null else subGestures.find { it.id == actionSelect.subGestureId }
    onUpdateState { state ->
        state.copy(
            isAutomation = isAutomation,
            subGestures = subGestures,
            excludedSubGestureId = actionSelect.subGestureId,
            actionLibraryEntries = actionLibraryEntries.filter {
                // 自动化放行带固定坐标的 tap/longPress 条目（参数优先于触点，见执行器）；
                // 无坐标、或勾了「使用触发位置坐标」的条目，在无触点的自动化路径必
                // InvalidParameter，继续藏（hasPointParams 对两种都返回 false）。
                it.typeId !in libraryExcluded || it.storedAction.hasPointParams()
            },
            excludedTypeIds = pickerExcluded,
            rules = rules,
            actionPanels = actionPanels,
            gridPanels = gridPanels,
            isPanelItemMode = actionSelect.actionPanelId.isNotBlank() || actionSelect.gridPanelId.isNotBlank(),
        )
    }
    val action = when {
        // 面板项模式：读回当前面板项（改变体时预选原变体），不碰触钮槽。
        actionSelect.actionPanelId.isNotBlank() -> {
            val index = actionSelect.actionPanelItemIndex
            val panel = actionPanels.firstOrNull { it.id == actionSelect.actionPanelId }
            val item = if (index >= 0) panel?.items?.getOrNull(index) else null
            item?.longPressVariant() ?: item
        }
        // 网格面板只进不出：进选择页只会是加项，没有可预选的旧动作。
        actionSelect.gridPanelId.isNotBlank() -> null
        // 悬浮球槽：与触钮路径互斥，先判它，否则会掉进 button/subGesture 的 null 分支。
        actionSelect.floatBallSlot == FloatBallSlot.TAP -> actionSettings.floatBall.tapAction
        isAutomation -> loadAutomationAction(actionSelect)
        subGesture != null -> subGesture.actionFor(actionSelect.triggerType, actionSelect.direction)
        button != null -> button.actionFor(actionSelect.triggerType, actionSelect.direction)
        else -> null
    }
    onUpdateState { state ->
        state.copy(selectedRecord = state.selectedRecord.selectOne(action))
    }
}

private fun GestureButton.actionFor(triggerType: GestureTriggerType, direction: hunoia.luno.config.model.GestureDirection): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Tap -> tapActions
        GestureTriggerType.DoubleTap -> doubleTapActions
        GestureTriggerType.LongPress -> longPressActions
        GestureTriggerType.Slide -> slideActions.actionBy(direction)
        GestureTriggerType.SlideHold -> slideHoldActions.actionBy(direction)
        GestureTriggerType.LongSlide -> longSlideActions.actionBy(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActions.actionBy(direction)
    }
}

private fun GestureButton.withAction(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    action: StoredAction?
): GestureButton {
    return when (triggerType) {
        GestureTriggerType.Tap -> copy(tapActions = action)
        GestureTriggerType.DoubleTap -> copy(doubleTapActions = action)
        GestureTriggerType.LongPress -> copy(longPressActions = action)
        GestureTriggerType.Slide -> copy(slideActions = slideActions.withAction(direction, action))
        GestureTriggerType.SlideHold -> copy(slideHoldActions = slideHoldActions.withAction(direction, action))
        GestureTriggerType.LongSlide -> copy(longSlideActions = longSlideActions.withAction(direction, action))
        GestureTriggerType.LongSlideHold -> copy(longSlideHoldActions = longSlideHoldActions.withAction(direction, action))
    }
}

private fun hunoia.luno.config.model.SubGesture.actionFor(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection
): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Slide -> slideActionFor(direction)
        GestureTriggerType.SlideHold -> slideHoldActionFor(direction)
        GestureTriggerType.LongSlide -> longSlideActionFor(direction)
        GestureTriggerType.LongSlideHold -> longSlideHoldActionFor(direction)
        else -> null
    }
}

private fun hunoia.luno.config.model.SubGesture.withAction(
    triggerType: GestureTriggerType,
    direction: hunoia.luno.config.model.GestureDirection,
    action: StoredAction?
): hunoia.luno.config.model.SubGesture {
    return when (triggerType) {
        GestureTriggerType.Slide -> withSlideAction(direction, action)
        GestureTriggerType.SlideHold -> withSlideHoldAction(direction, action)
        GestureTriggerType.LongSlide -> withLongSlideAction(direction, action)
        GestureTriggerType.LongSlideHold -> withLongSlideHoldAction(direction, action)
        else -> this
    }
}
