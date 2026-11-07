package hunoia.luno.action.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.core.domain.QuickToggleKind

/**
 * 快捷开关的展示名与图标：动作定义（行设置/搜索变体）、执行失败文案共用一份。
 *
 * 名字是中文常量而不是资源 id：搜索索引在无 Context 的契约口组装（`ActionSearchSource.index`），
 * 与 `ActionDefinition.name` 同一口径。需要本地化时整表上移到资源层，而不是这里加一套。
 */
val QuickToggleKind.label: String
    get() = when (this) {
        QuickToggleKind.DND -> "勿扰"
        QuickToggleKind.WIFI -> "Wi-Fi"
        QuickToggleKind.BLUETOOTH -> "蓝牙"
        QuickToggleKind.MOBILE_DATA -> "移动数据"
        QuickToggleKind.AIRPLANE -> "飞行模式"
        QuickToggleKind.LOCATION -> "定位"
        QuickToggleKind.HOTSPOT -> "热点"
        QuickToggleKind.AUTO_ROTATE -> "自动旋转"
        QuickToggleKind.DARK_MODE -> "深色模式"
    }

val QuickToggleKind.icon: ImageVector
    get() = when (this) {
        QuickToggleKind.DND -> Icons.Default.DoNotDisturbOn
        QuickToggleKind.WIFI -> Icons.Default.Wifi
        QuickToggleKind.BLUETOOTH -> Icons.Default.Bluetooth
        QuickToggleKind.MOBILE_DATA -> Icons.Default.SignalCellularAlt
        QuickToggleKind.AIRPLANE -> Icons.Default.AirplanemodeActive
        QuickToggleKind.LOCATION -> Icons.Default.LocationOn
        QuickToggleKind.HOTSPOT -> Icons.Default.WifiTethering
        QuickToggleKind.AUTO_ROTATE -> Icons.Default.ScreenRotation
        QuickToggleKind.DARK_MODE -> Icons.Default.DarkMode
    }
