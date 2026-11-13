package hunoia.luno.domain

import hunoia.luno.domain.backup.BackupCenter
import hunoia.luno.facade.Facades
import hunoia.luno.facade.BackupFacade
import hunoia.luno.facade.ClipboardFacade
import hunoia.luno.facade.ClipFloatFacade
import hunoia.luno.domain.clipboard.ClipboardUseCases
import hunoia.luno.domain.system.ClipFloatUseCase
import hunoia.luno.domain.log.LogCenter
import hunoia.luno.facade.LogFacade
import hunoia.luno.domain.link.LinkSettingsActions
import hunoia.luno.facade.LinkSettingsFacade
import hunoia.luno.domain.ocr.OcrModelCenter
import hunoia.luno.facade.OcrModelFacade
import hunoia.luno.facade.SystemFacade
import hunoia.luno.domain.runtime.RuntimeActions
import hunoia.luno.facade.RuntimeFacade
import hunoia.luno.domain.system.SystemUseCases

/**
 * 轻量组装根：UI 只认门面接口，实现可在测试或后续拆分时替换。
 *
 * 不引入第三方 DI；默认实现按需解析（getter），避免仅引用组装根就提前初始化
 * DataStore / 运行态。测试当前只替换 system 门面（见 AppGraphTest）。
 */
object AppGraph : Facades {

    private var systemOverride: SystemFacade? = null

    override val backup: BackupFacade
        get() = BackupCenter

    override val system: SystemFacade
        get() = systemOverride ?: SystemUseCases

    override val runtime: RuntimeFacade
        get() = RuntimeActions

    /** OCR 模型管理（PP-OCRv6 GPU 包的导入/删除/状态）。 */
    override val ocrModels: OcrModelFacade
        get() = OcrModelCenter

    /** 运行日志：设置页读取/导出，不暴露文件实现。 */
    override val logs: LogFacade
        get() = LogCenter

    /** 链接分发配置：页面只读快照流、只提交规则变换。 */
    override val linkSettings: LinkSettingsFacade
        get() = LinkSettingsActions

    /** 剪贴板写入敏感口径 + 图片 OCR 结果页。 */
    override val clipboard: ClipboardFacade
        get() = ClipboardUseCases

    /** 剪贴板悬浮窗显式入口：页面只提交条目 id，窗口归常驻服务。 */
    override val clipFloat: ClipFloatFacade
        get() = ClipFloatUseCase

    fun overrideSystem(facade: SystemFacade?) {
        systemOverride = facade
    }
}
