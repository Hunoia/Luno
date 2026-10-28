package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * sheet 内容的滚动衔接（[LunoSheet] 内部使用）：内容自带滚动容器时，手指在内容上拖
 * 会同时触发 sheet 的 dismissal。传 [OptimizedScrollState.Scroll] 后，内容未滚到顶时
 * sheet 不消费滚动，滚到顶才把手势还给 sheet。
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
        // imePadding 放这里而不是 LunoSheet：键盘弹起时整个弹层上移，
        // 弹层内的输入框不会被键盘挡住（配合内容自身的 verticalScroll 生效）。
        Column(Modifier.nestedScroll(connection).imePadding()) {
            content()
        }
    } else {
        Column(Modifier.imePadding()) {
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
