package hunoia.luno.picker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import dev.chrisbanes.haze.HazeState
import hunoia.luno.picker.R
import hunoia.luno.ui.component.FloatingBarLayout
import hunoia.luno.ui.component.FloatingBottomBarDefaults
import hunoia.luno.ui.component.FloatingTabRow
import hunoia.luno.ui.component.LunoSearchField
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.BottomBarIconSize
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.BottomBarTabWidth
import hunoia.luno.ui.theme.DockGutter
import hunoia.luno.ui.theme.DockTabHeight
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.MotionSwapIn
import hunoia.luno.ui.theme.MotionSwapOut
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.glassCapsule

/** 底栏一颗类型 chip：图标 + 无障碍文案，key 为 null 表示「全部」。 */
internal data class ActionTypeChip(
    val key: String?,
    val label: String,
    val icon: ImageVector,
)

/**
 * 动作选择页底栏：类型 chip 常驻一行，搜索与 chip **同一行**展开（占满 chip 区域，不另起一行）。
 *
 * 几何与主页三球停靠栏同一套（ui-guidelines §4.2）：胶囊外高 `DockCapsuleHeight`、
 * 内容高 [DockTabHeight]、左右 [DockGutter]、宽屏限宽 600dp、
 * chip 宽 48..[BottomBarTabWidth]；右端搜索槽占 `DockTabHeight` 见方，从 chip 可用宽里先扣掉，
 * 胶囊按内容宽居中 —— 不再通栏铺满。
 *
 * 悬浮在列表之上（`hazeState` 采列表内容做模糊），高度通过 [onHeightChange] 回传给
 * 列表做 contentPadding —— 列表尾行才能滚到这块玻璃上方，键盘弹起时也一样。
 * 注意本组件必须画在 hazeSource **之外**（与列表是兄弟节点），否则会采到自己。
 */
