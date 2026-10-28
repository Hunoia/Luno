package hunoia.luno.gridpanel.internal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.runtime.automation.AutomationNotificationListener

private const val TAG = "InternalMedia"

/** 一个活跃媒体会话的渲染快照。 */
private class SessionUiState {
    var callbackRef: android.media.session.MediaController.Callback? = null
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artwork by mutableStateOf<Any?>(null)
    var playing by mutableStateOf(false)
}

/**
 * 媒体控制：默认 4×2，格数 = (spanX/2) × (spanY/2)，每会话一块 2×2。
 * 铺满 vs 网格：奇数 span 凑不出整块 → 铺满；单会话多格空间 → 铺满；其余走网格。
 * 超出容量只渲染前 N 个（播放中优先、本地优先），余量以「+N」角标提示。
 * 数据源 MediaSessionManager 需通知访问权限；无权限整格变「点此授权」，
 * 面板可见期间 registerCallback 实时回显、离组注销。
 */
@Composable
fun MediaControlCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 2,
    /** 编辑态=false：封面的点击与三键全禁，事件让给层根的拖拽/缩放。 */
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var granted by remember(rememberResumeTick()) {
        mutableStateOf(AutomationNotificationListener.isAccessGranted(context))
    }
    var sessions by remember { mutableStateOf<List<Pair<android.media.session.MediaController, SessionUiState>>>(emptyList()) }

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
        }

        fun applyPlayback(state: SessionUiState, pb: PlaybackState?) {
            state.playing = pb?.state == PlaybackState.STATE_PLAYING
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
            // 会话消失时先注销 callback：entries 被 retainAll 清掉后，onDispose 再也找不到它，
            // 每换一次播放器就会泄漏一份 callback + SessionUiState。
            entries.filterKeys { it !in alive }.values.forEach { (controller, state) ->
                state.callbackRef?.let { controller.unregisterCallback(it) }
            }
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
    val vertical = rememberVerticalSpan(spanX, spanY)

    if (!granted) {
        GrantHintCell(
            icon = Icons.Default.MusicNote,
            text = stringResource(R.string.grid_panel_media_need_permission),
            vertical = vertical,
            enabled = enabled,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            modifier = modifier,
        )
        return
    }

    if (sessions.isEmpty()) {
        EmptyHintCell(
            text = stringResource(R.string.grid_panel_media_no_session),
            vertical = vertical,
            modifier = modifier,
        )
        return
    }

    // 每会话占 2×2 → 格数 = (spanX/2)×(spanY/2)。
    val cols = (spanX / 2).coerceAtLeast(1)
    val gridRows = (spanY / 2).coerceAtLeast(1)
    val capacity = cols * gridRows
    val oddSpan = spanX % 2 != 0 || spanY % 2 != 0
    val fill = oddSpan || (sessions.size == 1 && capacity >= 2)

    if (fill) {
        SingleSessionCell(sessions[0], vertical = vertical, enabled = enabled, modifier = modifier)
        return
    }

    // 多会话网格；超出会话数的部分不渲染。
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until gridRows) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    for (col in 0 until cols) {
                        val index = row * cols + col
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            // 空槽不画东西：容量按 span 算，用户把部件拖大后未必
                            // 有那么多会话，留白比摆个占位图标干净。
                            sessions.getOrNull(index)?.let { (controller, state) ->
                                SessionBlock(controller, state, enabled)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 正方封面边长：[longSide] 为主依据（不低于 [min]），[limit] 是另一边越不得的上限。 */
private fun artworkSide(longSide: Dp, limit: Dp, min: Dp = 48.dp): Dp =
    longSide.coerceAtLeast(min).coerceAtMost(limit)

/**
 * 媒体部件排版参数，全部由内区尺寸推出（2×2 自动降级、4×2 自动放大）。
 * @property keySize 三键边长；keysVertical 一格放不下时改竖排；artSize 封面/缩略图边长；
 * artOnTop 封面置顶；titleLines 标题行数上限；artistFits 歌手是否可能与标题共存。
 */
private data class MediaMetrics(
    val keySize: Dp,
    val keysVertical: Boolean,
    val artSize: Dp,
    val artOnTop: Boolean,
    val titleLines: Int,
    val artistFits: Boolean,
)

/** 三键尺寸区间：小于下限点不准，大于上限抢戏。 */
private val KeySizeMin = 24.dp
private val KeySizeMax = 44.dp
/** 单行文字高：预留两行标题，标题占两行时歌手让位，键尺寸不被文字挤小。 */
private val LineTitle = 24.dp
private val LineArtist = 16.dp

/** 竖排时封面置顶的下限：比这更小就退回封面在左（放不下反而更乱）。 */
private val ArtStackMin = 40.dp

/** 2×2 块里的小缩略图：与三键同尺寸，对齐成一条线。 */
private val ThumbSize = 30.dp

/** 文字列下限：封面在左时，右半至少要能放一个歌名。 */
private val TextMinWidth = 56.dp

private val MediaPadH = 10.dp
private val MediaPadV = 8.dp
private val MediaGap = 6.dp
/** 三键容器固定高度：Column 按整列高测量非 weight 子件，键尺寸须自己钉死，文字侧用 weight 吸收压力。 */
private fun keysHeight(m: MediaMetrics): Dp =
    if (m.keysVertical) m.keySize * 3 + 4.dp else m.keySize

/**
 * 按内区尺寸算媒体排版参数。
 *
 * @param thumbOnly 2×2 会话块走这个模式：封面缩成 [ThumbSize] 小方块，
 *   空间让给歌名与三键；铺满布局走大封面。
 */
private fun mediaMetrics(
    width: Dp,
    height: Dp,
    vertical: Boolean,
    thumbOnly: Boolean,
): MediaMetrics {
    // 一格宽（内区约 32dp）横排三键要 10.8dp，点不准 —— 改竖排。
    val keysVertical = (width / 3) < KeySizeMin
    var keySize = minOf(width / 3, height * 0.42f).coerceIn(KeySizeMin, KeySizeMax)

    val textMin = maxOf(TextMinWidth, keySize * 3)
    val artOnTop = vertical && !thumbOnly &&
        minOf(width, height - (keySize + LineTitle * 2) - MediaGap) >= ArtStackMin

    val artSize = when {
        thumbOnly -> minOf(ThumbSize, height - keySize - MediaGap * 2).coerceAtLeast(0.dp)
        artOnTop -> artworkSide(width, height - (keySize + LineTitle * 2) - MediaGap)
        else -> artworkSide(height, width - textMin - MediaGap)
    }

    if (keysVertical) {
        keySize = KeySizeMin
    } else {
        // 键要落在**文字列**里，不能按整宽算：4×2 铺满时文字列只有 110dp，
        // 按整宽 213dp 算出来的键会溢出文字列。
        val keyBase = if (thumbOnly || artOnTop) width else width - artSize - MediaGap
        keySize = if (thumbOnly) {
            // 2×2 块的键在 weight 行**之下**，拿得到整列高，不用给文字让位。
            minOf(keyBase / 3, KeySizeMax)
        } else {
            // 键与文字共用列高：先扣两行标题 + 缝。不扣的话标题一长，键会被顶到下沿外被圆角裁掉。
            // 只扣标题、不扣歌手：歌手让位给标题，键能拿回尺寸（4×2 下 26.6 → 36.9dp）。
            val metaH = if (artOnTop) height - artSize - MediaGap else height
            minOf(keyBase / 3, metaH - LineTitle * 2 - MediaGap)
                .coerceIn(KeySizeMin, KeySizeMax)
        }
    }

    val metaH = when {
        thumbOnly -> height
        artOnTop -> height - artSize - MediaGap
        else -> height
    }
    val textBudget = metaH - MediaGap - keySize
    return MediaMetrics(
        keySize = keySize,
        keysVertical = keysVertical,
        artSize = artSize,
        artOnTop = artOnTop,
        titleLines = if (textBudget >= LineTitle * 2) 2 else 1,
        artistFits = textBudget >= LineTitle + LineArtist,
    )
}

/** 封面框：底衬 + 圆角 +（可选）点击拉起 app。封面缺失时退化成音符占位。 */
@Composable
private fun SessionArtworkBox(
    bitmap: Any?,
    modifier: Modifier = Modifier,
    cornerScale: Float = 0.5f,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ItemCorner * cornerScale))
            // 封面缺失时的占位底衬：0.09 淡到几乎看不见，音符图标形同悬空；
            // 提到 0.18 才是一块「托得住」的底板。
            .background(gridPanelOnCardColor.copy(alpha = 0.18f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            AsyncImage(
                model = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                imageLoader = LocalContext.current.imageLoader,
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = gridPanelOnCardColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 上/下一曲 + 播放/暂停（无进度条）。三键同尺寸，尺寸由 [MediaMetrics] 给。 */
@Composable
private fun TransportKeys(
    controller: android.media.session.MediaController,
    playing: Boolean,
    enabled: Boolean,
    metrics: MediaMetrics,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    val transport = controller.transportControls
    val icon = @Composable { key: String ->
        Icon(
            imageVector = when (key) {
                "prev" -> Icons.Default.SkipPrevious
                "next" -> Icons.Default.SkipNext
                else -> if (playing) Icons.Default.Pause else Icons.Default.PlayArrow
            },
            // icon-only 按钮必须带可访问名（ui-guidelines §8/§10）。
            contentDescription = when (key) {
                "prev" -> stringResource(R.string.grid_panel_media_previous)
                "next" -> stringResource(R.string.grid_panel_media_next)
                else -> stringResource(R.string.grid_panel_media_play_pause)
            },
            tint = onCard,
            modifier = Modifier.size(metrics.keySize * 0.66f),
        )
    }
    val keys: @Composable () -> Unit = {
        IconButton(
            onClick = { transport.skipToPrevious() },
            enabled = enabled,
            modifier = Modifier.size(metrics.keySize),
            content = { icon("prev") },
        )
        IconButton(
            onClick = { if (playing) transport.pause() else transport.play() },
            enabled = enabled,
            modifier = Modifier.size(metrics.keySize),
            content = { icon("play") },
        )
        IconButton(
            onClick = { transport.skipToNext() },
            enabled = enabled,
            modifier = Modifier.size(metrics.keySize),
            content = { icon("next") },
        )
    }
    if (metrics.keysVertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { keys() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        ) { keys() }
    }
}

/** 歌名 + 歌手，**合计不超过两行**：标题占两行时歌手自动让位（实测行数判定）。 */
@Composable
private fun SessionTexts(
    controller: android.media.session.MediaController,
    state: SessionUiState,
    vertical: Boolean,
    metrics: MediaMetrics,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    val align = if (vertical) TextAlign.Center else TextAlign.Start
    // onTextLayout 量出标题真实行数：短标题才留歌手，长标题把那一行让给标题。
    var titleLines by remember(state.title, metrics.titleLines) { mutableIntStateOf(1) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = if (vertical) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = state.title.ifBlank { controller.packageName },
            style = MaterialTheme.typography.bodyMedium,
            color = onCard,
            maxLines = metrics.titleLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            onTextLayout = { titleLines = it.lineCount },
        )
        if (state.artist.isNotBlank() && metrics.artistFits && titleLines == 1) {
            Text(
                text = state.artist,
                style = MaterialTheme.typography.labelSmall,
                color = onCard.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
            )
        }
    }
}
/** 2×2 会话块：小缩略图 + 歌名/歌手 + 三键。封面压到 [ThumbSize]，避免挤掉歌名。 */
@Composable
private fun SessionBlock(
    controller: android.media.session.MediaController,
    state: SessionUiState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val onArtwork = if (enabled) {
        { if (launchPackage(context, controller.packageName)) closePanel() }
    } else {
        null
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = MediaPadH, vertical = MediaPadV),
    ) {
        val m = mediaMetrics(maxWidth, maxHeight, vertical = false, thumbOnly = true)
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MediaGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MediaGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionArtworkBox(
                    bitmap = state.artwork,
                    modifier = Modifier.size(m.artSize),
                    onClick = onArtwork,
                )
                SessionTexts(
                    controller = controller,
                    state = state,
                    vertical = false,
                    metrics = m,
                    modifier = Modifier.weight(1f),
                )
            }
            TransportKeys(
                controller = controller,
                playing = state.playing,
                enabled = enabled,
                metrics = m,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keysHeight(m)),
            )
        }
    }
}

