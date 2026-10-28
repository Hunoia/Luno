package hunoia.luno.config.model

/**
 * 兼容桥：启动来源枚举已下沉 `hunoia.luno.core.domain`。
 * Phase 3 完成后删除，调用方改为直接 import 契约包。
 */
typealias AppLaunchSource = hunoia.luno.core.domain.AppLaunchSource
