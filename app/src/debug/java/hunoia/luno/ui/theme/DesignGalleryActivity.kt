package hunoia.luno.ui.theme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LoadingState
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.page.LunoPageList
import hunoia.luno.ui.component.PressTextButton

/**
 * 组件画廊（**仅 debug 变体**）：新页面前先在这里对照一套行 / 组 / 按钮 / 状态 / 色板。
 *
 * ```
 * adb shell am start -n hunoia.luno.debug/hunoia.luno.ui.theme.DesignGalleryActivity
 * ```
 */
class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SideGestureTheme {
                DesignGalleryScreen()
            }
        }
    }
}

@Composable
private fun DesignGalleryScreen() {
    var checked by remember { mutableStateOf(true) }
    var selected by remember { mutableIntStateOf(1) }
    var slider by remember { mutableFloatStateOf(0.4f) }
    var text by remember { mutableStateOf("Luno") }
    LunoPage(title = "组件画廊") {
        LunoPageList {
            group(title = "行") {
                row(
                    title = "设置行",
                    subtitle = "一行说明",
                    icon = Icons.Default.Settings,
                    onClick = {},
                )
                switchRow(
                    title = "开关行",
                    subtitle = "整行即开关",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
                dropdownRow(
                    title = "下拉行",
                    options = listOf("连续曲率", "标准圆弧", "跟随全局"),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
                sliderRow(
                    text = "滑杆行",
                    value = slider,
                    onValueChange = { slider = it },
                    valueDisplay = { "${(it * 100).toInt()}%" },
                    valueRange = 0f..1f,
                )
                inputRow(
                    label = "输入行",
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "输入内容",
                )
            }
            group(title = "状态") {
                item {
                    EmptyState(message = "这里是一条空态说明", action = {
                        PressTextButton(onClick = {}) { Text("新建") }
                    })
                }
                item {
                    LoadingState(message = "正在加载")
                }
            }
            block {
                Text(
                    text = "主题色板",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = PageGutter,
                        top = CardInnerSpacing,
                        bottom = CardInnerSpacing,
                    ),
                )
            }
            block {
                PaletteRow()
            }
        }
    }
}

@Composable
private fun PaletteRow() {
    Row(
        modifier = Modifier.padding(horizontal = PageGutter),
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuiltinAlertPalettes.all.take(6).forEach { palette ->
            val colors = palette.colors.ifEmpty { listOf(MaterialTheme.colorScheme.primary.value.toInt()) }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(colors.first()), CircleShape),
            )
        }
    }
}