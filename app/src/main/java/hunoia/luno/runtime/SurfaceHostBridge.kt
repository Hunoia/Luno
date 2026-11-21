package hunoia.luno.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.AppLauncher
import hunoia.luno.core.domain.LaunchSpec
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.execute.SurfaceHost

/**
 * 双执行上下文共用的 SurfaceHost 转发基座。
 *
 * 「两个上下文行为一致」的启动 / 跳页 / 分享 / OCR 类方法在这里实现一次：
 * `:service` 手势容器（GestureCoordinator）与进程级直派发（SurfaceActionRuntime）
 * 只覆写各自有差别的显隐与触钮钩子（音量刮擦、指针、面板存在性、触钮隐藏等）。
 * 新增「谁都能做」的 Surface 方法只改这里。
 */
internal abstract class SurfaceHostBridge(
    private val context: Context,
    /** 启动能力入口；两个上下文的构造方式不同（见各自实现），用 provider 保持其原有惰性。 */
    private val launcher: () -> AppLauncher,
) : SurfaceHost {

    override fun showQuickSearch() {
        hunoia.luno.quickaccess.QuickSearchActivity.start(context)
    }

    override fun showSpeechRecognition(): Boolean {
        hunoia.luno.quickaccess.SpeechRecognitionActivity.start(context)
        return true
    }

    override fun showGridPanel(panelId: String, anchorX: Int, anchorY: Int) {
        hunoia.luno.gridpanel.GridPanelOverlay.show(panelId, anchorX, anchorY)
    }

    override suspend fun launchApp(spec: LaunchSpec): Boolean = launcher().launch(spec)

    override suspend fun openUrl(
        url: String,
        miniWindow: Boolean,
        queryParameters: List<QueryParam>,
        anchor: Pair<Int, Int>?,
    ): Boolean = launcher().openUrl(url, miniWindow, queryParameters, anchor)

    override suspend fun launchShortcut(dataJson: String): Boolean =
        launcher().launchShortcut(dataJson)

    override suspend fun openAppDetails(packageName: String, userId: Int): ActionResult =
        launcher().openAppDetails(packageName, userId)

    override suspend fun launchInPopup(packageName: String, anchor: Pair<Int, Int>?): ActionResult =
        launcher().launchInPopup(packageName, anchor)

    override suspend fun launchMiniWindow(packageName: String, anchor: Pair<Int, Int>?): ActionResult =
        launcher().launchMiniWindow(packageName, anchor)

    override fun showScreenPick(): Boolean =
        hunoia.luno.service.SideGestureService.current?.showScreenPick() == true

    override fun showScreenPickPortal(x: Int, y: Int, sessionId: String?): Boolean =
        hunoia.luno.service.SideGestureService.current?.showScreenPickPortal(x, y, sessionId) == true

    override fun showScreenAnnotate(): Boolean =
        hunoia.luno.service.SideGestureService.current?.showScreenAnnotate() == true

    override fun openImageAnnotate(): Boolean =
        hunoia.luno.annotate.ImageAnnotateActivity.startPicker(context)

    override fun openPhotoRecognize(): Boolean =
        hunoia.luno.ui.photo.PhotoRecognizeActivity.start(context)

    override fun openGalleryRecognize(): Boolean =
        hunoia.luno.ui.photo.PhotoRecognizeActivity.startGallery(context)

    override fun showOcrSegmentText(text: String): Boolean =
        hunoia.luno.domain.ocr.ClipboardOcrLauncher.launchSegmentText(context, text)

    override suspend fun showClipboardOcr(uri: Uri, mimeType: String): ActionResult =
        hunoia.luno.domain.ocr.ClipboardOcrLauncher.launchImage(context, uri, mimeType)

    override fun shareFile(): ActionResult = runCatching {
        context.startActivity(
            Intent(context, hunoia.luno.ui.share.ShareFilePickActivity::class.java).apply {
                action = hunoia.luno.ui.share.ShareFilePickActivity.ACTION_SHARE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        ActionResult.Success()
    }.getOrElse { e ->
        ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
    }
}
