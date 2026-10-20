package hunoia.luno.gridpanel.ui

import androidx.compose.ui.graphics.Color

/**
 * 面板底色固定近黑、内容固定浅色：不随深色模式变化（需求拍板）。
 * cardAlpha 透明度在 GridPanelCard 叠加。
 */
val gridPanelCardColor: Color = Color(0xFF000000)

/** 卡片上的自绘内容色（标题/时间/图标/网格线等）。 */
val gridPanelOnCardColor: Color = Color(0xFFEDEDED)
