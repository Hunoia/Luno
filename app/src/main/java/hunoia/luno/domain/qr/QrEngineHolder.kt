package hunoia.luno.domain.qr

import hunoia.luno.capability.qr.ZxingQrEngine

/**
 * `:service` 进程内的条码识别引擎单例。
 *
 * [hunoia.luno.service.SideGestureService]（无障碍路径）与
 * [hunoia.luno.runtime.SurfaceActionRuntime]（悬浮球 / 网格面板路径）共用同一实例。
 * [ZxingQrEngine] 无状态（解码器每次新建），懒创建即可，不增加常驻基线。
 */
object QrEngineHolder {

    private val instance: ZxingQrEngine by lazy { ZxingQrEngine() }

    fun get(): ZxingQrEngine = instance
}
