package hunoia.luno.ui.home
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.onSingleClick
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.component.buttonTextCompose
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.BadgeIconSize
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
enum class GesturePanelSection {
    TouchButton,
    SubGesture,
}
@Composable
fun GesturePanel(
    gestureButtons: List<GestureButton>,
    subGestures: List<SubGesture>,
    expandedSection: GesturePanelSection?,
    onExpandedChange: (GesturePanelSection?) -> Unit,
    onGestureButtonClick: (GestureButton) -> Unit,
    onSubGestureClick: (String) -> Unit,
    onGestureCheckedChange: (GestureButton, Boolean) -> Unit,
    onSubCheckedChange: (SubGesture, Boolean) -> Unit,
    onAddGesture: () -> Unit,
    onAddSub: () -> Unit,
    onMarkColorClick: (Any) -> Unit,
    onGestureButtonRename: (GestureButton) -> Unit = {},
    onSubGestureRename: (SubGesture) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        GestureSection(
            title = stringResource(id = R.string.gesture_button),
            addContentDescription = stringResource(id = R.string.add_gesture_button),
            expanded = expandedSection == GesturePanelSection.TouchButton,
            itemCount = gestureButtons.size,
            onExpandedChange = { expanded ->
                onExpandedChange(
                    if (expanded) GesturePanelSection.TouchButton else null
                )
            },
            onAdd = onAddGesture,
        ) {
            gestureButtons.fastForEachIndexed { index, button ->
                key(button.id) {
                    val markColor = gestureButtonMarkColor(button.color)
                    LunoSwitchRow(
                        title = button.buttonTextCompose(),
                        checked = button.enabled,
                        onCheckedChange = { onGestureCheckedChange(button, it) },
                        onClick = { onGestureButtonClick(button) },
                        shape = segmentedShape(index + 1, gestureButtons.size + 1),
                        leading = {
                            Row(
                                // Row 默认 Top 对齐，颜色圆(20dp) 与铅笔(24dp) 高度不同会错位，显式居中。
                                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RenameIcon(onClick = { onGestureButtonRename(button) })
                                Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(color = markColor, shape = CircleShape)
                                        .clickable(
                                            indication = null,
                                            interactionSource = null,
                                            onClick = { onMarkColorClick(button) },
                                        ),
                                )
                            }
                        },
                    )
                }
            }
        }
        GestureSection(
            title = stringResource(id = R.string.sub_gesture),
            addContentDescription = stringResource(id = R.string.add_sub_gesture),
            expanded = expandedSection == GesturePanelSection.SubGesture,
            itemCount = subGestures.size,
            onExpandedChange = { expanded ->
                onExpandedChange(
                    if (expanded) GesturePanelSection.SubGesture else null
                )
            },
            onAdd = onAddSub,
        ) {
            subGestures.fastForEachIndexed { index, gesture ->
                key(gesture.id) {
                    LunoSwitchRow(
                        title = gesture.name,
                        checked = gesture.enabled,
                        onCheckedChange = { onSubCheckedChange(gesture, it) },
                        onClick = { onSubGestureClick(gesture.id) },
                        shape = segmentedShape(index + 1, subGestures.size + 1),
                        leading = {
                            Row(
                                // 同上：显式居中，避免颜色圆与铅笔错位。
                                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RenameIcon(onClick = { onSubGestureRename(gesture) })
                                Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(
                                            color = gestureButtonMarkColor(gesture.color),
                                            shape = CircleShape,
                                        )
                                        .clickable(
                                            indication = null,
                                            interactionSource = null,
                                            onClick = { onMarkColorClick(gesture) },
                                        ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun GestureSection(
    title: String,
    addContentDescription: String,
    expanded: Boolean,
    itemCount: Int,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val totalItems = if (expanded) itemCount + 1 else 1
    LunoGroup {
        LunoSettingsRow(
            title = title,
            onClick = { onExpandedChange(!expanded) },
            shape = segmentedShape(0, totalItems),
            leading = { SectionArrow(expanded = expanded) },
            trailing = {
                SmallIconAction(
                    imageVector = Icons.Filled.Add,
                    contentDescription = addContentDescription,
                    onClick = onAdd,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
                content()
            }
        }
    }
}

/** 折叠箭头：折叠 -90°、展开 0°（与全库折叠组的旋转区间一致）。 */
@Composable
private fun SectionArrow(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "GestureSectionArrowRotation",
    )
    Icon(
        imageVector = Icons.Filled.ArrowDropDown,
        contentDescription = null,
        modifier = Modifier
            .size(RowIconSize)
            .graphicsLayer { rotationZ = rotation },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RenameIcon(onClick: () -> Unit) {
    SmallIconAction(
        imageVector = Icons.Filled.Edit,
        contentDescription = stringResource(R.string.rename),
        onClick = onClick,
    )
}
@Composable
private fun SmallIconAction(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    // 视觉与触控都保持 24dp：本行的铅笔与颜色圆间距仅 8dp，两个 48dp 目标无法共存
    // （要么撑大行宽把图标挤出对齐轴，要么命中区互相争抢）。
    Box(
        modifier = Modifier
            .size(RowIconSize)
            .onSingleClick { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(BadgeIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}