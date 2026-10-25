package hunoia.luno.config.store

/**
 * DataStore 文件名：语义化命名，按五组域划分。
 * 改某域模型只升对应 Schema 版本（见 BackupVersions），不改文件名。
 */
object DataStoreFiles {

    // 手势配置组
    const val INITIAL_SETTINGS = "initial_settings"
    const val GESTURE_BUTTONS = "gesture_buttons"
    const val SUB_GESTURE_SETTINGS = "sub_gesture_settings"

    // 调参组
    const val ADVANCED_SETTINGS = "advanced_settings"
    const val GESTURE_SETTINGS = "gesture_settings"

    // 动作库组
    const val ACTION_SETTINGS = "action_settings"
    const val ACTION_LIBRARY_SETTINGS = "new_action_library"

    // 自动化组
    const val AUTOMATION_RULES = "automation_rules"

    // 搜索启动组
    const val QUICK_APP_LAUNCHER = "quick_app_launcher"
    const val QUICK_SEARCH_SETTINGS = "quick_search_settings"
    /** Luno 触发的应用启动统计：设备本地派生数据，不进备份。 */
    const val APP_USAGE_STATS = "app_usage_stats"

    // 瞬态：不进备份
    /** 自动化执行历史：调试用，可丢，刻意不进备份。 */
    const val AUTOMATION_LOG = "automation_log"
    /** 快速启动器应用列表快照。可从系统重建，刻意不进备份。 */
    const val QUICK_APP_SNAPSHOT = "quick_app_snapshot"
}

