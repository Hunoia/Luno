package hunoia.luno.config

import hunoia.luno.core.domain.HomeSwitchSnapshot

/**
 * 临时桌面（自动切换采集）环境快照的读写门面：切前记录原桌面持有者与导航方式，
 * 恢复时按此还原。设备瞬态，不进备份、不被「恢复默认设置」清掉。
 */
object HomeSwitchSnapshotStore {
    suspend fun snapshot(): HomeSwitchSnapshot = ConfigProvider.getHomeSwitchSnapshot()

    suspend fun update(transform: suspend (HomeSwitchSnapshot) -> HomeSwitchSnapshot) {
        ConfigProvider.updateHomeSwitchSnapshot(transform)
    }

    /** 快照是否存在（`homePackage` 非空 = 有一次未恢复的切换）。 */
    suspend fun hasActive(): Boolean = snapshot().homePackage.isNotBlank()

    suspend fun clear() {
        ConfigProvider.updateHomeSwitchSnapshot { HomeSwitchSnapshot(homePackage = "") }
    }
}
