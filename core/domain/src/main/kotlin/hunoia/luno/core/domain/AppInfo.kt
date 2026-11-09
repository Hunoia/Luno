package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 启动器可见的一个应用/活动条目。
 *
 * 放在中性 model 包：配置、搜索、动作、面板都要用它，不能任何一方反向依赖另一方。
 * 平台查询与 Compose 图标扩展在 `hunoia.luno.quickaccess.model`（typealias 保持旧引用可用）。
 */
@Serializable
@Keep
data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val miniWindow: Boolean = false,
    /** 应用所属用户：0 = 主用户，其他值 = 分身（XSpace / 工作资料）。 */
    val userId: Int = 0,
) {
    /** 分身条目：包名/Activity 与主应用相同，仅用户不同。 */
    val isClone: Boolean get() = userId != 0
}
