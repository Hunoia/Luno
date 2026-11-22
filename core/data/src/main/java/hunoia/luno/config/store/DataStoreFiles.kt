package hunoia.luno.config.store

/**
 * DataStore 文件名：语义化命名，按备份域分组。
 * 改某域模型只升对应 Schema 版本（见 BackupVersions），不改文件名。
 */
object DataStoreFiles {

    // 手势配置组
    const val INITIAL_SETTINGS = "initial_settings"
    const val GESTURE_BUTTONS = "gesture_buttons"
    const val SUB_GESTURE_SETTINGS = "sub_gesture_settings"
    /** 识别区（全屏手势）：顺序即优先级，每区自带触发列表。 */
    const val GLOBAL_GESTURE = "global_gesture"

    // 调参组
    const val ADVANCED_SETTINGS = "advanced_settings"
    const val GESTURE_SETTINGS = "gesture_settings"

    // 动作库组
    const val ACTION_SETTINGS = "action_settings"
    const val ACTION_LIBRARY_SETTINGS = "new_action_library"

    // 标注组
    const val ANNOTATE_SETTINGS = "annotate_settings"

    // 搜索启动组
    const val QUICK_APP_LAUNCHER = "quick_app_launcher"
    const val QUICK_SEARCH_SETTINGS = "quick_search_settings"
    /** Luno 触发的应用启动统计：设备本地派生数据，不进备份。 */
    const val APP_USAGE_STATS = "app_usage_stats"

    // 应用冻结组
    /** 一键冻结名单；当前禁用状态是系统状态，不落盘。 */
    const val APP_CONTROL_SETTINGS = "app_control_settings"

    // 习惯组（独立于手势/动作/搜索，按 §11.1 语义分组）
    const val HABITS = "habits"
    const val TODOS = "todos"
    /** 趋势记录：指标定义 + 按指标分组的数值时间序列。 */
    const val TRENDS = "trends"

    // 瞬态：不进备份
    /** 快速启动器应用列表快照。可从系统重建，刻意不进备份。 */
    const val QUICK_APP_SNAPSHOT = "quick_app_snapshot"
    /** 电量 / 功率历史：7 天瞬态曲线数据，可从系统重新采，刻意不进备份。 */
    const val BATTERY_HISTORY = "battery_history"
    /** 剪贴板历史：设备本地派生数据；只有固定项随 `BackupDomain.CLIPBOARD` 导出。 */
    const val CLIPBOARD_HISTORY = "clipboard_history"
    /** 剪贴板设置（记录开关 / 未固定上限）：随 `BackupDomain.CLIPBOARD` 导出。 */
    const val CLIPBOARD_SETTINGS = "clipboard_settings"
    /** 定时自动备份的设备本地状态（开关 / 目录授权 / 上次执行结果）：授权是本机的，刻意不进备份。 */
    const val AUTO_BACKUP = "auto_backup"
    /**
     * 动态快捷方式导入记录：设备本地的系统授权引用，不注册备份域。
     * 恢复到另一台设备后必须重新导入；系统 pin 关系也不随应用数据迁移。
     */
    const val DYNAMIC_SHORTCUTS = "dynamic_shortcuts"
    /** 临时桌面（自动切换采集）的环境快照：原桌面持有者 + 导航方式，纯设备瞬态。 */
    const val HOME_SWITCH_SNAPSHOT = "home_switch_snapshot"
}

