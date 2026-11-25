package hunoia.luno.domain.ocr

import android.content.Context
import hunoia.luno.capability.ocr.PpOcrEngine

/**
 * `:service` 进程内的 OCR 引擎单例。
 *
 * [hunoia.luno.service.SideGestureService]（无障碍路径）与
 * [hunoia.luno.runtime.SurfaceActionRuntime]（悬浮球 / 网格面板路径）共用同一实例，
 * 避免双引擎双份模型句柄。只在该进程需要时懒创建，不增加常驻基线。
 *
 * 注意：**不要取 `context.applicationContext`**。`SideGestureService` 在构造期初始化
 * `ocrEngine` 字段（传给 `ScreenPickOverlay`），此时 Service 的 `mBase` 尚未 attach，
 * `getApplicationContext()` 返回 null → NPE → 无障碍服务崩溃。`PpOcrEngine` 构造器只存
 * context 引用、不在构造期调用任何 context 方法；首次模型操作时才取 `applicationContext`
 * （拿不到则退回传入的 context），所以不会把 Service 实例钉在单例里。
 *
 * 模型随 APK 打包（资产直载），无导入/删除事件，实例创建后不需要 `reload()`。
 */
object OcrEngineHolder {

    @Volatile
    private var instance: PpOcrEngine? = null

    /** 进程内单例；调用方直接使用，无需 `reload()`。 */
    fun get(context: Context): PpOcrEngine = instance ?: synchronized(this) {
        instance ?: PpOcrEngine(context).also { instance = it }
    }
}
