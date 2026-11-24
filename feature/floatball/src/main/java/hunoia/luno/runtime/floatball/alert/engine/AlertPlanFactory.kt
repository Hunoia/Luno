package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.paletteById
import hunoia.luno.core.effect.AlertContent
import hunoia.luno.core.effect.AlertEffectRequest
import hunoia.luno.runtime.floatball.alert.AlertPaletteSpec
import hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets
import hunoia.luno.runtime.floatball.alert.toSpec

/**
 * 配置 → 渲染计划：**一个预设 → 各启用层** → [AlertRenderPlan]。
 *
 * 纯 JVM，可单测；不弹窗、不读渲染状态。通知到达、试播、外部调用、编辑器试播共用这一条路径：
 * 调用侧只负责给出 `appPackage / anchor / 档位`，其余都在这里决定。
 */
internal object AlertPlanFactory {

    /** 当前可用的全部预设：内置 + 素材库用户预设（同 id 时用户预设覆盖内置）。 */
    fun presets(library: AlertLibrary): Map<String, AlertPreset> {
        val map = LinkedHashMap<String, AlertPreset>()
        BuiltinAlertPresets.all.forEach { map[it.id] = it }
        // 旧格式（v3 variants）解出来是空层预设：不展示、不播放，等用户重新保存。
        library.presets.filter { it.layers.isNotEmpty() }.forEach { map[it.id] = it }
        return map
    }

    /** 可供选择 / 编辑的预设（内置单层素材 + 用户单层 / 组合）。 */
    fun presetsForPicker(library: AlertLibrary): List<AlertPreset> =
        presets(library).values.filter { it.layers.isNotEmpty() }

    /**
     * 当前配置 → 渲染计划：总开关关闭 / 未选预设时返回空计划。
     *
     * @param tierFor 应用动效档位倍率（1 = 标准档），按层类别折叠进速度。
     */
    fun build(
        alert: NotificationAlertSettings,
        library: AlertLibrary,
        appPackage: String? = null,
        content: AlertContent? = null,
        tierFor: (AlertKind) -> Float = { 1f },
        seed: Int = 0,
    ): AlertRenderPlan {
        if (!alert.enabled) return AlertRenderPlan(layers = emptyList(), seed = seed)
        val presetId = alert.presetId ?: return AlertRenderPlan(layers = emptyList(), seed = seed)
        return buildFromPreset(
            preset = presets(library)[presetId],
            presetId = presetId,
            library = library,
            appPackage = appPackage,
            content = content,
            intensityScale = alert.intensityScale,
            speedScale = alert.speedScale,
            tierFor = tierFor,
            seed = seed,
        )
    }

    /** 外部请求 → 渲染计划（预设 id 由请求声明，层与宿主由预设决定）。 */
    fun buildFromRequest(
        request: AlertEffectRequest,
        library: AlertLibrary,
        appPackage: String? = null,
        tierFor: (AlertKind) -> Float = { 1f },
        seed: Int = request.seed ?: 0,
    ): AlertRenderPlan = buildFromPreset(
        preset = presets(library)[request.presetId],
        presetId = request.presetId,
        library = library,
        appPackage = appPackage,
        content = request.content,
        intensityScale = (request.intensity ?: 1f).coerceIn(0f, 2f),
        speedScale = request.speed,
        tierFor = tierFor,
        seed = seed,
    )

    /** 编辑器 / 试播：直接播一份（可能未落盘的）预设。 */
    fun buildFromPreset(
        preset: AlertPreset?,
        presetId: String,
        library: AlertLibrary,
        appPackage: String? = null,
        content: AlertContent? = null,
        intensityScale: Float = 1f,
        speedScale: Float = 1f,
        tierFor: (AlertKind) -> Float = { 1f },
        seed: Int = 0,
    ): AlertRenderPlan = AlertRenderPlanBuilder.build(
        preset = preset,
        presetId = presetId,
        paletteFor = paletteResolver(library),
        appPackage = appPackage,
        intensityScale = intensityScale,
        speedScale = speedScale,
        tierForKind = tierFor,
        seed = seed,
        content = content,
    )

    /** 颜色引用 → 渲染用调色板：逐色 > 素材库 / 内置调色板 > 跟随预设自身颜色。 */
    fun paletteResolver(library: AlertLibrary): (AlertColorRef) -> AlertPaletteSpec? = { ref ->
        when {
            ref.colors.isNotEmpty() -> AlertPaletteSpec(colors = ref.colors.take(5))
            ref.paletteId != null -> library.paletteById(ref.paletteId)?.toSpec()
            else -> null
        }
    }
}