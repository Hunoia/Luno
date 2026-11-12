package hunoia.luno.runtime.gesturepanel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterExitState.Visible
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.aaron.compose.ktx.clipToBackground
import com.aaron.compose.ktx.toPx
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.ui.component.panel.panelAppear
import hunoia.luno.ui.component.panel.panelFadeIn
import hunoia.luno.ui.component.panel.panelFadeOut
import hunoia.luno.ui.theme.PanelAppearSlide
import hunoia.luno.core.domain.launchMiniWindow
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.model.ActionPanelStyle
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.ui.theme.MiniWindowDefaultHeight
import hunoia.luno.ui.theme.MiniWindowWidth
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MotionSelection
import hunoia.luno.ui.theme.panelSpec
import hunoia.luno.ui.theme.tween
import kotlin.math.hypot
import kotlinx.coroutines.flow.filter
import hunoia.luno.gesture.vibrateForActionPanel
import hunoia.luno.ui.theme.toMarkColor
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.component.actionText
import hunoia.luno.action.model.Action



@Composable
fun ActionPanelMenu(
    actionPanelStyle: ActionPanelStyle,
    actionPanelState: ActionPanelState,
    modifier: Modifier = Modifier,
    gestureSettings: GestureSettings? = null
) {
    // 面板家族 spec：外层显隐与内部项/尺寸动画共用同一时长（下次打开生效）。
    val panelMotion = panelSpec()
    AnimatedVisibility(
        modifier = modifier,
        visible = actionPanelState.visible,
        // alpha 只收首尾段（与网格 / 快捷启动同一份曲线）；几何由下面的 panelAppear 负责。
        enter = panelFadeIn(panelMotion),
        exit = panelFadeOut(panelMotion),
    ) {
        // 进度直接读 AnimatedVisibility 自己的 transition，几何与 alpha 同一段时长。
        val appear = transition.animateFloat(transitionSpec = { panelMotion.tween() }) { from ->
            if (from == EnterExitState.Visible) 1f else 0f
        }
        var parentSize by remember { mutableStateOf(Size.Zero) }
        val resolvedStyle = actionPanelState.actionPanelStyle ?: actionPanelStyle
        val itemSizePx = (resolvedStyle as? ArcStyle)?.itemSize?.toFloat() ?: 48.dp.toPx()
        val density = LocalDensity.current
        // 与 ArcActionPanel 同一份边距：背景光晕、扇区与文字胶囊都用它定位。
        val insets = remember((resolvedStyle as? ArcStyle)?.placement, density) {
            (resolvedStyle as? ArcStyle)?.panelInsets(density.density) ?: ActionPanelInsets()
        }
        val appearSlidePx = with(density) { PanelAppearSlide.toPx() }
        // 缩放 / 位移锚点 = 面板几何里的触发点（已按边距夹进可点区），与光晕、扇区同一份原点。
        val appearOriginPoint = { size: Size ->
            if (size.width <= 0f || size.height <= 0f || !actionPanelState.origin.isSpecified) {
                Offset(size.width / 2f, size.height / 2f)
            } else {
                actionPanelOrigin(size, actionPanelState.origin, itemSizePx, insets)
            }
        }
        val actionLibrarySettings by hunoia.luno.domain.action.ActionLibrary.settings
            .collectAsStateWithLifecycle(initialValue = ActionLibrarySettings())
        LaunchedEffect(parentSize, itemSizePx) {
            actionPanelState.setLayoutInfo(parentSize, itemSizePx)
        }
        Box(
            modifier = Modifier
                .onGloballyPositioned { parentSize = it.size.toSize() }
                // 与网格 / 快捷启动同一份面板几何：绕触发点从 PanelAppearScale 展开 + 向触发点
                // 位移 + alpha 尾段（§5.1）。环形项自己也从触发点展开，两者的终点一致，
                // 起点都在指尖，不会变成两倍行程。
                .panelAppear(
                    progress = { appear.value },
                    originForSize = { size ->
                        if (size.width <= 0f || size.height <= 0f) {
                            TransformOrigin.Center
                        } else {
                            val o = appearOriginPoint(size)
                            TransformOrigin(o.x / size.width, o.y / size.height)
                        }
                    },
                    slideForSize = { size ->
                        if (size.width <= 0f || size.height <= 0f) {
                            Offset.Zero
                        } else {
                            val o = appearOriginPoint(size)
                            val dx = o.x - size.width / 2f
                            val dy = o.y - size.height / 2f
                            val len = hypot(dx, dy).coerceAtLeast(1f)
                            Offset(dx / len, dy / len) * appearSlidePx
                        }
                    },
                    fade = false,
                ),
        ) {
            ActionPanelBackdrop(
                modifier = Modifier.matchParentSize(),
                actionPanelState = actionPanelState,
                parentSize = parentSize,
                itemSizePx = itemSizePx,
                accentColor = actionPanelAccentColor(actionPanelState.buttonColor),
                insets = insets,
            )

            val selectedAction = actionPanelState.selectedAction
            val selectedLabel = actionText(selectedAction, emptyIfNone = false, actionLibraryEntries = actionLibrarySettings.entries)
            val animationSpec = panelMotion.tween<Float>()
            val enter = fadeIn(animationSpec) + scaleIn(animationSpec, 0.9f)
            val exit = fadeOut(animationSpec) + scaleOut(animationSpec, 0.9f)


            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.Center)
                    .displayCutoutPadding()
                    .padding(12.dp),
                visible = selectedAction.typeId == hunoia.luno.core.domain.AppLaunchAction.TYPE_ID,
                enter = enter,
                exit = ExitTransition.None
            ) {
                    BoxWithConstraints {
                    Box(
                        modifier = Modifier
                            .let { thisModifier ->
                                val miniWindow = selectedAction.launchMiniWindow()
                                val boxMaxWidth = maxWidth
                                val boxMaxHeight = maxHeight
                                val spec = tween<Dp>(
                                    durationMillis = panelMotion.durationMillis,
                                    easing = panelMotion.easing,
                                )
                                val width by animateDpAsState(
                                    targetValue = if (miniWindow) MiniWindowWidth else boxMaxWidth,
                                    animationSpec = spec
                                )
                                val height by animateDpAsState(
                                    targetValue = if (miniWindow) MiniWindowDefaultHeight else boxMaxHeight,
                                    animationSpec = spec
                                )
                                thisModifier.size(width = width, height = height)
                            }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = LargeShape
                            )
                    )
                }
            }

            when (val style = resolvedStyle) {
                is ArcStyle -> {
                    ArcActionPanel(
                        modifier = Modifier.fillMaxSize(),
                        actionPanelStyle = style,
                        actionPanelState = actionPanelState,
                        gestureSettings = gestureSettings
                    )
                }
            }

            // 不用 `visible` 做门控：退场期间环与光晕还在，标签也得跟着退场一起淡，
            // 否则一关就“唯标签先消失”。真正结束由 release() 清 origin / actions。
            val canShowPill = selectedLabel.isNotEmpty() &&
                    actionPanelState.selectedIndex >= 0 &&
                    actionPanelState.origin.isSpecified &&
                    !parentSize.isEmpty()
            if (canShowPill) {
                val origin = actionPanelOrigin(parentSize, actionPanelState.origin, itemSizePx, insets)
                val sector = actionPanelSector(parentSize, origin, actionPanelState.centerDegrees, itemSizePx)
                val actionBounds = actionPanelContentBounds(
                    parentSize = parentSize,
                    origin = origin,
                    sector = sector,
                    actionCount = actionPanelState.actions.size,
                    itemSizePx = itemSizePx,
                    spreadSpacing = resolvedStyle.spreadSpacing,
                    itemGapPx = resolvedStyle.itemGapPx.toFloat(),
                    maxAngleDegrees = resolvedStyle.maxAngleDegrees,
                    insets = insets,
                )
                val pillPosition = actionPanelPillPosition(parentSize, actionBounds, itemSizePx, insets)
                val accentColor = actionPanelAccentColor(actionPanelState.buttonColor)
                SelectedActionPill(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .displayCutoutPadding()
                        .graphicsLayer {
                            translationY = pillPosition.y
                        },
                    action = selectedAction,
                    label = selectedLabel,
                    triggerType = actionPanelState.triggerType,
                    accentColor = accentColor,
                )
            }
        }
    }
}







