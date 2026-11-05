package hunoia.luno.picker


import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.core.domain.launchAppRef
import hunoia.luno.core.domain.longPressVariant
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.core.domain.withLongPressVariant
import hunoia.luno.domain.action.ActionLibrary
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.GridCell
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.GridPanelItemLayout
import hunoia.luno.config.model.withOrientation
import hunoia.luno.core.AppContext
import hunoia.luno.gridpanel.geometry.gridPanelVisibleRows
import hunoia.luno.gridpanel.widget.PanelAppWidgetHost
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.identity
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.shortcutInfo

internal fun List<StoredAction>.shortcutIconPaths(): List<String> =
    flatMap { ShortcutIconFiles.iconPathsOf(it) }

internal fun StoredAction.shortcutIconPaths(): List<String> = ShortcutIconFiles.iconPathsOf(this)

internal fun tryDeleteShortcutIcons(old: StoredAction?, new: StoredAction?) {
    val newPaths = listOfNotNull(new).flatMap { it.shortcutIconPaths() }.toSet()
    listOfNotNull(old).flatMap { it.shortcutIconPaths() }
        .filter { it !in newPaths }
        .forEach(ShortcutIconFiles::delete)
}

/** 落盘结果：区分「成功」「页满」「目标消失」，VM 按此给不同提示，不假报已保存。 */
internal enum class SaveOutcome { Saved, PageFull, TargetMissing }

/**
 * 选择页的统一落盘入口。TargetMissing = 目标面板/规则在选择期间被删，
 * 数据未写入；调用方按目标类型提示，不报「保存成功」。
 */
internal suspend fun saveSettingsAction(
    actionSelect: ActionSelect,
    getUiState: () -> UiState,
    updateUiState: ((UiState) -> UiState) -> Unit
): SaveOutcome {
    // 单值语义：传 null 即「无动作」（删键/存 null），选别的传那一个。
    val picked = getUiState().selectedRecord.action
    if (actionSelect.actionPanelId.isNotBlank()) {
        return saveActionPanelItem(actionSelect, picked)
    }
    if (actionSelect.gridPanelId.isNotBlank()) {
        return saveGridPanelItem(actionSelect, picked)
    }
    if (actionSelect.floatBallSlot.isNotBlank()) {
        saveFloatBallSlot(actionSelect.floatBallSlot, picked)
        return SaveOutcome.Saved
    }
    if (actionSelect.subGestureId.isNotBlank()) {
        GestureConfig.updateSubGestures { settings ->
            settings.copy(
                subGestures = settings.subGestures.map { gesture ->
                    if (gesture.id != actionSelect.subGestureId) return@map gesture
                    val oldAction = gesture.actionFor(actionSelect.triggerType, actionSelect.slot)
                    tryDeleteShortcutIcons(oldAction, picked)
                    gesture.withAction(actionSelect.triggerType, actionSelect.slot, picked)
                }
            )
        }
        return SaveOutcome.Saved
    }
    GestureConfig.updateButtons { list ->
        val mutableList = list.toMutableList()
        val index = mutableList.indexOfFirst { it.id == actionSelect.gestureButtonId }
        if (index < 0) return@updateButtons mutableList
        val oldAction = mutableList[index].actionFor(actionSelect.triggerType, actionSelect.slot)
        tryDeleteShortcutIcons(oldAction, picked)
        mutableList[index] = mutableList[index]
            .withAction(actionSelect.triggerType, actionSelect.slot, picked)
        mutableList
    }
    return SaveOutcome.Saved
}

/**
 * 面板项落盘：`actionPanelItemIndex < 0` 是新增（追加），>=0 是替换该项的长按变体。
 * 返回 TargetMissing = 面板在选择期间被删（数据未写入），放弃添加不算失败。
 */
