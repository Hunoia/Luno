package hunoia.luno.gridpanel.internal

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrightnessAuto
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
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
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
    /** 起始图标的点击（静音 / 自动亮度切换）；null = 图标只作指示。 */
    onIconClick: (() -> Unit)? = null,
    iconContentDescription: String? = null,
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
            .clip(CircleShape)
            // 轨道底衬半透明：全透明的话 0% 时整条轨道消失，只剩填充
            // 一截悬在卡面上；半透既托住滑条形状，又不抢填充的 primary。
            // 不再画描边：网格项已有与动作项同色的填充底，双层底/双线会显得脏。
            .background(onCard.copy(alpha = 0.12f))
            .then(
                // enabled=false（编辑态）时不挂手势：事件落到层根的拖拽/缩放上，
                // 不然编辑态拖滑条 = 既改音量又拖部件。
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 吃掉 down：面板根的「点空白关窗 / 长按空白进编辑」判定域靠消费链
                            // （见 GridPanelContent 根 Box 的注释），不 consume 的话长按滑条会误进编辑态。
                            // 跨轴滚动不受影响：scrollable / pager 起手用 requireUnconsumed=false 看
                            // 后续移动，而这里只在主轴过 slop 后才消费 move（见下面 dragging 分支）。
                            down.consume()
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
            contentDescription = iconContentDescription,
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
                .size(iconDraw)
                // 图标点击自己吃掉 down：轨道拖动手势（同节点的 pointerInput）看到已消费的
                // down 不启动拖动；点按归图标、拖动归轨道（拖动时图标的 tap 被移动消费取消）。
                .then(
                    if (onIconClick != null) {
                        Modifier.pointerInput(onIconClick) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                if (waitForUpOrCancellation() != null) onIconClick()
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
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
    // 上次非零音量：静音后恢复用；静音时记下当前值，取消静音回到这里。
    var lastVolume by remember {
        mutableIntStateOf(audio?.getStreamVolume(AudioManager.STREAM_MUSIC)?.takeIf { it > 0 } ?: 0)
    }
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
        if (clamped > 0) lastVolume = clamped
    }

    val muted = level == 0
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(sliderPadding()),
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
            // 静音 / 取消静音：点击图标切换，静音时恢复上次非零音量。
            onIconClick = {
                applyVolume(if (muted) lastVolume.coerceAtLeast(1) else 0)
            },
            iconContentDescription = stringResource(
                if (muted) R.string.grid_panel_volume_unmute else R.string.grid_panel_volume_mute,
            ),
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
 * 亮度滑条：SCREEN_BRIGHTNESS（0..255），按 γ≈2.2 在滑条位置与设置值之间换算。
 * 需 WRITE_SETTINGS；只写标准 int 键（双写 float 键会与部分 ROM 的标度打架）。
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
    // SCREEN_BRIGHTNESS int 是伽马空间（AOSP 亮度滑条位置 ↔ 设置值之间过 γ≈2.2 曲线，
    // 见 AOSP BrightnessUtils）：直写线性分数会被系统解码成偏亮的中段（拖 1/5 ≈ 一半亮度）。
    // 滑条位置（线性）↔ 设置值：v = 255 * p^γ，p = (v/255)^(1/γ)。
    val brightnessGamma = 2.2f
    val systemValue = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }
        .getOrDefault(128)
    var value by remember {
        mutableStateOf(systemValue)
    }
    // 自动亮度开关（SCREEN_BRIGHTNESS_MODE）：图标点击切换，自动时图标换 BrightnessAuto。
    var autoBrightness by remember {
        mutableStateOf(
            runCatching {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            }.getOrDefault(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
    }
    val sliderFraction = (value.coerceIn(0, 255) / 255f).pow(1f / brightnessGamma)
    val onCard = gridPanelOnCardColor
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(sliderPadding()),
        contentAlignment = Alignment.Center,
    ) {
        QsSlider(
            value = sliderFraction,
            onValueChange = { fraction ->
                val v = (255f * fraction.pow(brightnessGamma)).roundToInt().coerceIn(0, 255)
                // 只写标准 int 键：双写 screen_brightness_float 会打架 —— 部分 ROM 采用
                // float 键且标度与 0..1 线性不一致。
                // （自动模式下系统可能覆盖，属预期。）
                runCatching {
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, v)
                }
                value = v
            },
            onValueChangeFinished = {},
            icon = if (autoBrightness) Icons.Default.BrightnessAuto else Icons.Default.Brightness6,
            iconDimmed = autoBrightness,
            vertical = vertical,
            enabled = enabled,
            // 手动 ↔ 自动亮度切换：自动时图标显示 BrightnessAuto 并 dim。
            onIconClick = {
                val newMode = if (autoBrightness) {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                } else {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                }
                runCatching {
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, newMode)
                }
                autoBrightness = !autoBrightness
            },
            iconContentDescription = stringResource(
                if (autoBrightness) {
                    R.string.grid_panel_brightness_manual
                } else {
                    R.string.grid_panel_brightness_auto
                },
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
