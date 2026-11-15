package hunoia.luno.ui.home
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.onSingleClick
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.SubGesture
import hunoia.luno.gesture.settings.global.globalZoneLabel
import hunoia.luno.ui.component.buttonTextCompose
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.component.row.groupItemSpacing
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.BadgeIconSize
import hunoia.luno.ui.theme.toMarkColor
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
enum class GesturePanelSection {
    TouchButton,
    SubGesture,
    GlobalZone,
}

@Composable
fun GesturePanel(
    gestureButtons: List<GestureButton>,
    subGestures: List<SubGesture>,
    globalZones: List<GlobalGestureZone> = emptyList(),
    expandedSection: GesturePanelSection?,
    onExpandedChange: (GesturePanelSection?) -> Unit,
    onGestureButtonClick: (GestureButton) -> Unit,
    onSubGestureClick: (String) -> Unit,
    onGlobalZoneClick: (String) -> Unit = {},
    onGestureCheckedChange: (GestureButton, Boolean) -> Unit,
    onSubCheckedChange: (SubGesture, Boolean) -> Unit,
    onGlobalZoneCheckedChange: (GlobalGestureZone, Boolean) -> Unit = { _, _ -> },
    onGlobalZoneRename: (GlobalGestureZone) -> Unit = {},
    onAddGesture: () -> Unit,
    onAddSub: () -> Unit,
    onAddGlobalZone: () -> Unit = {},
    onMarkColorClick: (Any) -> Unit,
    onGestureButtonRename: (GestureButton) -> Unit = {},
    onSubGestureRename: (SubGesture) -> Unit = {},
) {
    // 三个分区在同一张组卡里连续编号。分区头与展开行都按「组内第几项」取形状，
    // 大卡分割线风格才能在每个分区头 / 行上方画线；不能每个分区各自从 0 重排。
    // 展开态同一时间只有一个分区（expandedSection 单值），行数只算展开的那个。
    val expandedRowCount = when (expandedSection) {
        GesturePanelSection.TouchButton -> gestureButtons.size
        GesturePanelSection.SubGesture -> subGestures.size
        GesturePanelSection.GlobalZone -> globalZones.size
        null -> 0
    }
    val groupItemCount = 3 + expandedRowCount
    val touchHeaderIndex = 0
    val subHeaderIndex = touchHeaderIndex + 1 +
        if (expandedSection == GesturePanelSection.TouchButton) gestureButtons.size else 0
    val zoneHeaderIndex = subHeaderIndex + 1 +
        if (expandedSection == GesturePanelSection.SubGesture) subGestures.size else 0

    LunoGroup(title = stringResource(R.string.home_section_gesture)) {
        GestureSection(
            title = stringResource(id = hunoia.luno.core.data.R.string.gesture_button),
            addContentDescription = stringResource(id = R.string.add_gesture_button),
            expanded = expandedSection == GesturePanelSection.TouchButton,
            index = touchHeaderIndex,
            count = groupItemCount,
            onExpandedChange = { expanded ->
                onExpandedChange(if (expanded) GesturePanelSection.TouchButton else null)
            },
            onAdd = onAddGesture,
        ) { rowStart ->
            gestureButtons.fastForEachIndexed { index, button ->
                key(button.id) {
                    val markColor = button.colorSpec.toMarkColor()
                    LunoSwitchRow(
                        title = button.buttonTextCompose(),
                        checked = button.enabled,
                        onCheckedChange = { onGestureCheckedChange(button, it) },
                        onClick = { onGestureButtonClick(button) },
                        shape = segmentedShape(rowStart + index, groupItemCount),
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
                                        .pressClickable(onClick = { onMarkColorClick(button) }),
                                )
                            }
                        },
                    )
                }
            }
        }
        GestureSection(
            title = stringResource(id = hunoia.luno.gesture.R.string.sub_gesture),
            addContentDescription = stringResource(id = R.string.add_sub_gesture),
            expanded = expandedSection == GesturePanelSection.SubGesture,
            index = subHeaderIndex,
            count = groupItemCount,
            onExpandedChange = { expanded ->
                onExpandedChange(if (expanded) GesturePanelSection.SubGesture else null)
            },
            onAdd = onAddSub,
        ) { rowStart ->
            subGestures.fastForEachIndexed { index, gesture ->
                key(gesture.id) {
                    LunoSwitchRow(
                        title = gesture.name,
                        checked = gesture.enabled,
                        onCheckedChange = { onSubCheckedChange(gesture, it) },
                        onClick = { onSubGestureClick(gesture.id) },
                        shape = segmentedShape(rowStart + index, groupItemCount),
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
                                            color = gesture.colorSpec.toMarkColor(),
                                            shape = CircleShape,
                                        )
                                        .pressClickable(onClick = { onMarkColorClick(gesture) }),
                                )
                            }
                        },
                    )
                }
            }
        }
        GestureSection(
            title = stringResource(id = hunoia.luno.gesture.R.string.gesture_global_zone),
            addContentDescription = stringResource(id = hunoia.luno.gesture.R.string.gesture_global_zone_add),
            expanded = expandedSection == GesturePanelSection.GlobalZone,
            index = zoneHeaderIndex,
            count = groupItemCount,
            onExpandedChange = { expanded ->
                onExpandedChange(if (expanded) GesturePanelSection.GlobalZone else null)
            },
            onAdd = onAddGlobalZone,
        ) { rowStart ->
            globalZones.fastForEachIndexed { index, zone ->
                key(zone.id) {
                    LunoSwitchRow(
                        title = globalZoneLabel(zone, index),
                        checked = zone.enabled,
                        onCheckedChange = { onGlobalZoneCheckedChange(zone, it) },
                        onClick = { onGlobalZoneClick(zone.id) },
                        shape = segmentedShape(rowStart + index, groupItemCount),
                        leading = {
                            RenameIcon(onClick = { onGlobalZoneRename(zone) })
                        },
                    )
                }
            }
        }
    }
}