private suspend fun saveActionPanelItem(actionSelect: ActionSelect, picked: StoredAction?): SaveOutcome {
    val panelId = actionSelect.actionPanelId
    val index = actionSelect.actionPanelItemIndex
    var outcome = SaveOutcome.Saved
    TuneConfig.updateAction { settings ->
        val panel = settings.actionPanels.panelById(panelId) ?: run {
            outcome = SaveOutcome.TargetMissing
            return@updateAction settings
        }
        val items = when {
            // 新增：选了「无动作」等于放弃本次添加，不改面板。
            picked == null || picked.typeId == Action.NONE_TYPE_ID -> return@updateAction settings
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
    return outcome
}

/**
 * 网格面板项落盘：新增项落到 [ActionSelect.gridPanelPageIndex] 指定页的首个空位
 *（只在本页内扫，跨页不占位）。只进不出，无「替换已有项」分支，也无长按变体。
 */
private suspend fun saveGridPanelItem(actionSelect: ActionSelect, picked: StoredAction?): SaveOutcome {
    // 选了「无动作」等于放弃本次操作，不改面板，也不当失败提示。
    if (picked == null || picked.typeId == Action.NONE_TYPE_ID) return SaveOutcome.Saved
    var outcome = SaveOutcome.PageFull
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            outcome = SaveOutcome.TargetMissing
            return@updateAction settings
        }
        val ctx = gridContext(panel, actionSelect.gridPanelPageIndex)
        val pageOccupied = ctx.items.filter { it.pageIndex == ctx.pageIndex }.maxOfOrNull { it.bottom } ?: 0
        val rows = ctx.rows.coerceAtLeast(pageOccupied + 1) // 行数无上限：永远多留一行装 1×1 动作项
        val free = GridPanelItemLayout.firstFreeSlot(
            items = ctx.items.filter { it.pageIndex == ctx.pageIndex },
            columns = ctx.columns,
            rows = rows,
            spanX = 1,
            spanY = 1,
        ) ?: return@updateAction settings
        outcome = SaveOutcome.Saved
        settings.copy(
            actionGridPanels = settings.actionGridPanels.withPanel(
                panel.withOrientation(ctx.landscape) { orientation ->
                    orientation.copy(
                        items = orientation.items + GridPanelItem(
                            x = free.x,
                            y = free.y,
                            pageIndex = ctx.pageIndex,
                            kind = GridPanelItem.Kind.Action(listOf(picked.withoutVariantKey())),
                        ),
                    )
                },
            ),
        )
    }
    return outcome
}

/**
 * 追加语义的落点：新项放到本页已占内容正下方（y = max(bottom)），宽度不满整行时
 * 在那一行内从左往右找空位，被同排项挡住则顺延下一行。
 * [rows] 是「塞进现有网格空隙」的上限；页面行数是派生的，追加时会把有效行数
 * 抬到至少装得下 span，所以底部追加不该出现「放不下」。
 */
