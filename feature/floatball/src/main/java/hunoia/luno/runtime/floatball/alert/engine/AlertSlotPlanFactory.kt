package hunoia.luno.runtime.floatball.alert.engine

import hunoia.luno.config.model.AlertColorRef
import hunoia.luno.core.effect.AlertEffectHost
import hunoia.luno.core.effect.AlertEffectRequest
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertSlot
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.defaultPresetId
import hunoia.luno.config.model.paletteById
import hunoia.luno.config.model.preferredHost
import hunoia.luno.config.model.supports
import hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets
import hunoia.luno.runtime.floatball.alert.AlertPaletteSpec
import hunoia.luno.runtime.floatball.alert.toSpec

/**
 * 配置 → 渲染计划：五槽 → 预设变体 → [AlertRenderPlan]。
 *
 * 纯 JVM，可单测；不弹窗、不读渲染状态。通知到达、试播、外部调用共用这一条路径：
 * 调用侧只负责给出 `appPackage / anchor / 档位`，其余都在这里决定。
 */
internal object AlertSlotPlanFactory {

    /** 当前可用的全部预设：内置 + 素材库用户预设（同 id 时用户预设覆盖内置）。 */
    fun presets(library: AlertLibrary): Map<String, AlertPreset> {
        val map = LinkedHashMap<String, AlertPreset>()
        BuiltinAlertPresets.all.forEach { map[it.id] = it }
        library.presets.forEach { map[it.id] = it }
        return map
    }

    /**
     * 按槽位构建渲染计划：只收启用的槽位；预设缺失 / 宿主不匹配时跳过（由服务记日志）。
     *
     * @param tierFor 应用动效档位倍率（1 = 标准档）；按槽位折叠进速度覆盖。
     */
    fun build(
        alert: NotificationAlertSettings,
        library: AlertLibrary,
        appPackage: String? = null,
        tierFor: (AlertSlot) -> Float = { 1f },
        seed: Int = 0,
    ): AlertRenderPlan {
        val presets = presets(library)
        val requests = mutableListOf<AlertSlotRequest>()
        alert.slots.enabledSlots.forEach { slot ->
            val settings = alert.slots.of(slot)
            val presetId = settings.presetId ?: slot.defaultPresetId()
            val preset = presets[presetId] ?: return@forEach
            val host = preset.preferredHost(slot) ?: return@forEach
            val overrides = settings.overrides
            val color = when {
                overrides.colors.isNotEmpty() -> AlertColorRef(colors = overrides.colors.take(5))
                overrides.paletteId != null -> AlertColorRef(paletteId = overrides.paletteId)
                else -> null
            }
            val tier = tierFor(slot).coerceAtLeast(0.01f)
            requests += AlertSlotRequest(
                presetId = presetId,
                host = host,
                slot = slot,
                intensity = overrides.intensityPercent / 100f,
                speedScale = (overrides.speedPercent / 100f) * tier,
                colorOverride = color,
            )
        }
        return AlertRenderPlanBuilder.build(
            requests = requests,
            presets = presets,
            paletteFor = paletteResolver(library),
            appPackage = appPackage,
            seed = seed,
        )
    }

    /**
     * 外部请求 → 渲染计划（宿主由请求层显式声明，不做槽位偏好）。
     *
     * 调用进程托管与服务进程共用：预设集合来自素材库（内置 + 用户），
     * 请求级速度 / 强度 / 颜色覆盖叠加在预设之上。
     */
    fun buildFromRequest(
        request: AlertEffectRequest,
        library: AlertLibrary,
        appPackage: String? = null,
        seed: Int = request.seed ?: 0,
    ): AlertRenderPlan {
        val presets = presets(library)
        val requests = request.layers.mapNotNull { layer ->
            val host = layer.host.toAlertHost() ?: return@mapNotNull null
            val speed = (request.speed * (layer.overrides.speed ?: 1f)).coerceIn(0.25f, 4f)
            val color = layer.overrides.color?.let { override ->
                AlertColorRef(
                    paletteId = override.paletteId,
                    colors = override.colors.take(5),
                )
            }
            AlertSlotRequest(
                presetId = layer.presetId,
                host = host,
                intensity = layer.overrides.intensity,
                speedScale = speed,
                colorOverride = color,
            )
        }
        return AlertRenderPlanBuilder.build(
            requests = requests,
            presets = presets,
            paletteFor = paletteResolver(library),
            appPackage = appPackage,
            seed = seed,
        )
    }

    private fun AlertEffectHost.toAlertHost(): AlertHost? = when (this) {
        AlertEffectHost.Stage -> AlertHost.Stage
        AlertEffectHost.Edge -> AlertHost.Edge
        AlertEffectHost.Ball -> AlertHost.Ball
        AlertEffectHost.Capsule -> AlertHost.Capsule
        AlertEffectHost.Screen -> AlertHost.Screen
    }

    /** 颜色引用 → 渲染用调色板：逐色 > 素材库 / 内置调色板 > 跟随预设自身颜色。 */
    fun paletteResolver(library: AlertLibrary): (AlertColorRef) -> AlertPaletteSpec? = { ref ->
        when {
            ref.colors.isNotEmpty() -> AlertPaletteSpec(colors = ref.colors.take(5))
            ref.paletteId != null -> library.paletteById(ref.paletteId)?.toSpec()
            else -> null
        }
    }

    /**
     * 槽位 → 该槽可选的预设（设置页与试播共用）。
     *
     * 粒子槽与路径槽共用 `Stage` 宿主，这里再按「是否轨迹运动」分开：
     * 路径槽只列带路径 / 轨道 / 螺旋的预设，粒子槽列其余；两边都只是设置页分组，
     * 不阻止外部请求或用户预设跨槽使用。
     */
    fun presetsForSlot(slot: AlertSlot, library: AlertLibrary): List<AlertPreset> =
        presets(library).values.filter { preset ->
            preset.supports(slot) && when (slot) {
                AlertSlot.Particle -> !preset.isTrajectory()
                AlertSlot.Path -> preset.isTrajectory()
                else -> true
            }
        }

    private fun AlertPreset.isTrajectory(): Boolean {
        val motion = variants.firstOrNull()?.particle?.motion ?: return false
        return motion.path != null || motion.orbit > 0f || motion.spiral > 0f
    }
}
