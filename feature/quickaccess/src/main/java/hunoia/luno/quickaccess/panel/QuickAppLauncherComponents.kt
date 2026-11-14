package hunoia.luno.quickaccess.panel
import hunoia.luno.ui.theme.*

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import hunoia.luno.ui.component.pressCombinedClickable
import hunoia.luno.ui.theme.MediumShape
import hunoia.luno.quickaccess.R
import coil3.compose.AsyncImage
import hunoia.luno.quickaccess.QuickLaunchFacade
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.bridge.ui.hapticTap

/**
 * 键盘按键。用类型区分功能键而不是比较中文字符串——`label` 只用于显示，逻辑分支全走类型，
 * 改文案不会再悄悄改掉行为。[longPressDigit] 是长按时送出的数字 token，界面上没有提示。
 */
internal sealed interface KeyboardKey {
    val label: String
    val longPressDigit: String?

    data class Letter(
        override val label: String,
        val token: String,
        override val longPressDigit: String? = null,
    ) : KeyboardKey

    object Adjust : KeyboardKey {
        override val label: String = "调整"
        override val longPressDigit: String? = null
    }

    object Clear : KeyboardKey {
        override val label: String = "清空"
        override val longPressDigit: String? = null
    }
}

@Composable
internal fun KeyboardRow(
    view: View,
    keys: List<KeyboardKey>,
    keyHeight: Dp,
    onClear: (() -> Unit)? = null,
    onAdjust: (() -> Unit)? = null,
    onToken: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - KeyboardGap * (keys.size - 1)) / keys.size
        Row(horizontalArrangement = Arrangement.spacedBy(KeyboardGap), modifier = Modifier.fillMaxWidth()) {
            keys.forEach { key ->
                Surface(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(keyHeight)
                        .clip(MediumShape)
                        // 键盘键命中最密：反馈统一走按压缩放（§5.3），长按清空/调整照旧。
                        .pressCombinedClickable(
                            onClick = {
                                view.hapticTap()
                                when (key) {
                                    is KeyboardKey.Adjust -> onAdjust?.invoke()
                                    is KeyboardKey.Clear -> onClear?.invoke()
                                    is KeyboardKey.Letter -> onToken(key.token)
                                }
                            },
                            onLongClick = {
                                view.hapticTap()
                                when (key) {
                                    is KeyboardKey.Clear -> onClear?.invoke()
                                    is KeyboardKey.Adjust -> Unit
                                    is KeyboardKey.Letter -> key.longPressDigit?.let(onToken)
                                }
                            }
                        ),
                    // 功能键与字母键共用一层底色，不再用 tertiary/secondary 二分：
                    // 靠图标（Tune / ClearAll）和末行首末位置区分。
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        when (key) {
                            is KeyboardKey.Adjust -> Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.quick_app_launcher_adjust))
                            is KeyboardKey.Clear -> Icon(Icons.Outlined.ClearAll, contentDescription = stringResource(R.string.quick_app_launcher_clear_input))
                            is KeyboardKey.Letter -> Text(key.label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个应用项：只有图标，**刻意没有文字标签**——加标签会把候选行仅 ~92dp 的高度切给文字、
 * 挤掉图标，也和「图标即语义」的搜索排序取向冲突。
 * 同样**刻意不给「已禁用」加视觉区分**：禁用是罕见配置，为它加灰度或删除线会在常态下多一层
 * 噪音；[AppInfo] 的禁用状态只影响点击行为，不改外观。
 */
@Composable
internal fun AppItem(
    app: AppInfo,
    iconHeight: Dp? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    /** 编辑态 false：不挂点击/长按手势，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.pressCombinedClickable(
                        onClick = { onClick() },
                        onLongClick = { onLongPress() },
                    )
                } else {
                    Modifier
                }
            )
            .padding(AppItemPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val iconMod = if (iconHeight != null) {
            Modifier.height(iconHeight).fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().aspectRatio(1f)
        }.clip(MediumShape)
        val density = LocalDensity.current
        val request = remember(app.packageName, app.userId, app.className, density) {
            val decodePx = with(density) { AppIconDecodeSize.roundToPx() }
            QuickLaunchFacade.appIconRequest(context, app.packageName, decodePx, app.userId)
        }
        // 图标压在面板底色上，不再叠一层占位底色（面板底是唯一的一层底色）。
        // Box 留着是因为图标异步到位时尺寸不能变——用 matchParentSize 而不是 if/else，
        // 这样图标晚到也不会让布局跳一帧。
        Box(modifier = iconMod) {
            AsyncImage(
                model = request,
                contentDescription = app.label,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * 面板底部的药丸：点击触发 [onClick]，上拖超过 [DragThresholdPx] 触发 [onDragUp]；调用方要挂在
 * `Box` 里并自己 `align(Alignment.BottomCenter)`。
 * [dragEnabled] 对应网格那处「键盘已展开就不再响应上拖」；[hapticOnClick] 对应原先只有网格有
 * 点击震动、另两处没有的差异——都是原样保留，不是新行为。
 */
@Composable
internal fun Pill(
    onClick: () -> Unit,
    onDragUp: () -> Unit,
    modifier: Modifier = Modifier,
    dragEnabled: Boolean = true,
    hapticOnClick: Boolean = true,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(dragEnabled) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        if (dragEnabled && drag > DragThresholdPx) onDragUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(vertical = PillPaddingVertical)
                .clickable {
                    if (hapticOnClick) {
                        view.hapticTap()
                    }
                    onClick()
                }
        ) {
            Box(
                modifier = Modifier
                    .width(PillWidth)
                    .height(PillHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PillAlpha))
            )
        }
    }
}