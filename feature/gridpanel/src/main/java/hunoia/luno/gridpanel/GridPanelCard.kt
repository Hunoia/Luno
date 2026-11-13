package hunoia.luno.gridpanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import hunoia.luno.gridpanel.R
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.gridpanel.core.EdgeAutoScroll
import hunoia.luno.gridpanel.core.GridPanelState
import hunoia.luno.gridpanel.core.PageDrop
import hunoia.luno.gridpanel.geometry.GridMetrics
import hunoia.luno.gridpanel.geometry.IndicatorHeight
import hunoia.luno.gridpanel.geometry.ResizeCorner
import hunoia.luno.gridpanel.geometry.choosePanelResizeCorner
import hunoia.luno.gridpanel.geometry.isLeft
import hunoia.luno.gridpanel.geometry.isTop
import hunoia.luno.gridpanel.geometry.GridPadding
import hunoia.luno.gridpanel.ui.GridItemActions
import hunoia.luno.gridpanel.ui.GridPageIndicator
import hunoia.luno.gridpanel.ui.GridPanelLayer
import hunoia.luno.gridpanel.ui.PanelBlurHost
import hunoia.luno.gridpanel.ui.BlurState
import hunoia.luno.gridpanel.ui.PageDotSlot
import hunoia.luno.config.model.SurfacePlacement
import hunoia.luno.config.model.coerced
import hunoia.luno.config.model.resolveTopLeft
import hunoia.luno.gridpanel.ui.ItemResizeHandle
import hunoia.luno.gridpanel.ui.PageEllipsisDots
import hunoia.luno.gridpanel.ui.PageEllipsisSize
import hunoia.luno.gridpanel.ui.pageDotCentersPx
import hunoia.luno.gridpanel.ui.pageDotSlots
import hunoia.luno.gridpanel.ui.gridPanelCardColor
import hunoia.luno.bridge.ui.hapticTick
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.panel.panelAppear
import hunoia.luno.ui.theme.PanelAppearSlide
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.component.PressFloatingActionButton


/** 拖拽到距可视区上下沿这么多 dp 内开始自动滚动。 */
private val EdgeScrollBand = 56.dp

/** 自动滚动上限步进（dp/帧，约 960dp/s），实际按越界深度在 25%~100% 间取。 */
private val EdgeScrollStep = 16.dp

