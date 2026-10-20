package hunoia.luno.gridpanel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 面板底色与内容色**反转**：暗色主题用柔白卡片、亮色主题用近黑卡片 ——
 * 暗色模式背景本来就是黑的，卡片再用黑就看不清；亮色模式同理反过来。
 * 部分快捷方式/小部件的底色逻辑本身就是这么反的。
 *
 * 判定走当前主题 surface 的亮度（不依赖 isSystemInDarkTheme：面板可能跑在
 * 自定义主题进程里）。内容色取对偶：柔白卡上配近黑内容，近黑卡上配柔白内容。
 */
private val CardLight = Color(0xFFF5F5F5)
private val CardDark = Color(0xFF1C1B1F)

@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** 卡片底色（叠 [hunoia.luno.config.model.GridPanel.cardAlpha] 透明度后上屏）。 */
@Composable
fun gridPanelCardColor(): Color = if (isDarkTheme()) CardLight else CardDark

/** 卡片上的自绘内容色（标题/时间/图标/网格线等），与底色成对。 */
@Composable
fun gridPanelOnCardColor(): Color = if (isDarkTheme()) CardDark else CardLight
