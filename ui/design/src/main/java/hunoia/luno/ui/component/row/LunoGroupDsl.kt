package hunoia.luno.ui.component.row

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SegmentedGap

/** 组内一行的规格：位置与形状由 [LunoGroupRows] 统一注入，调用点不再传 `shape`。 */
internal class LunoRowSpec(
    val key: Any?,
    val content: @Composable (Shape) -> Unit,
)

/**
 * 组 DSL：声明式写行，组自己计数并算首 / 中 / 尾圆角。
 *
 * 构建器在容器组合作用域内执行，条件行（`if (state) row(...)`）会随同一批状态刷新；
 * 行内容仍是 `@Composable`，行内状态变化只重组该行。
 */
class LunoGroupScope internal constructor() {
    internal val rows = mutableListOf<LunoRowSpec>()

    private fun add(key: Any?, content: @Composable (Shape) -> Unit) {
        rows += LunoRowSpec(key = key, content = content)
    }

    /** 导航 / 设置行（等价于 [LunoSettingsRow]，shape 自动注入）。 */
    fun row(
        title: String,
        subtitle: String = "",
        icon: ImageVector? = null,
        iconBitmap: ImageBitmap? = null,
        iconModel: Any? = null,
        textIcon: TextIcon? = null,
        iconColorFilter: ColorFilter? = null,
        subtitleColor: Color? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        enabled: Boolean = true,
        stateDescription: String? = null,
        key: Any? = null,
    ) = add(key) { shape ->
        LunoSettingsRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            iconBitmap = iconBitmap,
            iconModel = iconModel,
            textIcon = textIcon,
            iconColorFilter = iconColorFilter,
            subtitleColor = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClick,
            onLongClick = onLongClick,
            leading = leading,
            trailing = trailing,
            shape = shape,
            enabled = enabled,
            stateDescription = stateDescription,
        )
    }

    /** 开关行（shape 自动注入）。 */
    fun switchRow(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        subtitle: String = "",
        icon: ImageVector? = null,
        iconBitmap: ImageBitmap? = null,
        iconModel: Any? = null,
        textIcon: TextIcon? = null,
        subtitleColor: Color? = null,
        onClick: (() -> Unit)? = null,
        leading: (@Composable () -> Unit)? = null,
        enabled: Boolean = true,
        key: Any? = null,
    ) = add(key) { shape ->
        LunoSwitchRow(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            subtitle = subtitle,
            icon = icon,
            iconBitmap = iconBitmap,
            iconModel = iconModel,
            textIcon = textIcon,
            subtitleColor = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClick,
            leading = leading,
            shape = shape,
            enabled = enabled,
        )
    }

    /** 可选中行（单 / 多选，shape 自动注入）。 */
    fun selectableRow(
        selected: Boolean,
        onSelectedChange: (Boolean) -> Unit,
        title: String = "",
        subtitle: String = "",
        subtitleColor: Color? = null,
        headline: (@Composable () -> Unit)? = null,
        marquee: Boolean = false,
        icon: ImageVector? = null,
        iconBitmap: ImageBitmap? = null,
        iconModel: Any? = null,
        textIcon: TextIcon? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        singleSelect: Boolean = false,
        containerColor: Color? = null,
        selectedContainerColor: Color? = null,
        key: Any? = null,
    ) = add(key) { shape ->
        LunoSelectableRow(
            title = title,
            selected = selected,
            onSelectedChange = onSelectedChange,
            subtitle = subtitle,
            subtitleColor = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            headline = headline,
            marquee = marquee,
            icon = icon,
            iconBitmap = iconBitmap,
            iconModel = iconModel,
            textIcon = textIcon,
            leading = leading,
            trailing = trailing,
            onLongClick = onLongClick,
            shape = shape,
            enabled = enabled,
            singleSelect = singleSelect,
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
        )
    }

    /** 下拉单选行（shape 自动注入）。 */
    fun dropdownRow(
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        subtitle: String? = null,
        enabled: Boolean = true,
        key: Any? = null,
    ) = add(key) { shape ->
        LunoDropdownRow(
            title = title,
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            subtitle = subtitle,
            enabled = enabled,
            shape = shape,
        )
    }

    /** 文本滑杆行（shape 自动注入）。 */
    fun sliderRow(
        value: Float,
        onValueChange: (Float) -> Unit,
        text: String,
        enabled: Boolean = true,
        sliderValueHint: Pair<String, String>? = null,
        valueDisplay: (@Composable (Float) -> String)? = null,
        onValueChangeFinished: (() -> Unit)? = null,
        valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        steps: Int? = null,
        key: Any? = null,
    ) = add(key) { shape ->
        LunoTextSlider(
            value = value,
            onValueChange = onValueChange,
            text = text,
            shape = shape,
            enabled = enabled,
            sliderValueHint = sliderValueHint,
            valueDisplay = valueDisplay,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }

    /** 行内输入（shape 自动注入）。 */
    fun inputRow(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String = "",
        subtitle: String = "",
        subtitleColor: Color? = null,
        isError: Boolean = false,
        singleLine: Boolean = true,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        trailing: (@Composable () -> Unit)? = null,
        key: Any? = null,
    ) = add(key) { shape ->
        LunoInputRow(
            label = label,
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            subtitle = subtitle,
            subtitleColor = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            isError = isError,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            trailing = trailing,
            shape = shape,
        )
    }

    /**
     * 逃生口：自定义行内容，仍拿到组算好的 [Shape]。
     * 复杂行（图表、编辑器、非标准容器）用它，不要回退到手写 `segmentedShape`。
     */
    fun item(key: Any? = null, content: @Composable (Shape) -> Unit) = add(key, content)

    /** 无形状需求的自定义行（内容自己管容器）也可用这个别名。 */
    fun customRow(key: Any? = null, content: @Composable (Shape) -> Unit) = item(key, content)
}

