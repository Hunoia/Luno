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
 * context 引用、不在构造期调用任何 context 方法，直接保存传入的 context 是安全的。
 * 调用方约定传入长生命周期 context（`SideGestureService` 自身 / `applicationContext`），
 * 不会因持有 Activity 引用而泄漏。
 *
 * 模型包导入 / 删除后由 `SideGestureService.refreshOcrEngine()` 调 `reload()`；
 * `SurfaceActionRuntime` 存活期且无障碍服务不在位时删除模型，本实例不会自动 reload，
 * 下次识别经 `OcrModelStore.isReady` 检查返回空结果（罕见路径，接受）。
 */
object OcrEngineHolder {

    @Volatile
    private var instance: PpOcrEngine? = null

    /** 返回具体 [PpOcrEngine]，调用方（如 SideGestureService.refreshOcrEngine）需要 `reload()`。 */
    fun get(context: Context): PpOcrEngine = instance ?: synchronized(this) {
        instance ?: PpOcrEngine(context).also { instance = it }
    }
}
