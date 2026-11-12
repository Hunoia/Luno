package hunoia.luno.domain.appcontrol

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AppControlSettings
import kotlinx.coroutines.flow.Flow

/**
 * 应用冻结域的配置门面（feature UI 唯一入口）。
 *
 * 页面不直连 ConfigProvider；这里只做配置流的读写投影。冻结/解冻与系统状态查询
 * 在 `:capability:launch`，不经过本门面。
 */
object AppControlCenter {

    val settings: Flow<AppControlSettings> = ConfigProvider.appControlSettings

    suspend fun setShowSystemApps(enabled: Boolean) {
        ConfigProvider.updateActionSettings { it.copy(showSystemAppsInAppList = enabled) }
    }

    /** 选择器确认：用选择结果整体替换名单（选择器已预选现有成员）。 */
    suspend fun setOneKeyList(packageNames: Collection<String>) {
        ConfigProvider.updateAppControlSettings { settings ->
            settings.copy(oneKeyFreezePackageNames = packageNames.toSet())
        }
    }

    suspend fun setOneKey(packageName: String, included: Boolean) {
        ConfigProvider.updateAppControlSettings { settings ->
            val next = if (included) {
                settings.oneKeyFreezePackageNames + packageName
            } else {
                settings.oneKeyFreezePackageNames - packageName
            }
            if (next == settings.oneKeyFreezePackageNames) {
                settings
            } else {
                settings.copy(oneKeyFreezePackageNames = next)
            }
        }
    }
}
