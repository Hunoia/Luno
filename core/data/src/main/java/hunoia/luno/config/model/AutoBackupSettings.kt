package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 定时自动备份的设备本地状态。
 *
 * 刻意**不进备份**（不挂在 [Backup] 上、不进 `ConfigBackupRepository` 的快照/重置列表）：
 * - [treeUri] 是本机的 SAF 持久化授权，拷到新设备就是废值；
 * - 开关 / 上次执行时间属于本机运行态，不是用户配置资产。
 *
 * 因此改这个模型不需要升任何 Schema 版本。
 */
@Serializable
@Keep
data class AutoBackupSettings(
    /** 用户是否开启定时自动备份。 */
    val enabled: Boolean = false,
    /** 用户选定的备份目录（`OpenDocumentTree` 返回的 tree uri 字符串）。 */
    val treeUri: String = "",
    /** 最近一次**成功**备份的时刻（epoch ms）；0 = 还没有成功过。 */
    val lastSuccessAtMs: Long = 0,
    /** 最近一次**失败**的时刻（epoch ms）；0 = 还没有失败过。 */
    val lastFailureAtMs: Long = 0,
    /**
     * 最近一次失败的原因码（`AutoBackupRepository.Failure.name`），成功后清空。
     * 存码不存文案：文案由通知 / 子页按当前语言映射，换语言不用回改数据。
     * 也是没通知权限时的唯一反馈来源（子页红字）。
     */
    val lastError: String = "",
) {
    /** 是否已选好目录，可入队执行。 */
    fun hasTree(): Boolean = treeUri.isNotBlank()
}
