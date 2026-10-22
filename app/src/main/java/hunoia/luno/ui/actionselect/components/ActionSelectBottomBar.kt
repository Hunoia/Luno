package hunoia.luno.ui.actionselect

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import dev.chrisbanes.haze.HazeState
import hunoia.luno.R
import hunoia.luno.ui.animation.DampedDragAnimation
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.BottomBarIconSize
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.appGlassOutline
import hunoia.luno.ui.theme.appHazeEffect
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 底栏玻璃底透明度：与主页 FloatingBottomBar 同一套，自身不再盖底色（双层会发白）。 */
private const val BottomBarHazeAlpha = 0.65f

/** 底栏一颗类型 chip：图标 + 无障碍文案，key 为 null 表示「全部」。 */
internal data class ActionTypeChip(
    val key: String?,
    val label: String,
    val icon: ImageVector,
)

/**
 * 动作选择页底栏：类型 chip 常驻一行，搜索与 chip **同一行**展开（占满 chip 区域，不另起一行）。
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

    // 展开动画铺好布局再要焦点：组合期直接 requestFocus 节点还没 attach，会落空。
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // 键盘与导航栏取并集：IME 可见时底部 inset 已含导航栏，相加会多顶一截。
    val bottomInsets = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(bottomInsets)
            .padding(start = PageGutter, end = PageGutter, bottom = BottomBarPadding)
            .onSizeChanged { onHeightChange(it.height) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = TileInnerPadding * 2.5f,
                        color = Color.Black,
                        alpha = if (isSystemInDarkTheme()) 0.2f else 0.1f,
                    ),
                )
                // clip 在 haze 之外：方形糊输出裁成胶囊（只裁绘制，不影响采样）。
                .clip(CircleShape)
                // 底色/透明度对齐主页底栏；用 surfaceContainerHighest 而不是 surfaceContainer，
                // 否则叠在列表上过淡，看起来像 chip 自己浮着、没有胶囊容器。
                .appHazeEffect(
                    hazeState,
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = BottomBarHazeAlpha),
                )
                .appGlassOutline(CircleShape)
                .padding(TileInnerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomBarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // chip 区与搜索框同一行互换：不把底栏撑高，列表留白也就不会跳。
                AnimatedContent(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    targetState = searchExpanded,
                    transitionSpec = {
                        val enter = fadeIn(tween(160)) + slideInHorizontally(tween(160)) { full ->
                            if (targetState) full / 8 else -full / 8
                        }
                        val exit = fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { full ->
                            if (targetState) -full / 8 else full / 8
                        }
                        enter togetherWith exit
                    },
                    label = "actionSearchMode",
                ) { searching ->
                    if (searching) {
                        CapsuleSearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            placeholder = stringResource(R.string.search_hint_all),
                            focusRequester = focusRequester,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TypeChipStrip(
                            modifier = Modifier.fillMaxSize(),
                            typeChips = typeChips,
                            selectedType = selectedType,
                            onSelectedTypeChange = onSelectedTypeChange,
                        )
                    }
                }
                IconButton(
                    onClick = { if (searchExpanded) closeSearch() else searchExpanded = true },
                ) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = stringResource(
                            if (searchExpanded) R.string.search_collapse_cd else R.string.search_hint_all
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

/**
 * 类型 chip 条：等分整条可用宽度（不横向滚动），选中 pill 与主页底栏同一套交互：
 * 点击切换、水平拖动时 pill 跟手（带橡皮筋与速度倾斜），松手吸附到最近一颗。
 */