/**
 * 一个可折叠分区：组头 + 展开后的行。
 *
 * [index] / [count] 是**整组**里的位置（不是本分区内），展开行的起始位置是
 * `index + 1`。这样组内分割线与首尾圆角跨分区连续。
 */
@Composable
private fun GestureSection(
    title: String,
    addContentDescription: String,
    expanded: Boolean,
    index: Int,
    count: Int,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    content: @Composable (rowStart: Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(groupItemSpacing()),
    ) {
        LunoSettingsRow(
            title = title,
            onClick = { onExpandedChange(!expanded) },
            shape = segmentedShape(index, count),
            leading = { SectionArrow(expanded = expanded) },
            trailing = {
                SmallIconAction(
                    imageVector = Icons.Filled.Add,
                    contentDescription = addContentDescription,
                    onClick = onAdd,
                    // 分组头右侧空间充足：放大命中区（原 24dp 太小）与图标，去掉点击高亮
                    touchSize = 40.dp,
                    iconSize = 20.dp,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(groupItemSpacing()),
            ) {
                content(index + 1)
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
    touchSize: Dp = RowIconSize,
    iconSize: Dp = BadgeIconSize,
) {
    // 铅笔与颜色圆间距仅 8dp，两个 48dp 目标无法共存：行内图标保持 24dp，
    // 分组头右侧的添加按钮由调用方传更大的 touchSize。
    // 统一关 ripple：行本身已经 indication = null，图标再亮一下会显得脏。
    Box(
        modifier = Modifier
            .size(touchSize)
            .onSingleClick(enableRipple = false) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            // 命中区放大后图标仍要与行尾箭头对齐：把多出来的边距补到右侧
            modifier = Modifier
                .size(iconSize)
                .offset(x = (touchSize - RowIconSize) / 2),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}