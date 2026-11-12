package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 应用冻结域的配置：一键冻结名单。
 *
 * 这里只存「用户显式声明的冻结目标」，不存设备上当前哪些包处于禁用状态 ——
 * 后者是系统状态，可由 PackageManager 重建，不属于配置，也不进备份。
 * 解冻动作只作用于「名单 ∩ 当前已禁用」，因此不需要额外的所有权记录。
 */
@Serializable
@Keep
data class AppControlSettings(
    val oneKeyFreezePackageNames: Set<String> = emptySet(),
) {
    /** 名单为空 = 没有可备份的用户配置。 */
    fun isEmpty(): Boolean = oneKeyFreezePackageNames.isEmpty()
}
