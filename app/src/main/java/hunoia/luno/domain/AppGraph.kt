package hunoia.luno.domain

import hunoia.luno.domain.backup.BackupCenter
import hunoia.luno.facade.Facades
import hunoia.luno.facade.BackupFacade
import hunoia.luno.facade.ClipboardFacade
import hunoia.luno.domain.clipboard.ClipboardUseCases
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
 * DataStore / 运行态。测试可用 `overrideXxx` 注入替身。
 */
object AppGraph : Facades {

    private var backupOverride: BackupFacade? = null
    private var systemOverride: SystemFacade? = null
    private var runtimeOverride: RuntimeFacade? = null
    private var ocrModelsOverride: OcrModelFacade? = null
    private var logsOverride: LogFacade? = null
    private var linkSettingsOverride: LinkSettingsFacade? = null
    private var clipboardOverride: ClipboardFacade? = null

    override val backup: BackupFacade
        get() = backupOverride ?: BackupCenter

    override val system: SystemFacade
        get() = systemOverride ?: SystemUseCases

    override val runtime: RuntimeFacade
        get() = runtimeOverride ?: RuntimeActions

    /** OCR 模型管理（PP-OCRv6 GPU 包的导入/删除/状态）。 */
    override val ocrModels: OcrModelFacade
        get() = ocrModelsOverride ?: OcrModelCenter

    /** 运行日志：设置页读取/导出，不暴露文件实现。 */
    override val logs: LogFacade
        get() = logsOverride ?: LogCenter

    /** 链接分发配置：页面只读快照流、只提交规则变换。 */
    override val linkSettings: LinkSettingsFacade
        get() = linkSettingsOverride ?: LinkSettingsActions

    /** 剪贴板写入敏感口径 + 图片 OCR 结果页。 */
    override val clipboard: ClipboardFacade
        get() = clipboardOverride ?: ClipboardUseCases

    fun overrideBackup(facade: BackupFacade?) {
        backupOverride = facade
    }

    fun overrideSystem(facade: SystemFacade?) {
        systemOverride = facade
    }

    fun overrideRuntime(facade: RuntimeFacade?) {
        runtimeOverride = facade
    }

    fun overrideOcrModels(facade: OcrModelFacade?) {
        ocrModelsOverride = facade
    }

    fun overrideLogs(facade: LogFacade?) {
        logsOverride = facade
    }

    fun overrideLinkSettings(facade: LinkSettingsFacade?) {
        linkSettingsOverride = facade
    }

    fun overrideClipboard(facade: ClipboardFacade?) {
        clipboardOverride = facade
    }
}