@Composable
private fun TypeChipStrip(
    typeChips: List<ActionTypeChip>,
    selectedType: String?,
    onSelectedTypeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (typeChips.isEmpty()) return
    val count = typeChips.size
    val selectedIndex = typeChips.indexOfFirst { it.key == selectedType }.coerceAtLeast(0)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    var currentIndex by remember(count) { mutableIntStateOf(selectedIndex) }
    val onSelectedUpdated = rememberUpdatedState(onSelectedTypeChange)
    val selectedIndexUpdated = rememberUpdatedState(selectedIndex)

    BoxWithConstraints(modifier = modifier) {
        val slotWidth = maxWidth / count
        val slotWidthPx = with(density) { slotWidth.toPx() }
        val stripWidthPx = slotWidthPx * count
        val rubberBandPx = with(density) { TileInnerPadding.toPx() }

        // 拖到首尾之外时整条 chip 区被带着走，松手弹回。
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(rubberBandPx, stripWidthPx) {
            derivedStateOf {
                if (stripWidthPx == 0f) {
                    0f
                } else {
                    val fraction = (offsetAnimation.value / stripWidthPx).fastCoerceIn(-1f, 1f)
                    rubberBandPx * (if (fraction < 0f) -1f else 1f) * EaseOut.transform(abs(fraction))
                }
            }
        }

        fun indexAt(positionX: Float): Int {
            if (slotWidthPx == 0f) return currentIndex
            val logicalX = if (isLtr) positionX else stripWidthPx - positionX
            return (logicalX / slotWidthPx).toInt().fastCoerceIn(0, count - 1)
        }

        val dampedDragAnimation = remember(animationScope, count, density, isLtr, stripWidthPx) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.2f,
                canDrag = { position -> position.x in 0f..stripWidthPx },
                onDragStarted = { position ->
                    updateValue(indexAt(position.x).toFloat())
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    if (currentIndex != targetIndex) {
                        currentIndex = targetIndex
                        onSelectedUpdated.value(typeChips[targetIndex].key)
                    }
                    updateValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDragCancelled = {
                    updateValue(currentIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    if (slotWidthPx > 0f && dragAmount.x != 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / slotWidthPx * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, (count - 1).toFloat()),
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            )
        }

        // 外部选中变化（无障碍点击 / 状态恢复）同步 pill。
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { selectedIndexUpdated.value }.collectLatest { index ->
                if (currentIndex != index) {
                    currentIndex = index
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = panelOffset }
                .then(dampedDragAnimation.modifier),
        ) {
            // 滑动指示 pill：跟手位移 + 按压放大 + 速度倾斜，参数照抄主页 FloatingBottomBar。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(slotWidth)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * slotWidthPx
                        translationX = if (isLtr) progressOffset else -progressOffset
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = (dampedDragAnimation.velocity / 10f).fastCoerceIn(-0.2f, 0.2f)
                        scaleX /= 1f - velocity * 0.75f
                        scaleY *= 1f - velocity * 0.25f
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            )
            Row(modifier = Modifier.fillMaxSize()) {
                typeChips.forEachIndexed { index, chip ->
                    TypeChip(
                        chip = chip,
                        isSelected = index == currentIndex,
                        onClick = { onSelectedTypeChange(chip.key) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * 单颗类型 chip：只有图标，选中态由底下的滑动 pill 表达。
 * 不画 M3 FilterChip 的独立容器 —— 那样会变成「玻璃上贴了一排 chip」。
 * 点击/拖动由父级的 DampedDragAnimation 统一接管（与主页 tab 相同），这里只保留无障碍点击。
 */
@Composable
private fun TypeChip(
    chip: ActionTypeChip,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "actionTypeChipFg",
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                role = Role.Tab
                contentDescription = chip.label
                this.onClick(label = chip.label) {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = chip.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(BottomBarIconSize),
        )
    }
}


/**
 * 胶囊内联搜索框：不用 M3 OutlinedTextField —— 它自带 56dp 最小高度与上下内边距，
 * 在 fontScale 1.4 的设备上会被胶囊的 56dp 内高裁掉文字底部。这里用 BasicTextField
 * 自己控制排版，行高随字体自然居中，任何 fontScale 都不会被裁。
 */
@Composable
private fun CapsuleSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.search_clear_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
