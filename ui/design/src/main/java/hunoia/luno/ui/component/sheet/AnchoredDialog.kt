package hunoia.luno.ui.component.sheet

import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import hunoia.luno.ui.component.panel.awaitPanelExitFrame
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
 * 焦点时尝试拉起键盘；冷启动时窗口焦点早于输入框焦点，系统会忽略该状态（实测
 * `ImeVisibilityStateComputer: SOFT_INPUT_STATE_ALWAYS_VISIBLE is ignored`），所以内容里的显式
 * `show(ime)` 仍是主路径。等首个 IME inset 到达才起播卡片（`startOnIme`），键盘与卡片同一时刻开始。
 * **卡片内容必须始终参与组合**：输入框聚焦发生在内容里，若把内容一起 gate 在 IME 之后就会死锁，
 * 只能等超时（曾是「弹窗打开慢 600ms、键盘随后才弹」的根因）。退场只 `hide()`，**不等 IME 落定**——
 * 键盘是独立窗口，会自己把下滑动画播完，拿着窗口等反而触发系统再 `show` 一次（「键盘往回弹一下」）。
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
    val state = rememberAnchoredDialogState()

    Dialog(onDismissRequest = state::dismiss, properties = properties) {
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
        AnchoredDialogSurface(
            state = state,
            onDismiss = onDismiss,
            cardModifier = cardModifier,
            contentPadding = contentPadding,
            // 自动弹键盘的弹层等 IME 首次可见再起播：窗口先获得焦点、键盘后到，
            // 不 gate 就会「先出弹窗、键盘才弹出」，并且首帧 inset=0 会让卡片先画在屏幕底部。
            // 内容始终参与组合（见 AnchoredDialogSurface），IME 才能被输入框拉起。
            startOnIme = imeAutoShow,
            content = content,
        )
    }
}

/**
 * 无窗口的弹层内容（透明 Activity 直接铺一屏时用，如部件待办编辑弹窗）。
 *
 * 与 [AnchoredDialog] 共用遮罩 / 卡片 / 两段式退场，只是不建 Compose `Dialog` 子窗口：
 * 弹窗内容与宿主 Activity 同属一个窗口，没有第二个窗口的焦点延迟；卡片进场与键盘上滑由
 * `startOnIme` 对齐，也不会出现「先画出卡片、IME inset 晚到一帧再把卡片顶上去」的双窗口闪动。
 * 宿主需要在 `onCreate` 里设好窗口的 softInputMode。
 */
@Composable
fun AnchoredDialogContent(
    onDismiss: () -> Unit,
    cardModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LargeGutter),
    // true = 等 IME inset 首次可见再起播（宿主窗口已设 ALWAYS_VISIBLE）：卡片从键盘上方滑入，
    // 不再先画在屏幕底部、再被 inset 顶上去。只 gate 动画，内容始终组合（否则输入框不存在，
    // 拉不起 IME）。
    startOnIme: Boolean = false,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val state = rememberAnchoredDialogState()
    AnchoredDialogSurface(
        state = state,
        onDismiss = onDismiss,
        cardModifier = cardModifier,
        contentPadding = contentPadding,
        startOnIme = startOnIme,
        content = content,
    )
}

/** [AnchoredDialog] / [AnchoredDialogContent] 的进场、退场与关闭状态。 */
private class AnchoredDialogState {
    internal var closing by mutableStateOf(false)
        private set

    /** 开始两段式退场；重复调用无副作用。 */
    fun dismiss() {
        if (!closing) closing = true
    }
}

/** 进场状态挂在组合里，退场播完再由 [AnchoredDialogSurface] 回调宿主。 */
@Composable
private fun rememberAnchoredDialogState(): AnchoredDialogState = remember { AnchoredDialogState() }

@Composable
private fun AnchoredDialogSurface(
    state: AnchoredDialogState,
    onDismiss: () -> Unit,
    cardModifier: Modifier,
    contentPadding: PaddingValues,
    startOnIme: Boolean = false,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val closing = state.closing
    val dismiss = state::dismiss
    // 面板家族 spec：进出同长同曲线，不再分别写 170 / 140。
    val panelMotion = panelSpec()
    val view = LocalView.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    // 卡片内容必须**从第一帧就参与组合**：输入框的聚焦 / 拉起键盘都发生在内容里，
    // 若用 AnimatedVisibility 把内容一起 gate 到「IME 可见」之后，内容不存在就永远
    // 拉不起 IME，只能等超时兜底（实测每次开弹窗白等 600ms，键盘随后才弹）。
    // 因此这里始终组合内容，只用 progress 控制位移与透明度：等 IME 可见后再起播，
    // 卡片从键盘上方滑入，键盘与卡片同一时刻开始。
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (startOnIme) {
            // IME 拉起失败时不能一直不显示卡片：超时兜底，之后再走常规聚焦 / 补 show。
            withTimeoutOrNull(ImeBootstrapTimeoutMs) {
                snapshotFlow { imeVisible }.first { it }
            }
        }
        progress.animateTo(1f, panelMotion.tween())
    }

    // 关窗只等退场动画：键盘是独立窗口，它自己会把下滑动画播完（实测拆掉弹窗窗口不会截断它）；
    // 反而如果我们自己拿着窗口等它，期间系统会因为 controls 变更再 show 一次，
    // MIUI 输入法就把工具栏又滑上来一条（用户看到的「键盘回弹」）。
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        if (imeVisible) {
            val imm = view.context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
        progress.animateTo(0f, panelMotion.tween())
        // 退场末帧先画出来再拆窗：animateTo 的续体在末帧提交前恢复（ui-guidelines §5.1）。
        awaitPanelExitFrame()
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // 遮罩单独一层：淡入淡出走 graphicsLayer（不每帧重建颜色），也不把卡片一起淡掉。
        // 尚未起播时不吃点击：内容已经在组合里等 IME，此时窗口可见性还没确立。
        DialogScrim(
            alpha = progress.value,
            onClick = if (progress.value > 0f) dismiss else null,
        )
        Box(
            // 底部 inset 取并集（ui-guidelines §3.2-3）：ime 已含导航栏，不能相加。
            // **必须直接用 inset 本身**：Compose 的 `WindowInsets.ime` 已经是键盘的逐帧动画值
            // （实测 0→819 逐帧派发），再叠一层 `animateDpAsState` 等于给同一个位移做两级低通，
            // 进场会慢半拍、退场会拖在键盘后面（「键盘收起时弹窗没有跟随」）。
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
            ),
        ) {
            // 位移 / 透明度从自身测得的尺寸派生：不需要额外读卡片高度，也不会每帧重排。
            DialogCard(
                modifier = cardModifier.graphicsLayer {
                    translationY = (1f - progress.value) * size.height
                    alpha = progress.value
                },
                contentPadding = contentPadding,
            ) {
                content(dismiss)
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