private fun bottomSlot(
    items: List<GridPanelItem>,
    columns: Int,
    rows: Int,
    spanX: Int,
    spanY: Int,
): GridCell? {
    // span 大于列数时 `0..(columns - spanX)` 是空区间，会白扫到行尾才返回 null；
    // 调用方必须先把 span 夹进面板，这里直接拒绝非法输入。
    if (spanX > columns || spanY < 1) return null
    var y = items.maxOfOrNull { it.bottom } ?: 0
    val effectiveRows = maxOf(rows, y + spanY)
    while (y + spanY <= effectiveRows) {
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

/** 网格落盘函数共享的落点上下文：横竖屏 → geometry → 列数夹取 → 页码夹取 → 有效行数。 */
private data class GridContext(
    val columns: Int,
    val pageIndex: Int,
    val rows: Int,
    val landscape: Boolean,
    /** 当前朝向的项列表（横竖屏分开后按朝向取）。 */
    val items: List<GridPanelItem>,
)

private fun gridContext(panel: GridPanel, requestedPageIndex: Int): GridContext {
    val configuration = AppContext.get().resources.configuration
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val orientation = panel.orientation(landscape)
    val geo = orientation.geometry
    val columns = geo.columnCount.coerceIn(GridPanelDefaults.MinColumnCount, GridPanelDefaults.MaxColumnCount)
    val pageIndex = requestedPageIndex.coerceIn(0, orientation.derivedPageCount)
    return GridContext(
        columns = columns,
        pageIndex = pageIndex,
        rows = orientation.effectiveGridRows(
            pageIndex,
            gridPanelVisibleRows(
                screenWdp = configuration.screenWidthDp.toFloat(),
                screenHdp = configuration.screenHeightDp.toFloat(),
                columns = columns,
                cardWidthFraction = geo.cardWidthFraction,
                cardHeightFraction = geo.cardHeightFraction,
            ),
        ),
        landscape = landscape,
        items = orientation.items,
    )
}

/**
 * 降格序列与拖拽的重排思路分开：加项只填空（firstFreeSlot），放不下自动降档
 * 占更小的空位 —— 重排是编辑态拖拽的语义，加项不该惊动已有项。
 * 面积从大到小逐档试，返回首个放得下的（格子，实际 span）。
 */
private fun degradeSlot(
    pageItems: List<GridPanelItem>,
    columns: Int,
    rows: Int,
    spanX: Int,
    spanY: Int,
): Pair<GridCell, WidgetSpan>? {
    val candidates = buildList {
        for (h in spanY downTo 1) {
            for (w in spanX downTo 1) {
                // firstFreeSlot 内部会把宽度夹到列数，但返回的 span 仍是原值；
                // 候选本身先夹好，落盘就不会出现 spanX > 列数的坏数据。
                add(WidgetSpan(w.coerceIn(1, columns.coerceAtLeast(1)), h))
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
        return cell to span
    }
    return null
}

/** 行数派生且追加无上限：结果只有「存进去了」和「面板不存在」两种。 */
internal enum class GridPanelSaveResult { Saved, NoPanel }

/**
 * 第三方小部件落盘：原尺寸先追加到底部，放不下则面积从大到小逐档降格填空。
 * 返回 false 时调用方必须 Toast 并回收 appWidgetId。
 */
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
            hunoia.luno.core.LunoLog.w("GridPanelSave", "widget: panel not found requested=${actionSelect.gridPanelId} " +
            "available=${settings.actionGridPanels.panels.map { it.id to it.name }} select=${actionSelect}")
            return@updateAction settings
        }
        val ctx = gridContext(panel, actionSelect.gridPanelPageIndex)
        val pageItems = ctx.items.filter { it.pageIndex == ctx.pageIndex }
        // 入口再夹一次：即使调用方传来超列 span，也不能落成坏数据。
        val reqX = spanX.coerceIn(1, ctx.columns)
        val reqY = spanY.coerceAtLeast(1)
        // 追加优先：原尺寸先试「放到最下面」；实在不行才走降档扫空位兜底。
        // 行数派生且无上限：追加时把 rows 抬到装得下，运行态页面随内容长高。
        bottomSlot(pageItems, ctx.columns, maxOf(ctx.rows, pageItems.maxOfOrNull { it.bottom } ?: 0), reqX, reqY)?.let { cell ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.withOrientation(ctx.landscape) { orientation ->
                        orientation.copy(
                            items = orientation.items + GridPanelItem(
                                x = cell.x,
                                y = cell.y,
                                spanX = reqX,
                                spanY = reqY,
                                pageIndex = ctx.pageIndex,
                                kind = GridPanelItem.Kind.AppWidget(
                                    appWidgetId = appWidgetId,
                                    provider = provider,
                                    minSpanX = reqX,
                                    minSpanY = reqY,
                                ),
                            ),
                        )
                    },
                ),
            )
        }
        degradeSlot(pageItems, ctx.columns, ctx.rows, reqX, reqY)?.let { (cell, span) ->
            result = GridPanelSaveResult.Saved
            // 必须 return copy 的结果本身：return 原 settings 会让落盘形同没写。
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.withOrientation(ctx.landscape) { orientation ->
                        orientation.copy(
                            items = orientation.items + GridPanelItem(
                                x = cell.x,
                                y = cell.y,
                                spanX = span.spanX,
                                spanY = span.spanY,
                                pageIndex = ctx.pageIndex,
                                kind = GridPanelItem.Kind.AppWidget(
                                    appWidgetId = appWidgetId,
                                    provider = provider,
                                    // 冻结实际落位的 span（不是 provider 声明的最小值）：
                                    // minSpan 会经 options 传给 provider，必须与真实渲染一致。
                                    // 页满降档低于 provider 声明最小值是本路径的既定权衡。
                                    minSpanX = span.spanX,
                                    minSpanY = span.spanY,
                                ),
                            ),
                        )
                    },
                ),
            )
        }
        settings
    }
    if (result != GridPanelSaveResult.Saved) {
        hunoia.luno.core.LunoLog.w("GridPanelSave", "widget not placed: result=$result span=$spanX x $spanY")
        PanelAppWidgetHost.get().release(appWidgetId)
    }
    return result
}

