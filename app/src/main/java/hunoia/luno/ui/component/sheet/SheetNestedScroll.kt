package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * sheet 内容的滚动衔接（[LunoSheet] 内部使用）。
 *
 * 内容自带滚动容器（`rememberScrollState` 的 Column）时，手指在内容上拖会同时触发
 * sheet 的手势 dismissal，两边争抢。传 [OptimizedScrollState.Scroll] 后，内容未滚到
 * 顶时 sheet 不消费滚动，滚到顶才把手势还给 sheet。
 */
sealed class OptimizedScrollState {
    data object None : OptimizedScrollState()

    data class Scroll(val state: ScrollState) : OptimizedScrollState()
}

@Composable
internal fun SheetNestedContent(
    scrollState: OptimizedScrollState = OptimizedScrollState.None,
    content: @Composable () -> Unit,
) {
    if (scrollState is OptimizedScrollState.Scroll) {
        val connection = remember(scrollState.state) {
            SheetNestedScrollConnection {
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

private class SheetNestedScrollConnection(
    private val isAtTop: () -> Boolean,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (isAtTop()) return Velocity.Zero
        return Velocity(x = 0f, y = available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (isAtTop()) return Offset.Zero
        return Offset(x = 0f, y = available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return Velocity.Zero
    }
}
