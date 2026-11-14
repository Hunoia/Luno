package hunoia.luno.floatball.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.floatball.R
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮球设置页：只负责主球自身外观与单击动作。
 *
 * 通知胶囊与建议胶囊各自独立成页，从动作设置的「功能」组并列进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallEditScreen(
    onBack: () -> Unit,
    /** 去动作选择页配「单击」动作（导航意图由 :app 负责）。 */
    onEditTapAction: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val floatBall = actionSettings.floatBall

    // 滑杆拖动只改本地值，松手才落盘：每帧写 DataStore 会跨进程触发 :service 重组。
    var localSizeDp by remember(floatBall.sizeDp) { mutableFloatStateOf(floatBall.sizeDp) }
    var localIdleDelay by remember(floatBall.idleDimDelayMs) {
        mutableFloatStateOf(floatBall.idleDimDelayMs.toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    var showColorPicker by remember { mutableStateOf(false) }
    // 自定义图标：选中待裁剪的 URI（非空 = 显示裁剪预览）；裁剪确认后复制到 filesDir。
    var pendingIconUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        // GetContent 只在当前任务存活期间授权读取，确认后立即复制到 filesDir（见 onIconConfirm）。
        if (uri != null) pendingIconUri = uri
    }

    // 从系统悬浮窗设置回来时重查（授权与撤销都要）：横幅消隐要跟手，服务也要 nudge ——
    // 授权回来服务要从无权限的隐藏态亮回；撤销时系统已拆走悬浮窗，nudge 让服务摘窗回收，
    // 否则 ballVisible 停在 true，之后重新授权也亮不回来。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.canDrawOverlays()
                if (granted != overlayGranted) {
                    overlayGranted = granted
                    facades.system.refreshFloatBall(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun update(transform: (FloatBallSettings) -> FloatBallSettings) {
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(floatBall = transform(current.floatBall))
            }
        }
    }

    /**
     * 复制所选图标到 filesDir/floatball_icon/，返回绝对路径；失败返回 null。
     *
     * 文件名带时间戳保证唯一：`CustomBallIcon` 以 `File` 为 Coil model，
     * `File.equals` 只看路径 —— 固定名（icon.gif）换同类图时路径不变，
     * Coil 判定 model 未变就不重载，球面卡在旧图。
     */
    fun copyIconToFiles(uri: android.net.Uri): String? {
        val dir = File(context.filesDir, CUSTOM_ICON_DIR).apply { mkdirs() }
        val ext = when (context.contentResolver.getType(uri)) {
            "image/gif" -> "gif"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> "img"
        }
        val target = File(dir, "icon_${System.currentTimeMillis()}.$ext")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        return if (copied) target.absolutePath else null
    }

    /** 裁剪确认：复制文件 + 写设置（路径 / 缩放 / 偏移）。新文件替换旧文件。 */
    fun onIconConfirm(uri: android.net.Uri, scale: Float, offsetX: Float, offsetY: Float) {
        val oldPath = floatBall.customIconPath
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyIconToFiles(uri) }
            if (path == null) {
                hunoia.luno.bridge.ui.showToast(R.string.float_ball_custom_icon_copy_failed)
                return@launch
            }
            withContext(Dispatchers.IO) {
                // 旧文件删除失败不阻塞（留 orphan 由下次覆盖清理）。
                if (oldPath != null) runCatching { File(oldPath).delete() }
            }
            update {
                it.copy(
                    customIconPath = path,
                    customIconScale = scale,
                    customIconOffsetX = offsetX,
                    customIconOffsetY = offsetY,
                )
            }
        }
    }

    /** 恢复默认图标：清字段 + 删除已复制的文件（失败不阻塞，留 orphan 由下次覆盖清理）。 */
    fun resetCustomIcon() {
        scope.launch {
            val old = floatBall.customIconPath
            withContext(Dispatchers.IO) {
                if (old != null) runCatching { File(old).delete() }
            }
            update {
                it.copy(
                    customIconPath = null,
                    customIconScale = 1f,
                    customIconOffsetX = 0f,
                    customIconOffsetY = 0f,
                    customIconAlpha = FloatBallDefaults.CustomIconAlpha,
                )
            }
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball),
        onBack = onBack,
        state = listState,
        sheets = {
            if (showColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = floatBall.colorSpec.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        val colorSpec = selection.toColorSpec(scheme)
                        update {
                            it.copy(
                                color = colorSpec.customColor,
                                colorSource = colorSpec.source,
                                colorThemeKey = colorSpec.themeKey,
                            )
                        }
                        showColorPicker = false
                    },
                )
            }
            pendingIconUri?.let { uri ->
                IconCropSheet(
                    context = context,
                    uri = uri,
                    initialScale = floatBall.customIconScale,
                    initialOffsetX = floatBall.customIconOffsetX,
                    initialOffsetY = floatBall.customIconOffsetY,
                    initialAlpha = floatBall.customIconAlpha,
                    onDismiss = { pendingIconUri = null },
                    onConfirm = { scale, offsetX, offsetY ->
                        pendingIconUri = null
                        onIconConfirm(uri, scale, offsetX, offsetY)
                    },
                )
            }
        },
    ) {
        if (!overlayGranted) {
            item(key = "permission") {
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_permission_required),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        shape = segmentedShape(0, 1),
                        onClick = {
                            context.gotoOverlaySettings()
                            overlayGranted = context.canDrawOverlays()
                        },
                    )
                }
            }
        }

        item(key = "toggle") {
            LunoGroup(title = stringResource(R.string.float_ball)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_enabled),
                    checked = floatBall.enabled,
                    onCheckedChange = { enabled ->
                        update { it.copy(enabled = enabled) }
                        facades.system.setFloatBallEnabled(context, enabled)
                    },
                    shape = segmentedShape(0, 1),
                )
            }
        }

        item(key = "trigger") {
            LunoGroup(title = stringResource(R.string.float_ball_trigger_actions)) {
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_tap_action),
                    subtitle = floatBall.tapAction?.actionTextCompose()
                        ?: stringResource(R.string.float_ball_tap_unset),
                    icon = Icons.Default.TouchApp,
                    shape = segmentedShape(0, 1),
                    onClick = onEditTapAction,
                )
            }
        }

        item(key = "appearance") {
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                val hasCustomIcon = floatBall.customIconPath != null
                // 行序：图标 → [恢复默认] → 颜色 → 大小 → 静置 → [透明度] → [静置暂停]。
                val extraRows = 2 + (if (hasCustomIcon) 1 else 0)
                val count = 4 + extraRows
                val afterIcon = 1 + if (hasCustomIcon) 1 else 0
                var localAlpha by remember {
                    mutableFloatStateOf(floatBall.customIconAlpha)
                }
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_custom_icon),
                    subtitle = if (hasCustomIcon) {
                        stringResource(R.string.float_ball_custom_icon_set)
                    } else {
                        stringResource(R.string.float_ball_custom_icon_default)
                    },
                    icon = Icons.Default.Image,
                    shape = segmentedShape(0, count),
                    leading = {
                        if (hasCustomIcon) {
                            // 行内缩略预览：与球面同一渲染路径，实时反映当前缩放/偏移/透明度。
                            hunoia.luno.runtime.floatball.CustomBallIcon(
                                context = context,
                                model = File(floatBall.customIconPath!!),
                                scale = floatBall.customIconScale,
                                offsetX = floatBall.customIconOffsetX,
                                offsetY = floatBall.customIconOffsetY,
                                alpha = floatBall.customIconAlpha,
                                modifier = Modifier.size(MarkColorSize),
                            )
                        }
                    },
                    onClick = { iconPicker.launch("image/*") },
                )
                if (hasCustomIcon) {
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_custom_icon_reset),
                        icon = Icons.Default.Refresh,
                        shape = segmentedShape(1, count),
                        onClick = ::resetCustomIcon,
                    )
                }
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_color),
                    icon = Icons.Default.Palette,
                    shape = segmentedShape(afterIcon, count),
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MarkColorSize)
                                .background(
                                    color = floatBall.colorSpec.toMarkColor(),
                                    shape = CircleShape,
                                ),
                        )
                    },
                    onClick = { showColorPicker = true },
                )
                LunoTextSlider(
                    value = localSizeDp,
                    onValueChange = { value -> localSizeDp = value },
                    onValueChangeFinished = { update { it.copy(sizeDp = localSizeDp) } },
                    text = stringResource(R.string.float_ball_size),
                    valueDisplay = { "${it.toInt()} dp" },
                    valueRange = FloatBallDefaults.MinSizeDp..FloatBallDefaults.MaxSizeDp,
                    shape = segmentedShape(afterIcon + 1, count),
                )
                LunoTextSlider(
                    value = localIdleDelay,
                    onValueChange = { value -> localIdleDelay = value },
                    onValueChangeFinished = {
                        update { it.copy(idleDimDelayMs = localIdleDelay.toLong()) }
                    },
                    text = stringResource(R.string.float_ball_idle_dim_delay),
                    valueDisplay = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()} ms" },
                    valueRange = FloatBallDefaults.IdleDimDelayMsMin.toFloat()..
                        FloatBallDefaults.IdleDimDelayMsMax.toFloat(),
                    shape = segmentedShape(afterIcon + 2, count),
                )
                // 透明度只在有自定义图标时有意义（默认图标自带 alpha 语义）。
                if (hasCustomIcon) {
                    LunoTextSlider(
                        value = localAlpha,
                        onValueChange = { value -> localAlpha = value },
                        onValueChangeFinished = {
                            update { it.copy(customIconAlpha = localAlpha) }
                        },
                        text = stringResource(R.string.float_ball_custom_icon_alpha),
                        valueDisplay = { "${(it * 100).toInt()}%" },
                        valueRange = FloatBallDefaults.CustomIconAlphaMin..FloatBallDefaults.CustomIconAlphaMax,
                        shape = segmentedShape(afterIcon + 3, count),
                    )
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_custom_icon_pause_when_idle),
                        subtitle = stringResource(R.string.float_ball_custom_icon_pause_when_idle_subtitle),
                        checked = floatBall.customIconPauseWhenIdle,
                        onCheckedChange = { checked ->
                            update { it.copy(customIconPauseWhenIdle = checked) }
                        },
                        shape = segmentedShape(afterIcon + 4, count),
                    )
                }
            }
        }

        item(key = "behavior") {
            LunoGroup(title = stringResource(R.string.float_ball_behavior)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_follow_hand),
                    subtitle = stringResource(R.string.float_ball_follow_hand_subtitle),
                    checked = floatBall.followHand,
                    onCheckedChange = { checked -> update { it.copy(followHand = checked) } },
                    shape = segmentedShape(0, 1),
                )
            }
        }
    }
}

/** 自定义图标落盘目录（相对 filesDir）。 */
private const val CUSTOM_ICON_DIR = "floatball_icon"
