package hunoia.luno.action.model

/**
 * 兼容桥：动作模型已下沉 `hunoia.luno.core.domain`。
 * Phase 2 拆模块时删除这些 typealias，调用方改为直接 import 契约包。
 */
typealias StoredAction = hunoia.luno.core.domain.StoredAction

typealias AudioStream = hunoia.luno.core.domain.AudioStream

typealias VolumeDirection = hunoia.luno.core.domain.VolumeDirection
