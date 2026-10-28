package hunoia.luno.gridpanel.internal

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor

/**
 * QS 风格滑条：粗胶囊轨道 + 起始图标，无 thumb/百分比；填充 = primary，轨道垫 onCard 半透明底衬。
 * 自绘而非 M3 Slider：M3 样式拗不成 QS 形状。
 * 手势一颗 pointerInput：相对拖动（按下不跳值，从当前值按位移增量调）；回调用 [rememberUpdatedState] 保鲜，否则竖向翻转后按首帧方向算。
 */
@Composable
private fun QsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    icon: ImageVector,
    iconDimmed: Boolean,
    vertical: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    val fill = MaterialTheme.colorScheme.primary
    val fraction = value.coerceIn(0f, 1f)
    // 手势里读「拖动开始时的值」：闭包捕获 composition 里的 fraction 会拿到
    // 拖动中途的最新值，增量就叠错了。rememberUpdatedState 保的是手势闭包的鲜。
    val startFraction by rememberUpdatedState(fraction)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val isVertical by rememberUpdatedState(vertical)
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            // 轨道底衬半透明 + 细描边：全透明的话 0% 时整条轨道消失，只剩填充
            // 一截悬在卡面上；半透既托住滑条形状，又不抢填充的 primary。描边勾出
            // 胶囊边缘，背景关掉时轨道也有清晰的轮廓。
            .background(onCard.copy(alpha = 0.12f))
            .border(1.dp, onCard.copy(alpha = 0.14f), RoundedCornerShape(percent = 50))
            .then(
                // enabled=false（编辑态）时不挂手势：事件落到层根的拖拽/缩放上，
                // 不然编辑态拖滑条 = 既改音量又拖部件。
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 不 consume down：方向未定前父级（纵向 scroll / 横向 pager）
                            // 也要能参与判定，否则从滑条起手的跨轴滑动会被整段吞掉。
                            val verticalAxis = isVertical
                            // 相对拖动：按下不跳值。无论落指在轨道哪，都从当前值起按
                            // 「位移 / 轨长」算增量；没过 touchSlop 的纯点按不改值 ——
                            // 想微调时一步跳到点击处太糙。
                            // baseValue 必须定格一次：startFraction 是 rememberUpdatedState，
                            // 循环里每帧读它拿到的是拖动中途的最新值，累计位移会被反复叠上去
                            // （f += Δ 每帧执行），轻轻一拖就冲到 0%/100%。
                            val baseValue = startFraction
                            val startPos = if (verticalAxis) down.position.y else down.position.x
                            val trackPx = (if (verticalAxis) size.height else size.width)
                                .toFloat().coerceAtLeast(1f)
                            var dragging = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (pointer.changedToUp()) {
                                    if (dragging) pointer.consume()
                                    break
                                }
                                val dx = pointer.position.x - down.position.x
                                val dy = pointer.position.y - down.position.y
                                if (!dragging) {
                                    val primary = if (verticalAxis) dy else dx
                                    val cross = if (verticalAxis) dx else dy
                                    val slop = viewConfiguration.touchSlop
                                    if (abs(primary) < slop && abs(cross) < slop) continue
                                    // 交叉轴先过阈值（横向滑条上的上下滑、竖向滑条上的左右滑）：
                                    // 不消费，交给父级滚动或翻页。
                                    if (abs(cross) > abs(primary)) break
                                    dragging = true
                                }
                                pointer.consume()
                                val pos = if (verticalAxis) pointer.position.y else pointer.position.x
                                val delta = pos - startPos
                                // 竖向从底部起填：向上滑（delta 为负）才是增大。
                                val raw = baseValue +
                                    (if (verticalAxis) -delta else delta) / trackPx
                                change(raw.coerceIn(0f, 1f))
                            }
                            if (dragging) finished()
                        }
                    }
                },
            ),
    ) {
        // 填充：横排从左伸、竖排从下往上长。
        Box(
            modifier = if (vertical) {
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(maxHeight * fraction)
            } else {
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * fraction)
            }.background(fill),
        )
        // 起始图标：不垫圆形底衬，直接坐在轨道上，不随进度动；横向钉左端、竖向钉底端。
        // 三边等距：起始边的内边距取「交叉方向居中所剩空隙」的一半，与另两边严格相等。
        // 写死 dp 做不到 —— 格子尺寸随屏宽/列数变，居中余量是算出来的不是定的。
        val iconSize = (minOf(maxWidth, maxHeight) - 10.dp).coerceAtLeast(18.dp)
        val iconDraw = iconSize * 0.6f
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onCard.copy(alpha = if (iconDimmed) 0.5f else 1f),
            modifier = Modifier
                .then(
                    if (vertical) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = ((maxWidth - iconDraw) / 2).coerceAtLeast(0.dp))
                    } else {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = ((maxHeight - iconDraw) / 2).coerceAtLeast(0.dp))
                    },
                )
                .size(iconDraw),
        )
    }
}

