package hunoia.luno.runtime.condition

import hunoia.luno.core.domain.RuntimeSnapshot

/**
 * 兼容桥：条件/模板共用的快照与脉冲已下沉 `hunoia.luno.core.domain`。
 * Phase 2 拆模块时删除，调用方改为直接 import 契约包。
 */
typealias ConditionContext = RuntimeSnapshot
typealias NotificationPulse = hunoia.luno.core.domain.NotificationPulse
typealias PackageChangePulse = hunoia.luno.core.domain.PackageChangePulse
typealias SharePulse = hunoia.luno.core.domain.SharePulse
typealias ClipboardPulse = hunoia.luno.core.domain.ClipboardPulse
