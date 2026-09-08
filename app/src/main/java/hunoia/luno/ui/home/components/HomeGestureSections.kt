package hunoia.luno.ui.home

import hunoia.luno.ui.theme.*
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import hunoia.luno.ui.theme.Spacing8

@Composable
fun HomeGestureSections(
    uiState: UiState,
    onGestureHeaderClick: () -> Unit,
    onSubHeaderClick: () -> Unit,
    onGestureButtonClick: (GestureButton) -> Unit,
    onSubGestureClick: (String) -> Unit,
    onGestureCheckedChange: (GestureButton, Boolean) -> Unit,
    onSubCheckedChange: (SubGesture, Boolean) -> Unit,
    onAddGesture: () -> Unit,
    onAddSub: () -> Unit,
    onMarkColorClick: (Any) -> Unit,
    onGestureButtonRename: (GestureButton) -> Unit = {},
    onSubGestureRename: (SubGesture) -> Unit = {},
    onSectionPositioned: (Int) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onSectionPositioned(coordinates.positionInWindow().y.toInt())
            },
        verticalArrangement = Arrangement.spacedBy(Spacing8),
    ) {
        Column {
            GestureEntryCard(
                title = stringResource(id = R.string.gesture_button),
                subtitle = stringResource(
                    id = R.string.home_enabled_count,
                    uiState.gestureButtons.count { it.enabled },
                    uiState.gestureButtons.size,
                ),
                icon = Icons.Default.TouchApp,
                onClick = onGestureHeaderClick,
            )
            GestureButtonList(
                visible = uiState.isGestureButtonListExpanded,
                buttons = uiState.gestureButtons,
                onItemClick = onGestureButtonClick,
                onCheckedChange = onGestureCheckedChange,
                onAddClick = onAddGesture,
                onMarkColorClick = onMarkColorClick,
                onRenameClick = onGestureButtonRename,
            )
        }
        Column {
            GestureEntryCard(
                title = stringResource(id = R.string.sub_gesture_list),
                subtitle = stringResource(
                    id = R.string.home_enabled_count,
                    uiState.subGestures.count { it.enabled },
                    uiState.subGestures.size,
                ),
                icon = Icons.Default.AllInclusive,
                onClick = onSubHeaderClick,
            )
            SubGestureList(
                visible = uiState.isSubGestureListExpanded,
                gestures = uiState.subGestures,
                onItemClick = onSubGestureClick,
                onCheckedChange = onSubCheckedChange,
                onAddClick = onAddSub,
                onMarkColorClick = onMarkColorClick,
                onRenameClick = onSubGestureRename,
            )
        }
    }
}