/**
 * 卡片容器：几何 → 尺寸 → 位置动画 → HorizontalPager。
 * 每页的 [ScrollState] 在这里创建并长期持有（map + getOrPut）：放进 pager 的
 * page lambda 里 remember，页面被回收后边缘滚动协程可能握着废弃实例。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridPanelCard(
    panelId: String,
    editMode: Boolean,
    /** 派生页数（不含编辑态虚页）。 */
    pageCount: Int,
    /** 是否显示页指示点（派生页数 > 1，或编辑态有虚页）。 */
    showIndicator: Boolean,
    /** 当前生效的循环映射页数（编辑态 +1，见 Content）：真实页 = pagerIndex % mappingPages。 */
    mappingPages: Int,
    /** 跟随触发点的三个边距（dp）：横向贴触发侧，竖向跟随触发点。 */
    placement: SurfacePlacement,
    /** 卡片圆角（px）= 屏幕圆角 − 左右边距。 */
    cornerPx: Float,
    pagerState: PagerState,
    /** 卡片级几何（定位/卡面尺寸用，不含页差异）。 */
    metrics: GridMetrics,
    /** 按页几何：行数 = max(可见行数，本页占用) + 编辑态尾行，各行页不再同高。 */
    metricsForPage: (Int) -> GridMetrics,
    anchorX: Int,
    anchorY: Int,
    insetsTop: Int,
    insetsBottom: Int,
    appearance: Float,
    /** 拖拽中把卡面整体隐去（只改 alpha，不改布局：缩放会让行列坐标跟着变）。 */
    hiddenForDrag: Boolean = false,
    cardAlpha: Float,
    /** 面板/内置部件背景模糊半径（px，0 = 关闭）。 */
    blurRadiusPx: Int = 0,
    state: GridPanelState,
    itemActions: (pageIndex: Int) -> GridItemActions,
    onSelectPage: (Int) -> Unit,
    onAdd: () -> Unit,
    /** 面板级缩放期间的临时位置：保持对角固定，避免配置回流前跳位。 */
    positionOverride: Offset? = null,
    panelResizing: Boolean = false,
    onPanelResizeStart: ((ResizeCorner, Float, Float) -> Unit)? = null,
    onPanelResizeDelta: ((Offset) -> Unit)? = null,
    onPanelResizeEnd: (() -> Unit)? = null,
    /** 双指调列数的实时目标（null = 未在调）；仅用于中间提示。 */
    pinchColumns: Int? = null,
    /** (目标列数, 是否松手收尾)。 */
    onPinchColumns: ((Int, Boolean) -> Unit)? = null,
    /**
     * 非 null = 单功能面板：跳过 pager / 网格层 / 页指示 / 加项 FAB，只渲染这一块整页内容。
     * 卡片定位、进场、模糊、旋转与缩放手柄仍由本卡片统一负责。
     */
    singleContent: (@Composable (GridMetrics) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val bandPx = with(density) { EdgeScrollBand.toPx() }
    val stepPx = with(density) { EdgeScrollStep.toPx() }
    val indicatorPx = with(density) { IndicatorHeight.toPx() }
    val gridPadPx = with(density) { GridPadding.toPx() }
    val safePlacement = placement.coerced()
    val target = safePlacement.resolveTopLeft(
        anchorX = anchorX,
        anchorY = anchorY,
        widthPx = metrics.cardW,
        heightPx = metrics.cardH,
        screenWidthPx = metrics.screenW.roundToInt(),
        screenHeightPx = metrics.screenH.roundToInt(),
        safeTopPx = insetsTop,
        safeBottomPx = insetsBottom,
        density = density.density,
    )
    val targetPosX = target.x
    val targetPosY = target.y
    // 出现 / 收口走位移（ui-guidelines §3「面板」）：首尾位置相同、只往触发锚点方向
    // 偏移 [PanelAppearSlide]，因此半程就能看出「从手指落下处展开」，不是整层淡入淡出。
    // 方向取「卡片中心 → 触发点」的单位向量；anchor 无意义（0,0）时就从左上滑出。
    val appearSlidePx = with(density) { PanelAppearSlide.toPx() }
    val appearDir = remember(targetPosX, targetPosY, anchorX, anchorY, metrics.cardW, metrics.cardH) {
        val dx = anchorX - (targetPosX + metrics.cardW / 2f)
        val dy = anchorY - (targetPosY + metrics.cardH / 2f)
        val len = hypot(dx, dy).coerceAtLeast(1f)
        Offset(dx / len, dy / len)
    }
    // 缩放锚点 = 触发点在卡面内的归一化位置（触发点落在卡外时夹到最近的边 / 角）：
    // 进场从指尖处展开、退场缩回指尖，而不是绕卡片中心缩放（ui-guidelines §5.1）。
    val appearOrigin = remember(targetPosX, targetPosY, anchorX, anchorY, metrics.cardW, metrics.cardH) {
        TransformOrigin(
            pivotFractionX = ((anchorX - targetPosX) / metrics.cardW).coerceIn(0f, 1f),
            pivotFractionY = ((anchorY - targetPosY) / metrics.cardH).coerceIn(0f, 1f),
        )
    }
    val sideMarginPx = with(density) { safePlacement.sideMarginDp.dp.toPx() }
    val topGapPx = with(density) { safePlacement.topGapDp.dp.toPx() }
    val bottomGapPx = with(density) { safePlacement.bottomGapDp.dp.toPx() }
    // 面板级缩放手柄：优先落在离屏边最远的角。拖动期间冻结起手角，尺寸变化
    // 不能把正在拖的手柄换到另一个角去。
    var resizeCornerOverride by remember(panelId) { mutableStateOf<ResizeCorner?>(null) }
    LaunchedEffect(editMode) {
        if (!editMode) resizeCornerOverride = null
    }
    val currentColumns = rememberUpdatedState(metrics.columns)
    val pinchColumnsCallback = rememberUpdatedState(onPinchColumns)
    val cornerLeft = positionOverride?.x ?: targetPosX
    val cornerTop = positionOverride?.y ?: targetPosY
    val resizeCorner = resizeCornerOverride ?: choosePanelResizeCorner(
        cardLeft = cornerLeft,
        cardTop = cornerTop,
        cardWidth = metrics.cardW,
        cardHeight = metrics.cardH,
        screenWidth = metrics.screenW,
        screenHeight = metrics.screenH,
        insetsTop = insetsTop,
        insetsBottom = insetsBottom,
        sideMarginPx = sideMarginPx,
        topGapPx = topGapPx,
        bottomGapPx = bottomGapPx,
    )
    // 转屏 / 改网格尺寸时卡片要挪位置：首帧直接对齐（别从 0 飞过来），之后平滑过渡。
    val posAnimX = remember { Animatable(targetPosX) }
    val posAnimY = remember { Animatable(targetPosY) }
    val drawX = positionOverride?.x ?: posAnimX.value
    val drawY = positionOverride?.y ?: posAnimY.value
    LaunchedEffect(targetPosX, targetPosY) {
        if (appearance < 0.99f) {
            posAnimX.snapTo(targetPosX)
            posAnimY.snapTo(targetPosY)
        } else {
            posAnimX.animateTo(
                targetPosX,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            posAnimY.animateTo(
                targetPosY,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val pageScrolls = remember(panelId) { mutableMapOf<Int, ScrollState>() }
    // 页数被调小后清掉越界页的滚动状态，避免再调大时复用旧滚动位置。
    LaunchedEffect(pageCount, panelId) {
        pageScrolls.keys.retainAll(0 until pageCount)
    }

    // 循环：pager 页数恒为极大值（见 Content）；真实页 = index % mappingPages，
    // 编辑态末尾虚页 ≡ 派生页数。单页取模归 0。
    fun realPage(index: Int): Int = index.mod(mappingPages)

    // 顶部指示条与拾取浮层共用一套窗口：按卡片宽度定最大槽位，以**当前页**为中心。
    // 拖拽中当前页不变，x → 页 的映射才稳定；窗口不能跟目标页走（越滑越远）。
    val activePage = realPage(pagerState.currentPage)
    val maxIndicatorSlots = remember(metrics.cardW, density) {
        val available = metrics.cardW - with(density) { 88.dp.toPx() }
        val slot = with(density) { 24.dp.toPx() }
        ((available / slot).toInt()).coerceIn(3, 9)
    }
    val indicatorSlots = remember(pageCount, editMode, activePage, maxIndicatorSlots) {
        pageDotSlots(
            pageCount = pageCount,
            includeVirtual = editMode,
            centerPage = activePage,
            maxSlots = maxIndicatorSlots,
        )
    }
    val indicatorSlotsRef = rememberUpdatedState(indicatorSlots)
    val indicatorCenters = remember(indicatorSlots, pageCount, metrics.cardW, density) {
        pageDotCentersPx(indicatorSlots, pageCount, metrics.cardW, density.density)
    }
    val indicatorCentersRef = rememberUpdatedState(indicatorCenters)
    val indicatorPageCountRef = rememberUpdatedState(pageCount)
    val indicatorHitSlop = with(density) { 8.dp.toPx() }
    val indicatorHitDistance = with(density) { 10.dp.toPx() }

    /** 手指是否在顶部指示条热区（允许上下各 8dp 手措误差）。 */
    fun inIndicatorBand(cardY: Float): Boolean =
        cardY >= -indicatorHitSlop && cardY <= indicatorPx + indicatorHitSlop

    /** 拖拽位置（卡片坐标，原点在卡左上角）命中的页点：null = 不在页点热区。 */
    fun pageDropAt(cardX: Float, cardY: Float): PageDrop? {
        if (!inIndicatorBand(cardY)) return null
        val slots = indicatorSlotsRef.value
        val centers = indicatorCentersRef.value
        val pageCountNow = indicatorPageCountRef.value
        var best: PageDrop? = null
        var bestDistance = indicatorHitDistance
        slots.forEachIndexed { slotIndex, slot ->
            if (slot !is PageDotSlot.Page) return@forEachIndexed
            val distance = abs(cardX - centers[slotIndex])
            if (distance <= bestDistance) {
                best = PageDrop(slot.index, isVirtual = slot.index >= pageCountNow)
                bestDistance = distance
            }
        }
        return best
    }

    // 拖拽手指在卡片坐标里的位置：只在进入指示条热区时弹出「页拾取器」。
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    val pointerNow = dragPointer
    val pickerVisible = state.draggingId != null && pointerNow != null &&
        inIndicatorBand(pointerNow.y)
    // 退场、拖拽隐去时把 blur 一起收掉，避免卡片已经看不见、背景还在糊。
    val blurActive = blurRadiusPx > 0 && !hiddenForDrag && appearance > 0.01f
    // 模糊状态三态：**没挂上**不等于**挂不上**。只有确定挂不上（重试耗尽 / 隐藏 API 被拒）
    // 才提高不透明度兜底；入场最初几帧只是还没 attach，用 fallback 会在面板一亮时把
    // 卡片从 ~92% 闪到 75%（浅色主题 surfaceContainerHigh 接近白，读作「白色闪一下」）。
    val blurState = remember(panelId) { mutableStateOf(BlurState.Pending) }
    val cardColor = gridPanelCardColor
    val cardBackgroundAlpha = when {
        blurRadiusPx <= 0 -> cardAlpha
        blurState.value == BlurState.Unavailable -> maxOf(cardAlpha, 0.92f)
        else -> cardAlpha
    }

    Box(
        Modifier
            .offset { IntOffset(drawX.roundToInt(), drawY.roundToInt()) }
            .size(
                with(density) { metrics.cardW.toDp() },
                with(density) { metrics.cardH.toDp() },
            )
            .panelAppear(
                progress = { appearance },
                originForSize = { appearOrigin },
                slideForSize = { appearDir * appearSlidePx },
                hidden = hiddenForDrag,
            )
            // 编辑态双指捏合调列数：只在卡面上生效，且要求两根手指，避免抢走单项拖拽。
            // 捏合命中区放外层（无 clip）：双指本就是全卡面，圆角外差几个像素无感。
            .pinchToColumns(
                enabled = editMode && onPinchColumns != null,
                currentColumns = { currentColumns.value },
                onColumns = { target, final ->
                    if (!final) view.hapticTick()
                    pinchColumnsCallback.value?.invoke(target, final)
                },
            ),
    ) {
        // 区域模糊在卡片背景之下：外层不 clip，圆角由 drawable 自己画。
        PanelBlurHost(
            enabled = blurActive,
            radiusPx = blurRadiusPx,
            cornerRadiusPx = cornerPx,
            modifier = Modifier.matchParentSize(),
            onStateChanged = { blurState.value = it },
        )
        // 卡面内容：背景/圆角裁剪放在内层。外层留着给面板缩放手柄，命中区才能伸出
        // 面板外——Modifier.clip 会连子节点的命中测试一起裁，就地偏移会被裁掉。
        val cardShape = remember(cornerPx, density) {
            containerShape(with(density) { cornerPx.toDp() })
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(cardColor.copy(alpha = cardBackgroundAlpha), cardShape)
                .clip(cardShape),
        ) {
        if (singleContent != null) {
            Box(Modifier.fillMaxSize()) {
                singleContent.invoke(metrics)
            }
        } else {
        // 多页指示点固定在顶部，高度预算 IndicatorHeight 在 metrics 里；页切换靠点/横滑。
        Column(Modifier.fillMaxSize()) {
            // 顶部预留区恒存在（高度 = IndicatorHeight），单页运行态为空白，
            // 与底部等高预留对称：进编辑态指示点出现时卡面不跳动。
            if (showIndicator) {
                GridPageIndicator(
                    slots = indicatorSlots,
                    pageCount = pageCount,
                    activePage = activePage,
                    onSelectPage = onSelectPage,
                    // 拖拽到点上时高亮目标页（包括末页的虚页点 = 新建页）。
                    pendingPage = state.dragPageDrop?.dotIndex,
                    // 拾取浮层浮出时把原指示条压淡，避免两排点抢注意力。
                    modifier = Modifier.fillMaxWidth().alpha(if (pickerVisible) 0.25f else 1f),
                )
            } else {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(IndicatorHeight),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = GridPadding)
                    // 底部与顶部预留块等高（IndicatorHeight）：进编辑态卡高不跳变，
                    // 卡面上下对称。
                    .padding(bottom = IndicatorHeight),
            ) {
                HorizontalPager(
                    state = pagerState,
                    // 单映射（单页运行态）禁手势滑动：页数仍恒为极大值以保住循环索引，
                    // 只是不让用户滑（见 GridPanelContent 的注释）。
                    // 拖拽中先锁住翻页：第二根手指滑页时拖拽态还留在旧页，松手会落在
                    // 看不见的那页。等跨页迁移做完再放开（见后续计划）。
                    userScrollEnabled = mappingPages > 1 && state.draggingId == null,
                    modifier = Modifier.fillMaxSize(),
                ) { pagerIndex ->
                    // 循环模式下 pager 索引 ≠ 真实页；滚动状态等按真实页共享，
                    // 同一页的多个循环副本共用同一个 ScrollState。
                    val pageIndex = realPage(pagerIndex)
                    val vScroll = remember(pageIndex, panelId) {
                        pageScrolls.getOrPut(pageIndex) { ScrollState(0) }
                    }
                    val edge = remember(pageIndex, panelId, bandPx, stepPx) {
                        EdgeAutoScroll(scope, state, bandPx, stepPx)
                    }
                    DisposableEffect(pageIndex) {
                        onDispose { edge.stop() }
                    }
                    val pageMetrics = metricsForPage(pageIndex)
                    LaunchedEffect(editMode, pageIndex) {
                        if (!editMode) edge.stop()
                    }
                    // baseActions 每次重组都是新实例（lambda 不稳定），不能直接当
                    // remember 的 key —— 否则等于白记。这里用 rememberUpdatedState
                    // 存最新值，actions 本体只按页建一次，闭包里读到的永远是新的。
                    val baseActionsState = rememberUpdatedState(itemActions(pageIndex))
                    val actions = remember(pageIndex) {
                        GridItemActions(
                            onTap = { item, center ->
                                baseActionsState.value.onTap(item, center)
                            },
                            onDelete = { item -> baseActionsState.value.onDelete(item) },
                            onDragStart = { item ->
                                dragPointer = null
                                edge.stop()
                                baseActionsState.value.onDragStart(item)
                            },
                            onDrag = { amount -> baseActionsState.value.onDrag(amount) },
                            onDragEnd = {
                                dragPointer = null
                                edge.stop()
                                baseActionsState.value.onDragEnd()
                            },
                            onDragPosition = { item, contentX, contentY ->
                                // contentY 已是网格内容坐标（层根 = 滚动内容），
                                // 减去已滚量才是可视区坐标。
                                val viewportY = contentY - vScroll.value
                                edge.update(vScroll, viewportY, pageIndex)
                                // 层在卡内偏移 (gridPad, IndicatorHeight)：换算到卡片坐标后
                                // 看手指是否落在顶部页指示点上。拖到点上 = 松手把项挪到那一页，
                                // 页内重排永远不会误触（热区在网格上方，和内容不重叠）。
                                val cardX = contentX + gridPadPx
                                val cardY = viewportY + indicatorPx
                                dragPointer = Offset(cardX, cardY)
                                state.onDragPageDrop(pageDropAt(cardX, cardY))
                                baseActionsState.value.onDragPosition?.invoke(item, contentX, contentY)
                            },
                            onResizeStart = { item ->
                                baseActionsState.value.onResizeStart(item)
                            },
                            onResizeDelta = { delta ->
                                baseActionsState.value.onResizeDelta(delta)
                            },
                            onResizeEnd = { baseActionsState.value.onResizeEnd() },
                            onToggleBackground = { item -> baseActionsState.value.onToggleBackground(item) },
                            onReconfigureInternal = { item -> baseActionsState.value.onReconfigureInternal(item) },
                        )
                    }
                    // pager 会同时组合相邻页：拖拽视觉/落点预览只属于正在拖的那一页，
                    // 否则翻到隔壁也能看到半透明的落位框。
                    val dragOnThisPage = state.dragPage == pageIndex
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll),
                    ) {
                        GridPanelLayer(
                            items = state.renderItems(pageIndex),
                            metrics = pageMetrics,
                            editMode = editMode,
                            draggingId = if (dragOnThisPage) state.draggingId else null,
                            hoverCell = if (dragOnThisPage) state.hoverCell else null,
                            dragVisual = if (dragOnThisPage) state.dragVisual else androidx.compose.ui.geometry.Offset.Zero,
                            glideFrom = state.glideFrom,
                            resizingId = state.resizingId,
                            resizeSpanF = state.resizeSpanF,
                            actions = actions,
                            onWidgetScroll = { dy -> vScroll.dispatchRawDelta(dy) },
                            onWidgetFling = { velocity ->
                                scope.launch {
                                    var v = velocity
                                    while (abs(v) > 60f) {
                                        val consumed = vScroll.dispatchRawDelta(v * 0.016f)
                                        if (consumed == 0f) break
                                        v *= 0.93f
                                        delay(16L)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        // 拖拽进入顶部指示条热区时：在卡片底部固定显示放大版页点（离开即隐藏）。
        if (pickerVisible) {
            PageDropPicker(
                slots = indicatorSlots,
                pageCount = pageCount,
                activePage = activePage,
                target = state.dragPageDrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
        // 双指调列数：列的实时数值浮在卡面中央，松手后按该值重排并落盘。
        if (pinchColumns != null) {
            val shape = CircleShape
            Box(
                Modifier
                    .align(Alignment.Center)
                    .zIndex(3f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.grid_panel_pinch_columns, pinchColumns),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // 编辑态加项入口：默认右下角；面板手柄占右下角时让到左下，两个抓手不叠。
        // 拖拽中隐藏：此时加项没有意义，而且它会挡住底部的页拾取浮层。
        AnimatedVisibility(
            visible = editMode && singleContent == null && state.draggingId == null,
            modifier = Modifier
                .align(
                    if (editMode && resizeCorner == ResizeCorner.BOTTOM_END) {
                        Alignment.BottomStart
                    } else {
                        Alignment.BottomEnd
                    },
                )
                .padding(16.dp),
            enter = fadeIn(tween(hunoia.luno.ui.theme.MotionCellIn)) + scaleIn(tween(hunoia.luno.ui.theme.MotionCellIn), initialScale = 0.8f),
            exit = fadeOut(tween(hunoia.luno.ui.theme.MotionCellOut)) + scaleOut(tween(hunoia.luno.ui.theme.MotionCellOut), targetScale = 0.8f),
        ) {
            PressFloatingActionButton(
                onClick = onAdd,
                // 加项是全卡唯一主操作，用 primary：内容色是固定浅灰，FAB 再用内容色
                // 会变成全卡最亮的大白钮；深色方案下 primary 亮、onPrimary 暗，黑卡上看得见。
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.grid_panel_add_item),
                )
            }
        }
        }
        }
        // 整块面板缩放手柄：编辑态显示在离屏边最远的角，视觉与项手柄一致。
        // 放外层（卡面 clip 之外）：命中盒一半伸出面板外，面板边缘也能抓到。
        // 项拖拽/项缩放进行中先收起，避免两套手柄抢同一根手指。
        if (editMode && !hiddenForDrag && state.draggingId == null &&
            (state.resizingId == null || panelResizing) &&
            onPanelResizeStart != null
        ) {
            val handleAlignment = when (resizeCorner) {
                ResizeCorner.TOP_START -> Alignment.TopStart
                ResizeCorner.TOP_END -> Alignment.TopEnd
                ResizeCorner.BOTTOM_START -> Alignment.BottomStart
                ResizeCorner.BOTTOM_END -> Alignment.BottomEnd
            }
            PanelResizeHandle(
                corner = resizeCorner,
                cornerRadiusPx = cornerPx,
                active = panelResizing,
                modifier = Modifier
                    .align(handleAlignment)
                    .zIndex(2f),
                onStart = {
                    resizeCornerOverride = resizeCorner
                    onPanelResizeStart.invoke(resizeCorner, drawX, drawY)
                },
                onDelta = { delta -> onPanelResizeDelta?.invoke(delta) },
                onEnd = {
                    onPanelResizeEnd?.invoke()
                    // 只冻结「拖动期间」的起手角；松手后重新按当前几何选角。
                    resizeCornerOverride = null
                },
            )
        }
    }
}

/** 编辑态双指调列数：只在出现第二根手指后接管，单指事件完全不消费。 */
private fun Modifier.pinchToColumns(
    enabled: Boolean,
    currentColumns: () -> Int,
    onColumns: (target: Int, final: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var firstId: PointerId = first.id
            var secondId: PointerId? = null
            var startSpan = 0f
            var baseColumns = currentColumns()
            var lastTarget = baseColumns
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (!pinching) {
                    if (pressed.size >= 2) {
                        val a = pressed[0]
                        val b = pressed[1]
                        firstId = a.id
                        secondId = b.id
                        startSpan = (a.position - b.position).getDistance()
                        baseColumns = currentColumns()
                        lastTarget = baseColumns
                        pinching = startSpan > 0f
                    } else if (pressed.isEmpty()) {
                        break
                    }
                }
                if (pinching) {
                    val a = event.changes.firstOrNull { it.id == firstId && it.pressed }
                    val b = event.changes.firstOrNull { it.id == secondId && it.pressed }
                    if (a == null || b == null) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                    val span = (a.position - b.position).getDistance()
                    if (startSpan > 0f) {
                        // 放大手势 = 格子变大 = 列数变少；缩小手势 = 列数变多。
                        val target = (baseColumns / (span / startSpan)).roundToInt()
                            .coerceIn(
                                GridPanelDefaults.MinColumnCount,
                                GridPanelDefaults.MaxColumnCount,
                            )
                        if (target != lastTarget) {
                            lastTarget = target
                            onColumns(target, false)
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
            }
            if (pinching) onColumns(lastTarget, true)
        }
    }
}

/** 整块面板的缩放手柄：按下即接管，输出相对按下点的总位移。 */
@Composable
private fun PanelResizeHandle(
    corner: ResizeCorner,
    /** 卡片实际圆角（px）：手柄双弧与边框同心。 */
    cornerRadiusPx: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onDelta: (Offset) -> Unit,
    onEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val cornerAlignment = when (corner) {
        ResizeCorner.TOP_START -> Alignment.TopStart
        ResizeCorner.TOP_END -> Alignment.TopEnd
        ResizeCorner.BOTTOM_START -> Alignment.BottomStart
        ResizeCorner.BOTTOM_END -> Alignment.BottomEnd
    }
    val cornerRadius = with(density) { cornerRadiusPx.toDp() }
    val visualSize = cornerRadius + 6.dp
    val hitSize = if (visualSize > 44.dp) visualSize + 8.dp else 44.dp
    val hitSizePx = with(density) { hitSize.toPx() }
    // 命中盒中心对准卡片角顶点：一半在卡内一半在卡外，面板边缘也能抓到。
    val hitOutX = (if (corner.isLeft) -1f else 1f) * hitSizePx / 2f
    val hitOutY = (if (corner.isTop) -1f else 1f) * hitSizePx / 2f
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // pointerInput 的 key 只有 corner，协程不会随每次重组重启；必须通过
    // rememberUpdatedState 取最新的回调，否则第二次拖动拿到首帧的 metrics，
    // 起手尺寸会回到面板初始大小（看起来像「第二次拖回原大小」）。
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDelta by rememberUpdatedState(onDelta)
    val currentOnEnd by rememberUpdatedState(onEnd)
    Box(modifier) {
        // 命中盒：外移半盒到面板外（在无 clip 的外层 Box 里才不被裁）。
        Box(
            Modifier
                .align(cornerAlignment)
                .size(hitSize)
                .offset { IntOffset(hitOutX.roundToInt(), hitOutY.roundToInt()) }
                .onGloballyPositioned { handleCoords = it }
                .pointerInput(corner) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        // 手柄会跟着卡片移动：位移必须换算到不动的根坐标，
                        // 否则每帧都把布局位移算进拖动量，卡片会来回抽动。
                        fun toRoot(position: Offset): Offset =
                            handleCoords?.localToRoot(position) ?: position
                        val start = toRoot(down.position)
                        view.hapticTick()
                        currentOnStart()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                change.consume()
                                currentOnDelta(toRoot(change.position) - start)
                            }
                        } finally {
                            currentOnEnd()
                        }
                    }
                },
        )
        // 视觉：仍贴卡片角，不随命中盒外移（视觉不变）。
        ItemResizeHandle(
            modifier = Modifier.align(cornerAlignment),
            active = active,
            corner = corner,
            label = stringResource(R.string.grid_panel_resize),
            cornerRadius = cornerRadius,
        )
    }
}

/**
 * 拖拽拾取页的浮层：放大版页点 + 目标页标记，固定在卡片底部。
 *
 * 只在手指进入顶部指示条热区时出现；固定底部而不是跟手，避免手指/手掌遮挡，
 * 也不用在卡片里算浮层位置。目标点用实心 + 外环标出，右侧直接给页码
 * （虚页点给「+」，表示会新建一页）。
 */
@Composable
private fun PageDropPicker(
    slots: List<PageDotSlot>,
    pageCount: Int,
    activePage: Int,
    target: PageDrop?,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左右各放一个同样大的隐形占位（等宽页码槽），页点始终以卡片中线为轴；
            // 槽位尺寸由占位文本决定，有/无目标、页码位数变化时浮层不会跳高跳宽。
            PickerLabelPlaceholder()
            Spacer(Modifier.width(6.dp))
            slots.forEachIndexed { slotIndex, slot ->
                if (slotIndex > 0) Spacer(Modifier.width(6.dp))
                if (slot is PageDotSlot.Ellipsis) {
                    // 与点同宽，保持整体居中与间距；画圆点而不是文字，大字体下不会被裁。
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        PageEllipsisDots(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(PageEllipsisSize),
                        )
                    }
                    return@forEachIndexed
                }
                val page = (slot as PageDotSlot.Page).index
                val isVirtual = page >= pageCount
                val selected = target?.dotIndex == page
                val active = page == activePage
                val color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                    val dotSize = if (isVirtual) 12.dp else 10.dp
                    if (isVirtual && !selected) {
                        Box(
                            Modifier
                                .size(dotSize)
                                .border(1.5.dp, color, CircleShape),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(contentAlignment = Alignment.Center) {
                // 隐形测量：保证槽位宽度/高度恒定；可见页码叠在上面。
                PickerLabelPlaceholder()
                if (target != null) {
                    Text(
                        text = if (target.isVirtual) "+" else "${target.dotIndex + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * 页码槽的隐形占位：宽度取「两位数字」的尺寸、高度取字体的行高。
 * 用真实文本测量而不是写死 dp —— 大字体缩放时槽位会一起长高，文字不会被裁，
 * 也不会因为「有/无目标」而在拖动中改变浮层高度。
 */
@Composable
private fun PickerLabelPlaceholder() {
    Text(
        text = "00",
        modifier = Modifier
            .alpha(0f)
            .clearAndSetSemantics {},
        style = MaterialTheme.typography.titleMedium,
    )
}