@Composable
internal fun ActionSelectBottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    typeChips: List<ActionTypeChip>,
    selectedType: String?,
    onSelectedTypeChange: (String?) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onHeightChange: (Int) -> Unit = {},
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    // 派生状态：键盘升降过程中 IME inset 每帧都在变，只在「可见/不可见」翻转时触发组合，
    // 不跟着键盘动画逐帧重组整条底栏。
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    // 点 × 后：先收键盘，等 IME 真正收起再换内容 + 清 query。
    // 三件事挤在同一段动画里会互相踩：键盘下滑（逐帧改 inset 与列表底部留白）、
    // AnimatedContent 换行、清空 query 后列表恢复全量内容，表现为明显卡一下。
    var collapsePending by remember { mutableStateOf(false) }

    // 收起 = 连查询一起清掉：搜索框藏起来后还留着筛选条件，用户看不见也清不掉。
    fun closeSearch() {
        if (imeVisible) {
            collapsePending = true
            keyboard?.hide()
        } else {
            collapsePending = false
            onQueryChange("")
            searchExpanded = false
        }
    }

    LaunchedEffect(collapsePending, imeVisible) {
        if (collapsePending && !imeVisible) {
            collapsePending = false
            onQueryChange("")
            searchExpanded = false
        }
    }

    // 系统返回先收搜索框，否则输入到一半会直接退出选择页。
    BackHandler(enabled = searchExpanded) { closeSearch() }

    // 键盘与导航栏取并集：IME 可见时底部 inset 已含导航栏，相加会多顶一截。
    val bottomInsets = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 悬浮层必须自己吃触摸：gutter、底部留白、inset 这些「看着是底栏、其实没有
            // pointerInput」的区域原先会穿透到下面的列表行，表现为点搜索框边缘/图标旁边
            // 偶尔直接选中了后面那一项。消费放在最外层，覆盖整块矩形。
            .swallowTouches()
            .windowInsetsPadding(bottomInsets)
            // 横屏刘海 / 挖孔：横向安全区也要让开（与主页停靠栏同一契约）。
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            )
            .padding(start = DockGutter, end = DockGutter, bottom = BottomBarPadding)
            .onSizeChanged { onHeightChange(it.height) },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // 左右 gutter 已在 Column 的 padding 里让出，这里只做宽屏限宽。
            val dockWidth = FloatingBarLayout.barWidth(maxWidth)
            val chipCount = typeChips.size.coerceAtLeast(1)
            // 搜索槽固定为内容高见方，先从 chip 可用宽里扣掉；胶囊宽按 chip 数算，
            // 搜索展开 / 收起都沿用同一个宽度，避免玻璃宽度跳一帧。
            val searchSlot = DockTabHeight
            val rawChipWidth = (dockWidth - searchSlot - TileInnerPadding * 2) / chipCount
            // 极窄屏连 48dp 下限都放不下时按可用宽压缩，宁可窄一点也不让胶囊溢出。
            val chipWidth = min(
                FloatingBarLayout.tabWidth(
                    barWidth = dockWidth,
                    tabCount = chipCount,
                    maxTabWidth = BottomBarTabWidth,
                    innerPadding = TileInnerPadding,
                    reservedWidth = searchSlot,
                ),
                rawChipWidth,
            )
            val capsuleWidth = FloatingBarLayout.capsuleWidth(
                tabWidth = chipWidth,
                tabCount = typeChips.size,
                innerPadding = TileInnerPadding,
                trailingSlot = searchSlot,
            )

            // 玻璃胶囊容器（阴影/裁形/毛玻璃/描边/内边距）与页面级悬浮搜索胶囊共用同一份样式。
            Column(
                modifier = Modifier
                    .width(capsuleWidth)
                    .glassCapsule(hazeState),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DockTabHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // chip 区与搜索框同一行互换：不把底栏撑高，列表留白也就不会跳。
                    AnimatedContent(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        targetState = searchExpanded,
                        transitionSpec = {
                            val enter = fadeIn(tween(MotionSwapIn)) + slideInHorizontally(tween(MotionSwapIn)) { full ->
                                if (targetState) full / 8 else -full / 8
                            }
                            val exit = fadeOut(tween(MotionSwapOut)) + slideOutHorizontally(tween(MotionSwapOut)) { full ->
                                if (targetState) -full / 8 else full / 8
                            }
                            enter togetherWith exit
                        },
                        label = "actionSearchMode",
                    ) { searching ->
                        if (searching) {
                            // 展开动画铺好布局再要焦点：组合期直接 requestFocus 节点还没 attach，
                            // 会落空（旧实现用固定 delay(100) 盲等，慢设备等不够就静默失败、键盘
                            // 不弹，快设备又白等）。改用 onGloballyPositioned 等搜索框完成首帧布局，
                            // 回调触发即节点已挂载，requestFocus 必不落空；标志位与搜索框同组合作用域，
                            // 收起自动销毁、下次展开从 false 重新触发，反复展开/收起都正确。
                            var placed by remember { mutableStateOf(false) }
                            LaunchedEffect(placed) {
                                if (placed) runCatching { focusRequester.requestFocus() }
                            }
                            LunoSearchField(
                                query = query,
                                onQueryChange = onQueryChange,
                                placeholder = stringResource(hunoia.luno.design.R.string.search_hint_all),
                                focusRequester = focusRequester,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { placed = true },
                            )
                        } else {
                            TypeChipStrip(
                                modifier = Modifier.fillMaxSize(),
                                typeChips = typeChips,
                                chipWidth = chipWidth,
                                selectedType = selectedType,
                                onSelectedTypeChange = onSelectedTypeChange,
                            )
                        }
                    }
                    // 自己画点击区而不是用 IconButton：IconButton 固定 48dp，搜索槽要与内容高
                    // 正方形对齐，点在死区上什么都不会发生（以前还会穿透到列表）。
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(searchSlot)
                            .clip(CircleShape)
                            .pressClickable(
                                role = Role.Button,
                                onClick = { if (searchExpanded) closeSearch() else searchExpanded = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (searchExpanded) R.string.search_collapse_cd else hunoia.luno.design.R.string.search_hint_all
                            ),
                            tint = if (searchExpanded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 只吃事件、不做任何事：把落在底栏矩形范围内的触摸从下层兄弟节点手里拿回来。
 *
 * 底栏与列表是兄弟节点且悬浮其上，没挂 pointerInput 的空白区域不参与命中测试，
 * 触摸会漏到下面的列表行（点搜索框边缘偶尔选中后一项）。放在 Main pass 消费：
 * 子树里的 chip 拖动 / 搜索框 / 图标按钮先处理完，再轮到这里兜底。
 */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
        }
    }
}

/**
 * 类型 chip 条：等宽 chip（宽由几何算出），选中 pill 与主页底栏同一套交互：
 * 点击切换、水平拖动时 pill 跟手（带橡皮筋与速度倾斜），松手吸附到最近一颗。
 *
 * 直接复用 [FloatingTabRow]：手势宽度实时读取（`rememberUpdatedState`），不再把宽度
 * 放进手势节点的 remember key —— 转屏 / 分屏后 pill 仍跟手、落点按新宽度换算。
 */
@Composable
private fun TypeChipStrip(
    typeChips: List<ActionTypeChip>,
    chipWidth: Dp,
    selectedType: String?,
    onSelectedTypeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (typeChips.isEmpty()) return
    val selectedIndex = typeChips.indexOfFirst { it.key == selectedType }.coerceAtLeast(0)
    FloatingTabRow(
        modifier = modifier,
        items = typeChips,
        selectedIndex = { selectedIndex },
        onSelected = { index -> onSelectedTypeChange(typeChips[index].key) },
        tabWidth = chipWidth,
        tabHeight = DockTabHeight,
        colors = FloatingBottomBarDefaults.colors(),
        rubberBand = true,
    ) { chip, _ ->
        // 选中态由底下滑动的 pill 表达；颜色过渡与底栏 pill 同拍（180ms）。
        val tint by animateColorAsState(
            targetValue = LocalContentColor.current,
            animationSpec = tween(MotionSubtle),
            label = "actionTypeChipFg",
        )
        Icon(
            imageVector = chip.icon,
            contentDescription = chip.label,
            tint = tint,
            modifier = Modifier.size(BottomBarIconSize),
        )
    }
}
