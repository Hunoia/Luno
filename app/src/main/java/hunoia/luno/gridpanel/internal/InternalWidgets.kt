package hunoia.luno.gridpanel.internal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.runtime.automation.AutomationNotificationListener

/**
 * 内置小部件（[hunoia.luno.config.model.GridPanelItem.Kind.Internal]）的定义与内容。
 *
 * 全部 Compose 自绘：不走 AppWidgetHost，就不付跨进程、常驻、授权三件套的成本
 * （页眉时钟不进网格模型的同款理由）。手势不在这里挂整格级的东西 —— 编辑态的
 * 拖拽/缩放由层根 gridLayerGestures 接管，这里只有自己需要的点击（按钮/滑条）。
 */

private const val TAG = "InternalMedia"

/** 内置小部件的持久化 id（存进 [hunoia.luno.config.model.GridPanelItem.Kind.Internal.widgetId]）。 */
object InternalWidgetIds {
    const val MEDIA = "internal.media"
    const val VOLUME = "internal.volume"
    const val BRIGHTNESS = "internal.brightness"
    const val RECENTS = "internal.recents"
}

/** 一个内置小部件的选择页条目与落格默认值。 */
data class InternalWidgetDef(
    val id: String,
    val labelRes: Int,
    val spanX: Int,
    val spanY: Int,
)

/** 选择页「Luno 内置」分区的清单；第一版只有媒体控制和音量条。 */
val InternalWidgetDefs = listOf(
    InternalWidgetDef(InternalWidgetIds.MEDIA, R.string.grid_panel_internal_media, 4, 2),
    InternalWidgetDef(InternalWidgetIds.VOLUME, R.string.grid_panel_internal_volume, 4, 1),
    InternalWidgetDef(InternalWidgetIds.BRIGHTNESS, R.string.grid_panel_internal_brightness, 4, 1),
    InternalWidgetDef(InternalWidgetIds.RECENTS, R.string.grid_panel_internal_recents, 4, 1),
)

fun internalWidgetDefOf(widgetId: String): InternalWidgetDef? =
    InternalWidgetDefs.firstOrNull { it.id == widgetId }

/** 一个活跃媒体会话的渲染快照。 */
private class SessionUiState {
    var callbackRef: android.media.session.MediaController.Callback? = null
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artwork by mutableStateOf<Any?>(null)
    var playing by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
}

/**
 * 媒体控制：每个活跃媒体会话一行（封面 + 歌名/歌手 + 可拖动进度条 + 三键）。
 *
 * - 数据源 [MediaSessionManager]：要通知访问权限（`AutomationNotificationListener`
 *   已是常驻 listener，这里是复用而不是新增授权面）。
 * - 没权限：整格变成「点此授权」一行，跳系统通知访问设置页。
 * - 有权限但没活跃会话：灰态图标 + 提示。
 * - **多会话多行**：行数随会话数变，通过 [onDesiredRows] 请求把项的 spanY 撑到
 *   `max(2, 会话数)`（落格模型是静态的，只能落盘改 span）。
 * - 面板开着期间 `registerCallback` 实时回显，离组注销；播放中每秒推位置。
 */