/**
 * 铺满布局：单会话时占满整个部件。封面是大方块（比 2×2 块的小缩略图大得多），
 * 歌名/歌手 + 三键在另一半 —— 竖排时封面置顶。
 */
@Composable
private fun SingleSessionCell(
    session: Pair<android.media.session.MediaController, SessionUiState>,
    vertical: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val (controller, state) = session
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val onArtwork = if (enabled) {
        { if (launchPackage(context, controller.packageName)) closePanel() }
    } else {
        null
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .padding(horizontal = MediaPadH, vertical = MediaPadV),
    ) {
        val m = mediaMetrics(maxWidth, maxHeight, vertical, thumbOnly = false)
        val texts: @Composable (Modifier) -> Unit = { m2 ->
            Column(
                modifier = m2,
                verticalArrangement = Arrangement.spacedBy(MediaGap),
            ) {
                // weight(1f) 是关键：Column 先测非 weight 子件（键拿满），
                // 再把剩下的分给这里 —— 标题再长也顶不动键，文字自己截断省略。
                SessionTexts(
                    controller = controller,
                    state = state,
                    vertical = m.artOnTop,
                    metrics = m,
                    modifier = Modifier.weight(1f),
                )
                TransportKeys(
                    controller = controller,
                    playing = state.playing,
                    enabled = enabled,
                    metrics = m,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (m.artOnTop) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MediaGap),
            ) {
                SessionArtworkBox(
                    bitmap = state.artwork,
                    cornerScale = 0.6f,
                    modifier = Modifier.size(m.artSize),
                    onClick = onArtwork,
                )
                texts(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(MediaGap),
            ) {
                SessionArtworkBox(
                    bitmap = state.artwork,
                    cornerScale = 0.6f,
                    modifier = Modifier.size(m.artSize),
                    onClick = onArtwork,
                )
                texts(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
/**
 * 「点此授权」提示态。竖向格只有一格宽，横排放不下文案 —— 按部件方向切 Column。
 * 媒体（通知访问）与亮度（WRITE_SETTINGS）共用。
 */
@Composable
internal fun GrantHintCell(
    icon: ImageVector,
    text: String,
    vertical: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
        )
    }
    val badge: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = onCard.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
    }
    val base = modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(ItemCorner))
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    if (vertical) {
        Column(
            modifier = base.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            badge()
            label()
        }
    } else {
        Row(
            modifier = base.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            badge()
            label()
        }
    }
}

/** 有权限但无活跃会话的灰态提示；方向处理同 [GrantHintCell]。 */
@Composable
private fun EmptyHintCell(
    text: String,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val onCard = gridPanelOnCardColor
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(0.45f)
            .padding(horizontal = if (vertical) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val label: @Composable () -> Unit = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
            )
        }
        val badge: @Composable () -> Unit = {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = onCard,
                modifier = Modifier.size(22.dp),
            )
        }
        if (vertical) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                badge()
                label()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                badge()
                label()
            }
        }
    }
}

private fun launchPackage(context: Context, packageName: String): Boolean {
    if (packageName.isBlank()) return false
    return runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
            true
        } ?: false
    }.getOrDefault(false)
}
