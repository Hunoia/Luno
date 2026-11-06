package hunoia.luno.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import sv.lib.squircleshape.CornerSmoothing
import sv.lib.squircleshape.SquircleShape

/**
 * 形状软度画廊（**仅 debug 源集，不进产品代码**）。
 *
 * 目的：在定规范之前先肉眼比 3 档平滑度，对应 ui-guidelines §2.1.3 的标定项。
 * 三列 = Small 0.2 / Medium 0.48 / High 0.67；每格标注该档下**实际使用**的几何半径。
 *
 * 判定四件事：
 * 1. 直边与弧的切点是否消失；
 * 2. 端部的药丸感还剩多少；
 * 3. 小元素（32dp chip / 图标底）有没有变方或变肿；
 * 4. 弧上有没有 1px 毛刺（越界时会出现）。
 *
 * 注意：预览里没有 haze、阴影和动态取色，玻璃面观感只能上真机看（方案 A 的 trial 分支）。
 * 定完档位就删掉这个文件与 build.gradle.kts 里的两条 debugImplementation。
 */

private val Trials = listOf(
    "Small 0.2" to CornerSmoothing.Small,
    "Medium 0.48" to CornerSmoothing.Medium,
    "High 0.67" to CornerSmoothing.High,
)

/** 容器角的几何上限：r ≤ h / (2(1+s))，与 ui-guidelines §2.1.2 第 3 条一致。 */
private fun capOf(h: Dp, smoothing: Int): Dp = h / (2f * (1f + smoothing / 100f))

/** 容器角：档位不许超过上限，超了就按上限取。 */
private fun containerShape(h: Dp, smoothing: Int, preferred: Dp): Shape =
    SquircleShape(minOf(preferred, capOf(h, smoothing)), smoothing)

/** 胶囊角：零直边百分比 50/(1+s)，与高度无关。 */
private fun capsuleShape(smoothing: Int): Shape = PercentSquircleShape(50f / (1f + smoothing / 100f), smoothing)


private fun capsulePercent(smoothing: Int): Int = (50f / (1f + smoothing / 100f)).toInt()

private enum class Kind { Container, Capsule, Circle }

private data class Spec(
    val title: String,
    val height: Dp,
    val preferred: Dp?,
    val kind: Kind,
)

private val Specs = listOf(
    Spec("行 h=50dp（现 20dp）", 50.dp, 20.dp, Kind.Container),
    Spec("大卡 h=96dp（现 32dp）", 96.dp, 32.dp, Kind.Container),
    Spec("图标底 h=32dp", 32.dp, 20.dp, Kind.Container),
    Spec("chip h=32dp（现 12dp）", 32.dp, 12.dp, Kind.Container),
    Spec("底栏胶囊 h=64dp", 64.dp, null, Kind.Capsule),
    Spec("指示 pill h=56dp", 56.dp, null, Kind.Capsule),
    Spec("搜索胶囊 h=44dp", 44.dp, null, Kind.Capsule),
    Spec("正圆对照 h=32dp", 32.dp, null, Kind.Circle),
)

private fun Dp.short(): String = ((value * 10f).roundToInt() / 10f).toString()

@Composable
private fun Gallery(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(20.dp),
    ) {
        Text("形状软度画廊", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(
            "三列 = 三档平滑度；下方数字是该档实际几何半径。切点 / 药丸感 / 小元素是否变方 / 弧上毛刺。",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Specs.forEach { spec ->
            Text(
                spec.title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Trials.forEach { (name, smoothing) ->
                    val preferred = spec.preferred
                    val shape = when (spec.kind) {
                        Kind.Container -> containerShape(spec.height, smoothing, preferred ?: 0.dp)
                        Kind.Capsule -> capsuleShape(smoothing)
                        Kind.Circle -> CircleShape
                    }
                    val note = when (spec.kind) {
                        Kind.Container -> "r=${minOf(preferred ?: 0.dp, capOf(spec.height, smoothing)).short()}"
                        Kind.Capsule -> "~${capsulePercent(smoothing)}%"
                        Kind.Circle -> "50%"
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(spec.height)
                                .background(scheme.surfaceContainerHigh, shape)
                                .border(1.dp, scheme.outlineVariant, shape),
                        )
                        Text(
                            "$name\n$note",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryContent(dark: Boolean) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Gallery()
    }
}

/**
 * 画廊的真机入口：`adb shell am start -n hunoia.luno.debug/hunoia.luno.ui.theme.ShapeGalleryActivity`。
 * 只在 debug 源集，release 不包含。
 */
@Composable
fun ShapeGalleryScreen() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Gallery(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
    }
}

@Preview(name = "浅色", widthDp = 460, heightDp = 1240, showBackground = true)
@Composable
private fun ShapeGalleryLight() = GalleryContent(dark = false)

@Preview(
    name = "深色",
    widthDp = 460,
    heightDp = 1240,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ShapeGalleryDark() = GalleryContent(dark = true)