@Composable
fun MediaControlCell(
    modifier: Modifier = Modifier,
    onDesiredRows: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AutomationNotificationListener.isAccessGranted(context)) }
    var sessions by remember { mutableStateOf<List<Pair<android.media.session.MediaController, SessionUiState>>>(emptyList()) }

    // 项高度跟会话数走：每会话两行（上排封面+标题+三键，下排进度），空态保底 2 行。
    androidx.compose.runtime.LaunchedEffect(sessions.size) {
        onDesiredRows(maxOf(2, sessions.size * 2))
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listenerComponent = ComponentName(context, AutomationNotificationListener::class.java)

        val entries = LinkedHashMap<String, Pair<android.media.session.MediaController, SessionUiState>>()

        fun applyMetadata(state: SessionUiState, metadata: MediaMetadata?) {
            state.title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString().orEmpty()
            state.artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString().orEmpty()
            state.artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            state.durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        }

        fun applyPlayback(state: SessionUiState, pb: PlaybackState?) {
            state.playing = pb?.state == PlaybackState.STATE_PLAYING
            state.positionMs = pb?.getPosition() ?: 0L
        }

        fun rebind() {
            // 本地播放优先、正在播放优先：蓝牙/投屏会话常驻但不播，别抢前排。
            val picked = runCatching {
                Log.i(TAG, "getActiveSessions, granted=$granted")
                manager.getActiveSessions(listenerComponent)
            }.onFailure { Log.w(TAG, "getActiveSessions failed", it) }
                .getOrNull().orEmpty()
                // 只保留「真的在播/播过」的会话：很多 app（阅读器、浏览器）会常驻
                // 一个空 MediaSession —— STATE_NONE、无播放动作、位置 0，全都不算。
                .filter { c ->
                    val pb = c.playbackState
                    pb != null &&
                        pb.state != PlaybackState.STATE_NONE &&
                        pb.actions.and(PlaybackState.ACTION_PLAY) != 0L &&
                        (pb.state == PlaybackState.STATE_PLAYING ||
                            pb.state == PlaybackState.STATE_PAUSED ||
                            pb.state == PlaybackState.STATE_BUFFERING ||
                            pb.position > 0L)
                }
                .sortedWith(
                    compareByDescending<android.media.session.MediaController> {
                        it.playbackState?.state == PlaybackState.STATE_PLAYING
                    }
                )
            Log.i(TAG, "sessions=${picked.size} pkgs=${picked.map { it.packageName }}")
            // media.session.MediaController 不暴露 sessionId（那是 MediaSession 的）。
            // 用包名 + 实例身份做 key：同一进程内稳定，重绑后旧 key 自然被 retainAll 清掉。
            fun keyOf(c: android.media.session.MediaController) =
                "${c.packageName}@${System.identityHashCode(c)}"
            val alive = picked.map { keyOf(it) }.toHashSet()
            entries.keys.retainAll(alive)
            for (controller in picked) {
                val key = keyOf(controller)
                val existing = entries[key]
                if (existing != null) continue
                val state = SessionUiState()
                val callback = object : android.media.session.MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) =
                        applyMetadata(state, metadata)
                    override fun onPlaybackStateChanged(pb: PlaybackState?) =
                        applyPlayback(state, pb)
                }
                state.callbackRef = callback
                controller.registerCallback(callback)
                applyMetadata(state, controller.metadata)
                applyPlayback(state, controller.playbackState)
                entries[key] = controller to state
            }
            sessions = entries.values.toList()
        }

        rebind()
        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { _ -> rebind() }
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
        }.onFailure { Log.w(TAG, "addOnActiveSessionsChangedListener failed", it) }
        onDispose {
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
            for ((controller, state) in entries.values) {
                state.callbackRef?.let { controller.unregisterCallback(it) }
            }
            entries.clear()
        }
    }

    val onCard = gridPanelOnCardColor
    if (!granted) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(ItemCorner))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = onCard.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.grid_panel_media_need_permission),
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    if (sessions.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .alpha(0.45f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = onCard,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.grid_panel_media_no_session),
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((controller, state) in sessions) {
            MediaSessionRow(
                context = context,
                controller = controller,
                state = state,
                onCard = onCard,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 单会话块（占两行）：
 * ```
 * [      ] 标题        ⏮ ▶ ⏭
 * [ 封面 ] 歌手
 * [      ] 1:23 ───●─── 3:45
 * ```
 * 封面跨两行（点它拉起 app）；进度条独占底排，两侧时间数字。
 */

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun MediaSessionRow(
    context: Context,
    controller: android.media.session.MediaController,
    state: SessionUiState,
    onCard: Color,
    modifier: Modifier = Modifier,
) {
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    // 播放中每秒推位置（position = state.position + elapsed × speed）。面板关闭
    // 随组合销毁，不留常驻 ticker。
    androidx.compose.runtime.LaunchedEffect(state.playing, state.positionMs, state.durationMs) {
        if (state.playing && state.durationMs > 0) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val pb = controller.playbackState ?: break
                state.positionMs = pb.position.coerceIn(0, state.durationMs)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // 上排：封面（撑满上排高）+ 标题/歌手
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .clip(RoundedCornerShape(ItemCorner * 0.5f))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { launchPackage(context, controller.packageName) },
                contentAlignment = Alignment.Center,
            ) {
                if (state.artwork != null) {
                    AsyncImage(
                        model = state.artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        imageLoader = context.imageLoader,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = onCard,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.title.ifBlank { controller.packageName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.artist.isNotBlank()) {
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = onCard.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val transport0 = controller.transportControls
            IconButton(
                onClick = { if (state.playing) transport0.pause() else transport0.play() },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = onCard,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        // 下排：三键横排 + 进度条，横跨整个项宽（含封面下方）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val transport = controller.transportControls
            if (state.durationMs > 0) {
                val fraction = draggingValue
                    ?: (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                Slider(
                    value = fraction,
                    onValueChange = { draggingValue = it },
                    onValueChangeFinished = {
                        draggingValue?.let { f ->
                            runCatching {
                                controller.transportControls.seekTo((f * state.durationMs).toLong())
                            }
                        }
                        draggingValue = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                )
            }
            IconButton(onClick = { transport.skipToPrevious() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SkipPrevious, null, tint = onCard, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { transport.skipToNext() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SkipNext, null, tint = onCard, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun launchPackage(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }
}

/**
 * 音量条：固定媒体流（STREAM_MUSIC）。横条 + 左侧图标 + 右侧百分比。
 *
 * - 拖动实时 `setStreamVolume`（不带 FLAG_SHOW_UI：面板里再弹系统音量气泡是双重反馈）。
 * - 面板可见期间监听 VOLUME_CHANGED_ACTION：按键/蓝牙改了音量，滑条要跟。
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun VolumeSliderCell(modifier: Modifier = Modifier) {
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

    // 静音切换：记一个恢复档（静音前的音量）。VOLUME_CHANGED 也会刷新它，
    // 避免「外部调到 3 → 静音 → 取消静音变回满格」。
    var lastNonZero by remember { mutableStateOf(15) }
    val current = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    if (current > 0) lastNonZero = current
    val muted = current == 0

    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val onCard = gridPanelOnCardColor
        Icon(
            imageVector = if (muted) {
                Icons.AutoMirrored.Filled.VolumeOff
            } else {
                Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = null,
            tint = onCard.copy(alpha = if (muted) 0.5f else 1f),
            modifier = Modifier
                .size(22.dp)
                .clickable {
                    audio?.let { am ->
                        if (muted) {
                            runCatching {
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, lastNonZero.coerceIn(1, max), 0)
                            }
                        } else {
                            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
                        }
                    }
                },
        )
        Slider(
            value = draggingValue ?: (level / max.toFloat()),
            onValueChange = { fraction ->
                draggingValue = fraction
                applyVolume((fraction * max).toInt())
            },
            onValueChangeFinished = {
                draggingValue?.let { applyVolume((it * max).toInt()) }
                draggingValue = null
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        )
        Text(
            text = "${(draggingValue?.let { (it * max).toInt() } ?: level) * 100 / max}%",
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.7f),
        )
    }
}


/**
 * 亮度滑条：`Settings.System.SCREEN_BRIGHTNESS`（0..255），拖动中若处于自动亮度
 * 先切手动。需要 WRITE_SETTINGS（AppOps）：没授权整格变「点此授权」。
 * 部分 ROM 只认 float 档，两处都写，失败静默。
 */
@Composable
fun BrightnessSliderCell(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val canWrite = remember { Settings.System.canWrite(context) }
    if (!canWrite) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(ItemCorner))
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                                .setData(android.net.Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Brightness6,
                contentDescription = null,
                tint = gridPanelOnCardColor.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.grid_panel_brightness_need_permission),
                style = MaterialTheme.typography.bodySmall,
                color = gridPanelOnCardColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Brightness6,
            contentDescription = null,
            tint = onCard,
            modifier = Modifier.size(22.dp),
        )
        Slider(
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
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        )
        Text(
            text = "${(value * 100 / 255).coerceIn(0, 100)}%",
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.7f),
        )
    }
}

/**
 * 最近应用：一排图标（旧 → 新，最多 6 个，不含当前前台），点按直达。
 * 数据来自 RecentAppsHub（a11y TYPE_WINDOW_STATE_CHANGED 维护，无额外权限）。
 */
@Composable
fun RecentAppsCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 1,
) {
    val context = LocalContext.current
    val history by hunoia.luno.runtime.environment.RecentAppsHub.history.collectAsState()
    // 容量 = 格数；一行一格，多行往下排。
    val capacity = (spanX * spanY).coerceAtLeast(1)
    val pkgs = remember(history, capacity) { history.dropLast(1).takeLast(capacity) }
    val onCard = gridPanelOnCardColor
    if (pkgs.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .alpha(0.45f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.grid_panel_recents_empty),
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        for (row in 0 until spanY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (col in 0 until spanX) {
                    val index = row * spanX + col
                    if (index >= pkgs.size) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val pkg = pkgs[index]
                    val icon = remember(pkg) {
                        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(ItemCorner * 0.5f))
                            .clickable {
                                runCatching {
                                    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(it)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            AsyncImage(
                                model = icon,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                imageLoader = context.imageLoader,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = onCard,
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

