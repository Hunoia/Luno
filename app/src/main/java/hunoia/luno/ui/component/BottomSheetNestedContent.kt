package hunoia.luno.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.ExperimentalMaterial3Api

sealed class OptimizedScrollState {
    data object None : OptimizedScrollState()

    data class Scroll(val state: ScrollState) : OptimizedScrollState()
}

@Composable
fun BottomSheetNestedContent(
    scrollState: OptimizedScrollState = OptimizedScrollState.None,
    content: @Composable () -> Unit
) {
    if (scrollState is OptimizedScrollState.Scroll) {
        val connection = remember(scrollState.state) {
            BottomSheetNestedScrollConnection {
                scrollState.state.value <= 0
            }
        }
        Column(Modifier.nestedScroll(connection)) {
            content()
        }
    } else {
        Column {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptimizedBottomSheet(
    onDismissRequest: () -> Unit,
    scrollState: OptimizedScrollState = OptimizedScrollState.None,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        BottomSheetNestedContent(scrollState = scrollState) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        slideInVertically { it / 8 }
            ) {
                content()
            }
        }
    }
}

private class BottomSheetNestedScrollConnection(
    private val isAtTop: () -> Boolean
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (isAtTop()) return Offset.Zero
        return Offset(x = 0f, y = available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (isAtTop()) return Velocity.Zero
        return Velocity(x = 0f, y = available.y)
    }
}