/**
 * 音量条：固定媒体流（STREAM_MUSIC）。QS 风格粗滑条，图标只作状态指示。
 * 拖动实时 setStreamVolume（不带 FLAG_SHOW_UI）；面板可见期间监听 VOLUME_CHANGED_ACTION 跟外部变化。
 */
@Composable
fun VolumeSliderCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 1,
    enabled: Boolean = true,
) {
    val vertical = rememberVerticalSpan(spanX, spanY)
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var level by remember { mutableStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    // 拖动中的值不接收系统回显覆盖：VOLUME_CHANGED 会在每次 set 后回来，
    // 拖动中让回显赢过手指 = 滑条往回跳。用局部状态隔离。
    var draggingValue by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    draggingValue ?: run { level = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun applyVolume(value: Int) {
        val clamped = value.coerceIn(0, max)
        if (clamped != (audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1)) {
            runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0) }
        }
        level = clamped
    }

    val muted = level == 0
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(sliderPadding(vertical)),
        contentAlignment = Alignment.Center,
    ) {
        QsSlider(
            value = draggingValue ?: (level / max.toFloat()),
            onValueChange = { fraction ->
                draggingValue = fraction
                applyVolume((fraction * max).toInt())
            },
            onValueChangeFinished = {
                draggingValue?.let { applyVolume((it * max).toInt()) }
                draggingValue = null
            },
            icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            iconDimmed = muted,
            vertical = vertical,
            enabled = enabled,
            // 撑满两个轴：只给 fillMaxHeight 时宽度没人管，竖向轨道会缩成一条。
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** ON_RESUME 递增的版本号：权限类状态（WRITE_SETTINGS / 通知访问）在用户从设置页
 *  返回后必须重查，而 remember 不会自己失效。 */
@Composable
internal fun rememberResumeTick(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return tick
}

/**
 * 亮度滑条：SCREEN_BRIGHTNESS（0..255），拖动中若自动亮度先切手动。
 * 需 WRITE_SETTINGS；部分 ROM 只认 float 档，两处都写、失败静默。
 * 方向随占位走，未授权时「点此授权」在竖向窄格改竖排。
 */
@Composable
fun BrightnessSliderCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 1,
    enabled: Boolean = true,
) {
    val vertical = rememberVerticalSpan(spanX, spanY)
    val context = LocalContext.current
    // 授权页返回后要重查（ON_RESUME 递增 tick）：一次性 remember 会让格子永远卡在「点此授权」。
    val canWrite = remember(rememberResumeTick()) { Settings.System.canWrite(context) }
    if (!canWrite) {
        GrantHintCell(
            icon = Icons.Default.Brightness6,
            text = stringResource(R.string.grid_panel_brightness_need_permission),
            vertical = vertical,
            enabled = enabled,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            modifier = modifier,
        )
        return
    }
    val resolver = remember { context.contentResolver }
    var value by remember {
        mutableStateOf(
            runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }
                .getOrDefault(128),
        )
    }
    val onCard = gridPanelOnCardColor
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(sliderPadding(vertical)),
        contentAlignment = Alignment.Center,
    ) {
        QsSlider(
            value = (value.coerceIn(0, 255)) / 255f,
            onValueChange = { fraction ->
                val v = (fraction * 255).toInt().coerceIn(0, 255)
                // 不动 SCREEN_BRIGHTNESS_MODE：自动亮度的开关交给用户/系统，拖动只写值
                //（自动模式下系统可能覆盖，属预期）。
                runCatching {
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, v)
                    // 部分 ROM（MIUI 等）只认 float 档，双写，失败忽略。
                    runCatching {
                        Settings.System.putFloat(resolver, "screen_brightness_float", v / 255f)
                    }
                }
                value = v
            },
            onValueChangeFinished = {},
            icon = Icons.Default.Brightness6,
            iconDimmed = false,
            vertical = vertical,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
