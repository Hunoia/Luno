package hunoia.luno.gesture.settings.global

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.gesture.R

/**
 * 识别区显示名：用户命名优先；默认全屏区（id = "1"）用本地化「全屏」，
 * 其余匿名单区按序号显示，避免把中文默认名写进配置模型。
 */
@Composable
fun globalZoneLabel(zone: GlobalGestureZone, index: Int): String =
    zone.name.ifBlank {
        if (zone.id == "1") {
            stringResource(R.string.gesture_global_zone_full)
        } else {
            stringResource(R.string.gesture_global_zone_n, index + 1)
        }
    }
