package hunoia.luno.floatball.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.BallCustomIcon
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.CustomBallIcon
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.MarkColorSize
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 图标文件落盘目录（相对 filesDir）：主球与内容球共用，文件名带时间戳保唯一。 */
internal const val BALL_ICON_DIR = "floatball_icon"

/**
 * 内容球自定义图标的公共设置件：选图 → 裁剪 → 复制落盘 → 写配置。
 *
 * 通知球与结果球各持有自己的 [BallCustomIcon]，共享同一套交互与渲染路径；
 * 主球（[FloatBallEditScreen]）沿用自身的扁平字段，但落盘目录与本文件同源。
 */
@Stable
class BallIconPickerState internal constructor() {
    /** 待裁剪的图片 URI；非空即显示裁剪弹层。 */
    var pendingUri by mutableStateOf<Uri?>(null)
        internal set

    internal var pickAction: () -> Unit = {}

    /** 打开系统选图。 */
    fun pick() = pickAction()
}

@Composable
fun rememberBallIconPickerState(): BallIconPickerState {
    val state = remember { BallIconPickerState() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // GetContent 只在当前任务存活期间授权读取，确认后立即复制到 filesDir（见 onConfirm）。
        if (uri != null) state.pendingUri = uri
    }
    // 每次组合都指向当前 launcher；launcher 本身随宿主存活，赋值不会泄漏旧实例。
    state.pickAction = { launcher.launch("image/*") }
    return state
}

/**
 * 内容球的图标行（图标 → [恢复默认]）。
 *
 * 直接在 `LunoGroup { }` 里调用；[startIndex] / [rowCount] 用于和同组其它行拼接段形。
 */
@Composable
fun ColumnScope.BallCustomIconRows(
    context: Context,
    icon: BallCustomIcon,
    startIndex: Int,
    rowCount: Int,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    // 配置里的路径可能是失效的（文件被清理 / 跨包备份恢复留下的绝对路径指向他包目录）。
    // 球面渲染 [FloatBallWindow.BallFace] 有 exists() 检查并回退默认图标；编辑页预览必须
    // 一致——否则 `CustomBallIcon` 拿到不存在的 File，加载失败崩溃（FileNotFoundException）。
    val hasIcon = icon.path != null && File(icon.path).exists()
    LunoSettingsRow(
        title = stringResource(R.string.float_ball_custom_icon),
        subtitle = stringResource(
            if (hasIcon) R.string.float_ball_custom_icon_set
            else R.string.float_ball_custom_icon_default,
        ),
        icon = Icons.Default.Image,
        shape = segmentedShape(startIndex, rowCount),
        leading = {
            if (hasIcon) {
                // 行内缩略预览：与球面同一渲染路径，实时反映当前缩放 / 偏移 / 透明度。
                CustomBallIcon(
                    context = context,
                    model = File(icon.path!!),
                    scale = icon.scale,
                    offsetX = icon.offsetX,
                    offsetY = icon.offsetY,
                    alpha = icon.alpha,
                    modifier = Modifier.size(MarkColorSize),
                )
            }
        },
        onClick = onPick,
    )
    if (hasIcon) {
        LunoSettingsRow(
            title = stringResource(R.string.float_ball_custom_icon_reset),
            icon = Icons.Default.Refresh,
            shape = segmentedShape(startIndex + 1, rowCount),
            onClick = onReset,
        )
    }
}

/**
 * 待裁剪图标的弹层；宿主放进 `LunoScreenLazy` 的 `sheets` 槽。
 *
 * 确认时复制到 filesDir 并回调 [onChange]，由宿主写自己的那组配置；
 * 旧文件在这里删除，避免调用方各写一份复制 / 清理逻辑。
 */
@Composable
fun BallCustomIconSheet(
    context: Context,
    picker: BallIconPickerState,
    current: BallCustomIcon,
    onChange: (BallCustomIcon) -> Unit,
) {
    val uri = picker.pendingUri ?: return
    val scope = rememberCoroutineScope()
    IconCropSheet(
        context = context,
        uri = uri,
        initialScale = current.scale,
        initialOffsetX = current.offsetX,
        initialOffsetY = current.offsetY,
        initialAlpha = current.alpha,
        onDismiss = { picker.pendingUri = null },
        onConfirm = { scale, offsetX, offsetY ->
            // 确认即退场（AnchoredDialog 播完退场回调这里）：把 pendingUri 置空会让本组合
            // 函数提前 return、退出组合，rememberCoroutineScope 随之取消 —— 若 persist 挂在
            // 该 scope 上，IO 复制虽能跑完，恢复主线程时会被取消，onChange（落盘
            // customIconPath）永远执行不到，表现为「文件已复制、设置没写入、球仍是默认图标」。
            // 用 NonCancellable 让复制+落盘在弹层拆掉后仍完成（短操作，无取消价值）。
            picker.pendingUri = null
            scope.launch {
                withContext(NonCancellable) {
                    val path = withContext(Dispatchers.IO) { copyIconToFiles(context, uri) }
                    if (path == null) {
                        showToast(R.string.float_ball_custom_icon_copy_failed)
                        return@withContext
                    }
                    withContext(Dispatchers.IO) { deleteIconFile(current.path) }
                    onChange(
                        current.copy(
                            path = path,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                        ),
                    )
                }
            }
        },
    )
}

/** 恢复默认图标：清配置 + 删除已复制文件（失败不阻塞，留 orphan 由下次覆盖清理）。 */
fun resetBallIcon(
    scope: CoroutineScope,
    current: BallCustomIcon,
    onChange: (BallCustomIcon) -> Unit,
) {
    scope.launch {
        withContext(Dispatchers.IO) { deleteIconFile(current.path) }
        onChange(BallCustomIcon())
    }
}

/**
 * 复制所选图标到 filesDir/[BALL_ICON_DIR]/，返回绝对路径；失败返回 null。
 *
 * 文件名带时间戳保证唯一：`CustomBallIcon` 以 `File` 为 Coil model，
 * `File.equals` 只看路径 —— 固定名换同类图时路径不变，Coil 判定 model 未变就不重载。
 */
private fun copyIconToFiles(context: Context, uri: Uri): String? {
    val dir = File(context.filesDir, BALL_ICON_DIR).apply { mkdirs() }
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

private fun deleteIconFile(path: String?) {
    if (path == null) return
    runCatching { File(path).delete() }
}
