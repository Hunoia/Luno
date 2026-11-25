package hunoia.luno.ui.component.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.row.LunoGroupRows
import hunoia.luno.ui.component.row.LunoGroupScope
import hunoia.luno.ui.component.row.MaxRowsPerLazyItem
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.appHazeSource

/**
 * 页面内容 DSL：长页面用 [LunoPageList]，内容按组 / 块声明，容器与边距由框架算。
 *
 * 组会在超过 [MaxRowsPerLazyItem] 时自动分块，每块一张完整卡片（ui-guidelines §3.2 第 10 条）。
 */
@Composable
fun LunoPageList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentSpacing: Dp = ListSpacing,
    contentPadding: Dp = PageGutter,
    bottomContentPadding: Dp = ContentBottom,
    content: @Composable LunoPageListScope.() -> Unit,
) {
    val env = LocalLunoPageEnv.current
    val scope = LunoPageListScope()
    scope.content()
    val listModifier = modifier
        .fillMaxSize()
        .imePadding()
        .then(
            if (env?.hazeState != null) Modifier.appHazeSource(env.hazeState) else Modifier,
        )
        .then(
            if (env?.nestedScrollConnection != null) {
                Modifier.nestedScroll(env.nestedScrollConnection)
            } else {
                Modifier
            },
        )
    LazyColumn(
        modifier = listModifier,
        state = state,
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
        contentPadding = PaddingValues(
            start = contentPadding,
            end = contentPadding,
            top = env?.topPadding ?: 0.dp,
            bottom = (env?.bottomPadding ?: 0.dp) + bottomContentPadding,
        ),
    ) {
        scope.blocks.forEach { it() }
    }
}

/**
 * 内容 DSL 的注册作用域。
 *
 * 构建器在 [LunoPageList] 的组合作用域里执行，条件行读到的状态由容器跟踪；
 * 行内容本身仍是 `@Composable`，所以行内状态变化只重组该行。
 */
class LunoPageListScope internal constructor() {
    internal val blocks = mutableListOf<LazyListScope.() -> Unit>()

    @Composable
    fun group(
        title: String = "",
        subtitle: String = "",
        key: Any? = null,
        badgeText: String? = null,
        badgeError: Boolean = false,
        titleTrailing: @Composable () -> Unit = {},
        collapsible: Boolean = false,
        expanded: Boolean = true,
        onToggle: () -> Unit = {},
        contentSpacing: Dp = hunoia.luno.ui.theme.SegmentedGap,
        titleBottomSpacing: Dp = ListSpacing,
        content: @Composable LunoGroupScope.() -> Unit,
    ) {
        val groupScope = LunoGroupScope()
        groupScope.content()
        val rows = groupScope.rows
        if (rows.isEmpty()) return
        rows.chunked(MaxRowsPerLazyItem).forEachIndexed { chunkIndex, chunk ->
            val itemKey = key?.let { "$it#$chunkIndex" }
            val first = chunkIndex == 0
            val chunkTitleTrailing: @Composable () -> Unit =
                if (first) titleTrailing else ({})
            blocks += {
                item(key = itemKey) {
                    LunoGroupRows(
                        title = if (first) title else "",
                        subtitle = if (first) subtitle else "",
                        badgeText = if (first) badgeText else null,
                        badgeError = badgeError,
                        titleTrailing = chunkTitleTrailing,
                        collapsible = collapsible,
                        expanded = expanded,
                        onToggle = onToggle,
                        contentSpacing = contentSpacing,
                        titleBottomSpacing = titleBottomSpacing,
                        rows = chunk,
                    )
                }
            }
        }
    }

    /** 任意自绘块：进页面列表的一个 item，不参与组计数。 */
    fun block(key: Any? = null, content: @Composable () -> Unit) {
        blocks += { item(key = key) { content() } }
    }

    /** 兼容旧页面的单 item 写法；新页面优先用 [group] / [block]。 */
    fun item(key: Any? = null, content: @Composable () -> Unit) = block(key, content)

    /** 逃生口：直接写 `LazyListScope`（sticky header / 特殊列表）。 */
    fun lazy(content: LazyListScope.() -> Unit) {
        blocks += content
    }
}

/** 短页面用 Column 内容容器（无惰性窗口）；组不参与分块。 */
@Composable
fun LunoPageColumn(
    modifier: Modifier = Modifier,
    contentSpacing: Dp = ListSpacing,
    contentPadding: Dp = PageGutter,
    bottomContentPadding: Dp = ContentBottom,
    content: @Composable LunoPageColumnScope.() -> Unit,
) {
    val env = LocalLunoPageEnv.current
    val scope = LunoPageColumnScope()
    scope.content()
    hunoia.luno.ui.component.LunoColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .then(
                if (env?.hazeState != null) Modifier.appHazeSource(env.hazeState) else Modifier,
            )
            .then(
                if (env?.nestedScrollConnection != null) {
                    Modifier.nestedScroll(env.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        topPadding = env?.topPadding ?: 0.dp,
        bottomPadding = (env?.bottomPadding ?: 0.dp) + bottomContentPadding,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        scope.blocks.forEach { it() }
    }
}

class LunoPageColumnScope internal constructor() {
    internal val blocks = mutableListOf<@Composable () -> Unit>()

    @Composable
    fun group(
        title: String = "",
        subtitle: String = "",
        badgeText: String? = null,
        badgeError: Boolean = false,
        titleTrailing: @Composable () -> Unit = {},
        collapsible: Boolean = false,
        expanded: Boolean = true,
        onToggle: () -> Unit = {},
        contentSpacing: Dp = hunoia.luno.ui.theme.SegmentedGap,
        titleBottomSpacing: Dp = ListSpacing,
        content: @Composable LunoGroupScope.() -> Unit,
    ) {
        val groupScope = LunoGroupScope()
        groupScope.content()
        val rows = groupScope.rows
        if (rows.isEmpty()) return
        blocks += {
            LunoGroupRows(
                title = title,
                subtitle = subtitle,
                badgeText = badgeText,
                badgeError = badgeError,
                titleTrailing = titleTrailing,
                collapsible = collapsible,
                expanded = expanded,
                onToggle = onToggle,
                contentSpacing = contentSpacing,
                titleBottomSpacing = titleBottomSpacing,
                rows = rows,
            )
        }
    }

    fun block(content: @Composable () -> Unit) {
        blocks += content
    }

    /** 兼容旧页面的单块写法；新页面优先用 [group]。 */
    fun item(content: @Composable () -> Unit) = block(content)
}

/** 内容自己持有滚动与 `weight(1f)`：动作选择页、黑名单页。模板只给环境与整屏 Box。 */
@Composable
fun LunoPageRaw(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(LunoPageEnv) -> Unit,
) {
    val env = LocalLunoPageEnv.current ?: LunoPageEnv()
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        content(env)
    }
}