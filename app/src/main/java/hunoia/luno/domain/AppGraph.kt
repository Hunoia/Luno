package hunoia.luno.domain

import hunoia.luno.domain.automation.AutomationCenter
import hunoia.luno.domain.automation.AutomationFacade
import hunoia.luno.domain.backup.BackupCenter
import hunoia.luno.domain.backup.BackupFacade
import hunoia.luno.domain.system.SystemFacade
import hunoia.luno.domain.system.SystemUseCases

/**
 * 轻量组装根：UI 只认门面接口，实现可在测试或后续拆分时替换。
 *
 * 不引入第三方 DI；实现默认指向 `:app` 内的门面对象。可写属性仅用于测试注入，
 * 生产路径不应在运行期替换。
 */
object AppGraph {

    var automation: AutomationFacade = AutomationCenter
    var backup: BackupFacade = BackupCenter
    var system: SystemFacade = SystemUseCases
}