/**
 * 内置小部件落盘：同款「只填空 + 放不下降档」，无宿主 id 可回收。
 * 默认 span 4×1（def 声明），列数不够时逐档降到 1×1。
 * [params] 是每实例配置（见 architecture §9.2）：习惯部件传 `HabitWidgetParams` JSON，无参部件传 null。
 */
internal suspend fun saveGridPanelInternalWidget(
    actionSelect: ActionSelect,
    widgetId: String,
    params: String? = null,
): GridPanelSaveResult {
    val def = hunoia.luno.gridpanel.internal.InternalWidgetDefs.firstOrNull { it.id == widgetId }
    if (def == null) {
        hunoia.luno.core.LunoLog.w("GridPanelSave", "internal widget: unknown id=$widgetId")
        return GridPanelSaveResult.NoPanel
    }
    var result = GridPanelSaveResult.NoPanel
    TuneConfig.updateAction { settings ->
        val panel = settings.actionGridPanels.panelById(actionSelect.gridPanelId) ?: run {
            hunoia.luno.core.LunoLog.w("GridPanelSave", "internal: panel not found id=${actionSelect.gridPanelId}")
            return@updateAction settings
        }
        val ctx = gridContext(panel, actionSelect.gridPanelPageIndex)
        val pageItems = ctx.items.filter { it.pageIndex == ctx.pageIndex }
        // 内置部件的默认 span 是 4×N，面板列数可能只有 2/3：必须先把宽度夹进面板，
        // 否则落盘的 spanX > columns，手势层的尺寸/命中/重排全部会跟着坏。
        val reqX = def.spanX.coerceIn(1, ctx.columns)
        val reqY = def.spanY.coerceAtLeast(1)
        // 追加优先：原尺寸先试「放到最下面」；行数无上限，追加时同步抬 rows。
        bottomSlot(pageItems, ctx.columns, maxOf(ctx.rows, pageItems.maxOfOrNull { it.bottom } ?: 0), reqX, reqY)?.let { cell ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.withOrientation(ctx.landscape) { orientation ->
                        orientation.copy(
                            items = orientation.items + GridPanelItem(
                                x = cell.x,
                                y = cell.y,
                                spanX = reqX,
                                spanY = reqY,
                                pageIndex = ctx.pageIndex,
                                kind = GridPanelItem.Kind.Internal(widgetId = widgetId, showBackground = false, params = params),
                            ),
                        )
                    },
                ),
            )
        }
        degradeSlot(pageItems, ctx.columns, ctx.rows, reqX, reqY)?.let { (cell, span) ->
            result = GridPanelSaveResult.Saved
            return@updateAction settings.copy(
                actionGridPanels = settings.actionGridPanels.withPanel(
                    panel.withOrientation(ctx.landscape) { orientation ->
                        orientation.copy(
                            items = orientation.items + GridPanelItem(
                                x = cell.x,
                                y = cell.y,
                                spanX = span.spanX,
                                spanY = span.spanY,
                                pageIndex = ctx.pageIndex,
                                kind = GridPanelItem.Kind.Internal(widgetId = widgetId, showBackground = false, params = params),
                            ),
                        )
                    },
                ),
            )
        }
        settings
    }
    return result
}

