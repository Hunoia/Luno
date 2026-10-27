package hunoia.luno.config.model

/**
 * 兼容桥：运行态枚举已下沉 `hunoia.luno.core.domain`。
 * Phase 2 拆模块时删除，配置模型改为直接 import 契约包。
 */
typealias ScreenEventType = hunoia.luno.core.domain.ScreenEventType
typealias KeyboardEventType = hunoia.luno.core.domain.KeyboardEventType
typealias PlaybackDirection = hunoia.luno.core.domain.PlaybackDirection
typealias RingerMode = hunoia.luno.core.domain.RingerMode
typealias NetworkType = hunoia.luno.core.domain.NetworkType
typealias ConnectionDirection = hunoia.luno.core.domain.ConnectionDirection
