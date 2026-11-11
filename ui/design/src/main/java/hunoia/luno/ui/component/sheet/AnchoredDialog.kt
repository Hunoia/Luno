package hunoia.luno.ui.component.sheet

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.panelSpec
import hunoia.luno.ui.theme.tween

/**
 * 底部锚定弹层（ui-guidelines §4.4）：遮罩 + 底部卡片 + 进出场动画。
 *
 * 单一实现：所有 Dialog 形态的弹层都从这里出，页面 / 功能模块不再各写一套 `Dialog + Box + 卡片`。
 * [DialogCard] 只负责卡片本体（可单独用在全屏 Activity 里，如部件确认弹窗）。
 *
 * 关闭是**两段式**：内容里的 [dismiss] 只开始播退场，播完才回调 [onDismiss] 让宿主真正移除窗口。
 * 因此宿主不要在内容里同步卸载本弹层，否则退场被跳过（退化为瞬时消失）。
 */
@Composable
fun AnchoredDialog(
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LargeGutter),
    properties: DialogProperties = AnchoredDialogProperties,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    // 进场先挂载再置位，让 AnimatedVisibility 真正播一次；退场播完再回调宿主（镜像 OcrResultActivity）。
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    // 面板家族 spec：进出同长同曲线，不再分别写 170 / 140。
    val panelMotion = panelSpec()
    var closing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            visible.targetState = false
        }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (closing && visible.isIdle && !visible.currentState) onDismiss()
    }

    Dialog(onDismissRequest = dismiss, properties = properties) {
        // Dialog 是独立窗口且半透明：ADJUST_NOTHING 让窗口不自己缩放/平移，
        // 键盘占位统一交给 imePadding()（否则两套让位叠加，内容被顶两截）。
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 遮罩点按关闭；卡片本体自己吞点击（见 DialogCard），不会误触关闭。
                // 遮罩铺满整屏（含系统栏底），只把卡片让位给 inset。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.windowInsetsPadding(
                    // 底部 inset 取并集（ui-guidelines §3.2-3）：ime 已含导航栏，不能相加。
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                ),
                enter = slideInVertically(panelMotion.tween()) { it } +
                    fadeIn(panelMotion.tween()),
                exit = slideOutVertically(panelMotion.tween()) { it } +
                    fadeOut(panelMotion.tween()),
            ) {
                DialogCard(modifier = cardModifier, contentPadding = contentPadding) {
                    content(dismiss)
                }
            }
        }
    }
}

/** [AnchoredDialog] 的默认窗口约束：边到边（IME inset 才会派发到内容里，`imePadding()` 才有值）。 */
val AnchoredDialogProperties: DialogProperties
    get() = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )

/**
 * 底部锚定卡片（容器角色 + 实色底 + 内边距），只画卡片本身。
 *
 * 用 `Surface` 而不是 `background`：`contentColor` 会把 `LocalContentColor` 设成 `onSurface`，
 * 否则默认值是黑色，深色模式下未显式指定颜色的文字 / 图标（月历月份、导航箭头）会变黑（§2.2）。
 *
 * [modifier] 作用在卡片本体上（内边距之内），`heightIn` / `verticalScroll` 等由调用方追加。
 */
@Composable
fun DialogCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LargeGutter),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = PageGutter, vertical = PageGutter)
            .fillMaxWidth()
            .then(modifier),
        shape = ExtraLargeShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 吞掉卡片内的点击：遮罩的「点外部关闭」不能穿透到卡片空白处。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
            content = content,
        )
    }
}
