package hunoia.luno.domain

import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.automation.AutomationFacade
import hunoia.luno.domain.backup.BackupCenter
import hunoia.luno.domain.backup.BackupFacade
import hunoia.luno.domain.ocr.OcrModelCenter
import hunoia.luno.domain.ocr.OcrModelFacade
import hunoia.luno.domain.system.SystemFacade
import hunoia.luno.domain.runtime.RuntimeActions
import hunoia.luno.domain.runtime.RuntimeFacade
import hunoia.luno.domain.system.SystemUseCases

/**
 * 轻量组装根：UI 只认门面接口，实现可在测试或后续拆分时替换。
 *
 * 不引入第三方 DI；默认实现按需解析（getter），避免仅引用组装根就提前初始化
 * DataStore / 运行态。测试可用 `overrideXxx` 注入替身。
 */
object AppGraph {

    private var automationOverride: AutomationFacade? = null
    private var backupOverride: BackupFacade? = null
    private var systemOverride: SystemFacade? = null
    private var runtimeOverride: RuntimeFacade? = null
    private var ocrModelsOverride: OcrModelFacade? = null

    val automation: AutomationFacade
        get() = automationOverride ?: AutomationCenter

    val backup: BackupFacade
        get() = backupOverride ?: BackupCenter

    val system: SystemFacade
        get() = systemOverride ?: SystemUseCases

    val runtime: RuntimeFacade
        get() = runtimeOverride ?: RuntimeActions

    /** OCR 模型管理（PP-OCRv5 AOT 包的导入/删除/状态）。 */
    val ocrModels: OcrModelFacade
        get() = ocrModelsOverride ?: OcrModelCenter

    fun overrideAutomation(facade: AutomationFacade?) {
        automationOverride = facade
    }

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
}
