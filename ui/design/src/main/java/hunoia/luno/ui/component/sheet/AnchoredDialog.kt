package hunoia.luno.ui.component.sheet

import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import hunoia.luno.ui.theme.DialogScrimAlpha
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.ImeBootstrapTimeoutMs
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.panelSpec
import hunoia.luno.ui.theme.tween
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 底部锚定弹层（ui-guidelines §4.4）：遮罩 + 底部卡片 + 进出场动画。
 *
 * 单一实现：所有 Dialog 形态的弹层都从这里出，页面 / 功能模块不再各写一套 `Dialog + Box + 卡片`。
 * [DialogCard] 只负责卡片本体（可单独用在全屏 Activity 里，如部件确认弹窗）。
 *
 * 关闭是**两段式**：内容里的 [dismiss] 只开始播退场，播完才回调 [onDismiss] 让宿主真正移除窗口。
 * 因此宿主不要在内容里同步卸载本弹层，否则退场被跳过（退化为瞬时消失）。
 *
 * 键盘（ui-guidelines §3.2-7）：进场用窗口的 `SOFT_INPUT_STATE_ALWAYS_VISIBLE` 让系统在窗口获得
 * 焦点时自己拉起键盘，和卡片进场同一段动画；退场先 `hide()` 等 IME 落定，再清焦点、移除窗口。
 * 窗口属性只在创建时设一次——逐帧改会重算 IME 可见性，把正在下滑的键盘重新拉起。
 */
@Composable
fun AnchoredDialog(
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LargeGutter),
    properties: DialogProperties = AnchoredDialogProperties,
    imeAutoShow: Boolean = false,
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

    Dialog(onDismissRequest = dismiss, properties = properties) {
        // Dialog 是独立窗口且半透明：ADJUST_NOTHING 让窗口不自己缩放/平移，
        // 键盘占位统一交给 imePadding()（否则两套让位叠加，内容被顶两截）。
        val view = LocalView.current
        val density = LocalDensity.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val imeInsets = WindowInsets.ime
        val imeVisible by remember(imeInsets, density) {
            derivedStateOf { imeInsets.getBottom(density) > 0 }
        }
        // 窗口属性只在窗口创建时设一次。**不要放进 SideEffect**：遮罩动画每帧重组，逐帧
        // setSoftInputMode / setDimAmount 都会触发一次窗口属性变更，系统就重算一轮 IME 可见性，
        // 已实测在关窗时把正在下滑的键盘重新拉起（ImeTracker: CONTROLS_CHANGED show），
        // 表现就是键盘「往回弹一下」。
        LaunchedEffect(dialogWindow) {
            val window = dialogWindow ?: return@LaunchedEffect
            // 关掉窗口 dim：它不跟退场动画走，会在窗口移除时硬切；遮罩改为 DialogScrim。
            window.setDimAmount(0f)
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    if (imeAutoShow) {
                        // 让系统在窗口获得焦点时自己拉起键盘：不等 requestFocus + show 那一两帧，
                        // 卡片进场与键盘上滑同时开始（「先出弹窗、再弹键盘」就是等出来的）。
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    } else {
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                    },
            )
            if (!imeAutoShow) return@LaunchedEffect
            // 键盘真正出现后只清一次状态位：留着的话用户手动收了键盘，窗口再获焦点
            //（返回栈切换、系统弹窗）又会自己弹出来。
            withTimeoutOrNull(ImeBootstrapTimeoutMs) {
                snapshotFlow { imeVisible }.first { it }
            }
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        // 遮罩：与卡片同一段退场动画（panelSpec），比窗口 dim 可控，也不会在关窗时闪一下。
        val scrimAlpha = remember { Animatable(0f) }
        LaunchedEffect(visible.targetState) {
            scrimAlpha.animateTo(
                targetValue = if (visible.targetState) 1f else 0f,
                animationSpec = panelMotion.tween(),
            )
        }
        // 关窗只等退场动画：键盘是独立窗口，它自己会把下滑动画播完（实测拆掉弹窗窗口不会截断它）；
        // 反而如果我们自己拿着窗口等它，期间系统会因为 controls 变更再 show 一次，
        // MIUI 输入法就把工具栏又滑上来一条（用户看到的「键盘回弹」）。
        LaunchedEffect(closing) {
            if (!closing || !imeVisible) return@LaunchedEffect
            val imm = view.context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        LaunchedEffect(visible.isIdle, visible.currentState) {
            if (closing && visible.isIdle && !visible.currentState) onDismiss()
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            // 遮罩单独一层：淡入淡出走 graphicsLayer（不每帧重建颜色），也不把卡片一起淡掉
            // （卡片自己有 fadeIn / fadeOut，套在一起会双重淡出）。
            DialogScrim(alpha = scrimAlpha.value, onClick = dismiss)
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
 * 弹层遮罩层：`scrim` role × [DialogScrimAlpha]，铺满整屏（含系统栏底）。
 *
 * 单一实现：窗口的 `dimAmount` / `backgroundDimEnabled` 必须关掉（它不跟退场动画，会在窗口移除的
 * 那一帧硬切，表现就是关窗最后「背景一下子变亮」），遮罩统一由本组件画。
 * [onClick] 为空 = 不可点（纯视觉）；卡片本体自己吞点击（见 [DialogCard]），不会误触关闭。
 *
 * @param alpha 逐帧透明度（进场 / 退场动画传入），走 `graphicsLayer` 不重建颜色。
 */
@Composable
fun DialogScrim(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = DialogScrimAlpha)
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(scrim)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
    )
}

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