@Composable
internal fun AnimatedVisibilityScope.ActionPanelSelectableItem(
    actionPanelState: ActionPanelState,
    index: Int,
    action: StoredAction,
    targetAnimOffset: Offset,
    panelOrigin: Offset,
    itemSizePx: Float,
    gestureSettings: GestureSettings?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val transition = transition
    val selected = actionPanelState.isSelected(action)
    val hasSelection = actionPanelState.selectedAction.typeId != Action.NONE_TYPE_ID
    val accentColor = actionPanelAccentColor(actionPanelState.buttonColor)
    val itemAlpha by animateFloatAsState(if (!hasSelection || selected) 1f else 0.72f, tween(MotionSelection), label = "actionAlpha")
    LaunchedEffect(transition, actionPanelState, index, action, panelOrigin, targetAnimOffset) {
        snapshotFlow { actionPanelState.finger }
            .filter { it.isSpecified && !transition.isRunning && transition.currentState == Visible }
            .collect { finger ->
                if (actionPanelHitContains(finger, panelOrigin, targetAnimOffset, itemSizePx)) {
                    if (!actionPanelState.isSelected(action)) {
                        actionPanelState.select(index, action)
                        gestureSettings?.let { vibrateForActionPanel(it) }
                    }
                } else if (actionPanelState.isSelected(action)) {
                    actionPanelState.select(index, StoredAction.of(Action.NONE_TYPE_ID))
                }
            }
    }
    val applicationIcon = isApplicationIconAction(action)
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = targetAnimOffset.x
                translationY = targetAnimOffset.y
                alpha = itemAlpha
            }
            .clipToBackground(
                color = if (applicationIcon) Color.Transparent else actionPanelItemColor(accentColor),
                shape = shape,
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) accentColor.copy(alpha = 0.88f) else Color.Transparent,
                shape = shape,
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun ActionPanelIcon(
    action: StoredAction,
    iconSize: Dp,
    bitmapIconSize: Dp = iconSize,
    scale: Float = 1f,
) {
    val actionIcon = actionIcon(action = action)
    val iconModifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    when (actionIcon) {
        is ImageVector -> Image(
            modifier = iconModifier.size(iconSize),
            imageVector = actionIcon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
        )
        is TextIcon -> TextIconGlyph(
            text = actionIcon.text,
            size = iconSize,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = iconModifier,
        )
        else -> AsyncImage(
            modifier = iconModifier
                .size(bitmapIconSize),
            model = actionIcon,
            contentDescription = null,
            imageLoader = LocalContext.current.imageLoader,
        )
    }
}

internal fun isApplicationIconAction(action: StoredAction): Boolean =
    action.typeId == hunoia.luno.core.domain.AppLaunchAction.TYPE_ID ||
        action.typeId == "app.launchShortcut" ||
        action.typeId == "internal.previousApp"

@Composable
internal fun actionPanelItemColor(accentColor: Color): Color = accentColor

@Composable
internal fun actionPanelAccentColor(buttonColor: ColorSpec): Color = buttonColor.toMarkColor()
