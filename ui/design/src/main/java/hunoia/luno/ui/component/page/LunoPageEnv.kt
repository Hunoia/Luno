package hunoia.luno.ui.component.page

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * 页面骨架向内容层提供的唯一环境。
 *
 * 页面不再自己量顶栏 / 底栏、不再自己算 `contentPadding`：内容助手从这里读
 * [topPadding]（顶栏含状态栏让位后的实测高）与 [bottomPadding]（悬浮底栏实测高）。
 * [hazeState] 是整屏唯一的采样源，[nestedScrollConnection] 由顶栏收拢行为提供。
 */
data class LunoPageEnv(
    val topPadding: Dp = 0.dp,
    val bottomPadding: Dp = 0.dp,
    val hazeState: HazeState? = null,
    val nestedScrollConnection: NestedScrollConnection? = null,
    /** 有悬浮底栏时，内容底部留白由框架负责（底栏实测高 + 内容呼吸区）。 */
    val hasBottomBar: Boolean = false,
)

internal val LocalLunoPageEnv = compositionLocalOf<LunoPageEnv?> { null }