package hunoia.luno.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.design.R
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.input.rememberControlledTextFieldState
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.glassCapsule

/**
 * 玻璃胶囊内联搜索框（动作选择页底栏与页面常驻搜索框共用）：不用 M3 OutlinedTextField ——
 * 它自带 56dp 最小高度与上下内边距，在 fontScale 1.4 的设备上会被胶囊的 56dp 内高裁掉
 * 文字底部。这里用 BasicTextField 自己控制排版，行高随字体自然居中，任何 fontScale 都不会被裁。
 *
 * 高度由容器给（调用方用 glassCapsule 包裹并限定高度）；[focusRequester] 可选：
 * 动作选择页展开时自动聚焦用，常驻搜索框不传（进页不弹键盘）。
 * 清空语义遵循 ui-guidelines §8：框内 × 只清空 query。
 */
@Composable
fun LunoSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val textStyle = MaterialTheme.typography.bodyLarge
    val interactionSource = remember { MutableInteractionSource() }
    val fieldState = rememberControlledTextFieldState(query, onQueryChange)
    Row(
        modifier = modifier
            .fillMaxHeight()
            // 点输入框左侧放大镜/内边距也要能聚焦：BasicTextField 只覆盖文字那一块。
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { runCatching { focusRequester?.requestFocus() } },
            )
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            state = fieldState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorator = TextFieldDecorator { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            PressIconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.search_clear_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}


/** 搜索胶囊与取消胶囊的间隙。 */
private val SearchCapsuleGap = 8.dp

/**
 * 底栏搜索的**浮动双胶囊**（ui-guidelines §4.3 搜索型底栏）：两颗**独立玻璃胶囊**并排，
 * 不再套在外层底栏胶囊里（避免一层套一层，参考主页底栏玻璃胶囊族与 FlClash 页脚
 * docked 搜索）——
 *
 * - 搜索胶囊（`weight(1f)`，通栏主件）：放大镜 + 输入 + 有字时框内清空 ×；
 * - 取消胶囊（见方）：× 收起搜索（清 query + 收键盘 + 关搜索态，由调用方 [onCancel] 定义）。
 *
 * 两颗都用 [glassCapsule]（阴影 + 模糊 + 描边 + 内边距），列表从其下穿过。
 * 进场：FlClash 同款弹簧（mass 1 / stiffness 390 / dampingRatio 0.75）——搜索胶囊从左侧
 * 滑入淡入，取消胶囊从 0 放大淡入；退场随所在栏的模式切换一起走，不另排。
 * 高度 / 宽度由调用方决定（与底栏同族几何），展开后自动聚焦。
 */
@Composable
fun LunoCapsuleSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onCancel: () -> Unit,
    cancelContentDescription: String,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed) {
        if (placed) runCatching { focusRequester?.requestFocus() }
    }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 390f))
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchCapsuleGap),
    ) {
        // 搜索玻璃胶囊：通栏主件。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = enter.value
                    translationX = (1f - enter.value) * 24.dp.toPx()
                }
                .glassCapsule(hazeState),
        ) {
            LunoSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                focusRequester = focusRequester,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { placed = true },
            )
        }
        // 取消玻璃胶囊：见方，弹簧放大淡入。
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = enter.value
                    scaleY = enter.value
                    alpha = enter.value
                }
                .glassCapsule(hazeState)
                .pressClickable(role = Role.Button, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = cancelContentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

