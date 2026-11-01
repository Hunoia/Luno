package hunoia.luno.ui.annotate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * debug-only：把一张生成的测试位图直接送进图片标注页，真机验证工具条 / IME / 导出。
 *
 * 真实入口要经过系统图片选择器或识屏/OCR 交接，迭代 UI 时成本太高；这里只做
 * 「把位图交给同一个 Activity」，不复制 UI 逻辑，因此不构成第二条生产路径。
 *
 * 用法：
 * `adb shell am broadcast -a hunoia.luno.debug.ANNOTATE_PREVIEW [--ei w 1200 --ei h 2000]`
 */
class AnnotatePreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SCREEN) {
            hunoia.luno.service.SideGestureService.current?.showScreenAnnotate()
            return
        }
        val width = intent.getIntExtra("w", 1200).coerceIn(200, 4096)
        val height = intent.getIntExtra("h", 2000).coerceIn(200, 4096)
        val bitmap = runCatching { buildBitmap(width, height) }.getOrNull() ?: return
        runCatching { ImageAnnotateActivity.startWithBitmap(context, bitmap) }
    }

    companion object {
        const val ACTION_SCREEN = "hunoia.luno.debug.ANNOTATE_SCREEN"
    }

    private fun buildBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(32, 33, 36))
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = width * 0.10f
            isFakeBoldText = true
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 200, 200)
            textSize = width * 0.05f
        }
        canvas.drawText("标注预览 ${width}x${height}", width * 0.06f, height * 0.10f, title)
        val lines = listOf(
            "1. 用画笔拖一条曲线",
            "2. 换颜色 / 线宽后继续画",
            "3. 试矩形、箭头、马赛克",
            "4. 点文字工具，输入文字后点完成",
            "5. 选区后导出，检查裁剪",
        )
        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                width * 0.06f,
                height * (0.18f + index * 0.06f),
                body,
            )
        }
        // 参考色块与细线，方便看马赛克/线宽效果。
        val swatch = Paint().apply { style = Paint.Style.FILL }
        val colors = intArrayOf(
            Color.RED, Color.rgb(255, 149, 0), Color.YELLOW,
            Color.rgb(52, 199, 89), Color.rgb(0, 122, 255),
        )
        colors.forEachIndexed { index, color ->
            swatch.color = color
            canvas.drawRect(
                width * (0.06f + index * 0.14f),
                height * 0.55f,
                width * (0.06f + index * 0.14f) + width * 0.10f,
                height * 0.62f,
                swatch,
            )
        }
        return bitmap
    }
}
