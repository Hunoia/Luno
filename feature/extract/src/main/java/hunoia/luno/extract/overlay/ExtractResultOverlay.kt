package hunoia.luno.extract.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.extract.R
import hunoia.luno.core.LOG_TAG
import hunoia.luno.extract.ExtractActions
import hunoia.luno.extract.ExtractResult
import hunoia.luno.runtime.RuntimeHost
import hunoia.luno.runtime.overlay.safeAddView

/**
 * 取词结果浮层（transient Surface）：全屏透明、不压暗，底部卡片展示取到的文字 +
 * 复制 / 搜索 / 分享。与 `PointPickOverlay` 同构（TYPE_ACCESSIBILITY_OVERLAY + 不静默改剪贴板）。
 *
 * 不注册进 SurfaceController：它是一次性、自管理的瞬态浮层，和取点 / 识屏同款处理方式。
 */
class ExtractResultOverlay(
    private val host: RuntimeHost,
    private val actions: ExtractActions,
) {
    private companion object {
        /**
         * 无障碍 overlay 不带这个标志时，触摸会被系统拦掉（Android 12 起）。
         * `0x300000` 是两个 layout flag 的或，ROM 不接受拆分写法，只能照发。
         */
        const val TRUSTED_OVERLAY_FLAG = 0x300000
    }

    private var overlayView: View? = null

    fun show(result: ExtractResult.Text) {
        if (overlayView != null) return
        val wm = host.context.windowManager()
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                TRUSTED_OVERLAY_FLAG
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setContent {
                MaterialTheme {
                    ExtractResultScreen(
                        text = result.text,
                        onCopy = { actions.copy(result.text); close() },
                        onSearch = { actions.search(result.text); close() },
                        onShare = { actions.share(result.text); close() },
                        onDismiss = { close() },
                    )
                }
            }
        }
        if (!wm.safeAddView(composeView, lp)) {
            Log.e(LOG_TAG, "extract overlay: addView failed")
            return
        }
        overlayView = composeView
    }

    fun close() {
        val view = overlayView ?: return
        overlayView = null
        val wm = host.context.windowManager()
        runCatching { wm.removeView(view) }
    }

    @Composable
    private fun ExtractResultScreen(
        text: String,
        onCopy: () -> Unit,
        onSearch: () -> Unit,
        onShare: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.extract_dismiss), color = Color.White)
                    }
                    FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text(stringResource(R.string.extract_copy))
                    }
                    FilledTonalButton(onClick = onSearch, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Text(stringResource(R.string.extract_search))
                    }
                    FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(stringResource(R.string.extract_share))
                    }
                }
            }
        }
    }
}