/**
 * 无接收者版本：给 `ThemeSection` / `SpacingSection` 这类独立 Composable 用。
 *
 * 它自己渲染一张组卡；在 `ColumnScope` / 页面 DSL 作用域里优先解析到对应的成员 / 扩展。
 */
@Composable
fun group(
    title: String = "",
    modifier: Modifier = Modifier,
    subtitle: String = "",
    badgeText: String? = null,
    badgeError: Boolean = false,
    titleTrailing: @Composable () -> Unit = {},
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    contentSpacing: Dp = SegmentedGap,
    titleBottomSpacing: Dp = ListSpacing,
    content: @Composable LunoGroupScope.() -> Unit,
) {
    val scope = LunoGroupScope()
    scope.content()
    if (scope.rows.isEmpty()) return
    LunoGroupRows(
        modifier = modifier,
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
        rows = scope.rows,
    )
}

/**
 * 旧页面 / `block { Column { ... } }` 的过渡入口：在任意 `ColumnScope` 里也能用组 DSL。
 *
 * 新页面优先用 `LunoPageListScope.group` / `LunoPageColumnScope.group`；这个扩展只为了让
 * 存量页面在不重排外层容器的前提下先去掉 `LunoGroup + segmentedShape` 手写计数。
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.group(
    title: String = "",
    modifier: Modifier = Modifier,
    subtitle: String = "",
    badgeText: String? = null,
    badgeError: Boolean = false,
    titleTrailing: @Composable () -> Unit = {},
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    contentSpacing: Dp = SegmentedGap,
    titleBottomSpacing: Dp = ListSpacing,
    content: @Composable LunoGroupScope.() -> Unit,
) {
    val scope = LunoGroupScope()
    scope.content()
    if (scope.rows.isEmpty()) return
    LunoGroupRows(
        modifier = modifier,
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
        rows = scope.rows,
    )
}

/** 把组 DSL 收集到的行渲染成一张组卡；形状按组内下标自动计算。 */
@Composable
internal fun LunoGroupRows(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badgeText: String?,
    badgeError: Boolean,
    titleTrailing: @Composable () -> Unit,
    collapsible: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    contentSpacing: Dp,
    titleBottomSpacing: Dp,
    rows: List<LunoRowSpec>,
) {
    if (rows.isEmpty()) return
    LunoGroup(
        modifier = modifier,
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
    ) {
        rows.forEachIndexed { index, row ->
            key(row.key ?: index) {
                row.content(segmentedShape(index, rows.size))
            }
        }
    }
}