/** 悬浮球槽位落盘。按槽名分发而非写死，后续开放长按/滑动槽只加 case。 */
private suspend fun saveFloatBallSlot(slot: String, picked: StoredAction?) {
    if (!FloatBallSlot.isValid(slot)) return
    TuneConfig.updateAction { settings ->
        settings.copy(
            floatBall = settings.floatBall.copy(
                tapAction = picked?.takeIf { it.typeId != Action.NONE_TYPE_ID },
            ),
        )
    }
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
    // 补偿：queryShortcutActivities 只返回支持快捷方式创建的应用行（不带 shortcuts），
    // 已选中的快捷方式行必须单独补进它的 launcher 缓存，否则列表里看不到自己选了啥。
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
    // 统一清单：主用户 + 分身 + 禁用应用 +（按开关）无桌面入口系统包。
    val mergedApps = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryAppsIncludingDisabled(AppContext.get())
    }
    val selectedRecord = withContext(Dispatchers.Default) {
        getUiState().selectedRecord.let { record ->
            val selectedApp = record.action?.launchAppRef()
            if (selectedApp == null) {
                record
            } else if (mergedApps.any { it.identity == selectedApp.identity }) {
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
            selectedRef != null && selectedRef.identity == appInfo.identity
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
    // pickerExcluded 决定「有哪些类型可被选」，必须恒非空（空集让整张定义表都可选）。
    val pickerExcluded = ActionDefinitions.pickerExcludedTypeIds()
    val button = buttons.find { it.id == actionSelect.gestureButtonId }
    val subGesture = subGestures.find { it.id == actionSelect.subGestureId }
    onUpdateState { state ->
        state.copy(
            subGestures = subGestures,
            excludedSubGestureId = actionSelect.subGestureId,
            actionLibraryEntries = actionLibraryEntries,
            excludedTypeIds = pickerExcluded,
            actionPanels = actionPanels,
            gridPanels = gridPanels,
            isPanelItemMode = actionSelect.actionPanelId.isNotBlank() || actionSelect.gridPanelId.isNotBlank(),
            currentGridPanelId = actionSelect.gridPanelId,
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
        subGesture != null -> subGesture.actionFor(actionSelect.triggerType, actionSelect.slot)
        button != null -> button.actionFor(actionSelect.triggerType, actionSelect.slot)
        else -> null
    }
    onUpdateState { state ->
        state.copy(selectedRecord = state.selectedRecord.selectOne(action))
    }
}

private fun GestureButton.actionFor(triggerType: GestureTriggerType, slot: Int): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Tap -> tapActions
        GestureTriggerType.DoubleTap -> doubleTapActions
        GestureTriggerType.LongPress -> longPressActions
        GestureTriggerType.Slide -> slideActions.actionBy(slot)
        GestureTriggerType.SlideHold -> slideHoldActions.actionBy(slot)
        GestureTriggerType.LongSlide -> longSlideActions.actionBy(slot)
        GestureTriggerType.LongSlideHold -> longSlideHoldActions.actionBy(slot)
    }
}

private fun GestureButton.withAction(
    triggerType: GestureTriggerType,
    slot: Int,
    action: StoredAction?
): GestureButton {
    return when (triggerType) {
        GestureTriggerType.Tap -> copy(tapActions = action)
        GestureTriggerType.DoubleTap -> copy(doubleTapActions = action)
        GestureTriggerType.LongPress -> copy(longPressActions = action)
        GestureTriggerType.Slide -> copy(slideActions = slideActions.withAction(slot, action))
        GestureTriggerType.SlideHold -> copy(slideHoldActions = slideHoldActions.withAction(slot, action))
        GestureTriggerType.LongSlide -> copy(longSlideActions = longSlideActions.withAction(slot, action))
        GestureTriggerType.LongSlideHold -> copy(longSlideHoldActions = longSlideHoldActions.withAction(slot, action))
    }
}

private fun hunoia.luno.config.model.SubGesture.actionFor(
    triggerType: GestureTriggerType,
    slot: Int
): StoredAction? {
    return when (triggerType) {
        GestureTriggerType.Slide -> slideActionFor(slot)
        GestureTriggerType.SlideHold -> slideHoldActionFor(slot)
        GestureTriggerType.LongSlide -> longSlideActionFor(slot)
        GestureTriggerType.LongSlideHold -> longSlideHoldActionFor(slot)
        else -> null
    }
}

private fun hunoia.luno.config.model.SubGesture.withAction(
    triggerType: GestureTriggerType,
    slot: Int,
    action: StoredAction?
): hunoia.luno.config.model.SubGesture {
    return when (triggerType) {
        GestureTriggerType.Slide -> withSlideAction(slot, action)
        GestureTriggerType.SlideHold -> withSlideHoldAction(slot, action)
        GestureTriggerType.LongSlide -> withLongSlideAction(slot, action)
        GestureTriggerType.LongSlideHold -> withLongSlideHoldAction(slot, action)
        else -> this
    }
}
