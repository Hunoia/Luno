package hunoia.luno.gridpanel

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.actions.icon.IconPalette
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.PanelDockDefaults
import hunoia.luno.config.model.singlePanelWidgetId
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry

/** 底栏 tab 的图标：矢量图标，或名称 / 自定义文字的首字字标。 */
sealed interface PanelDockIcon {
    data class Vector(val imageVector: ImageVector) : PanelDockIcon

    data class Glyph(val text: String) : PanelDockIcon
}

/** 底栏的一个 tab：面板引用 id + 展示用的名字与图标。 */
data class PanelDockEntry(
    /** 面板引用 id（网格面板 UUID 或 `single:<widgetId>`），与手势绑定同一套 id。 */
    val id: String,
    val label: String,
    val icon: PanelDockIcon,
)

/**
 * 面板底栏的成员解析。
 *
 * `panelDock.members` 是**用户写入的原始顺序**，这里负责把它变成可渲染的清单：
 * 过滤悬空引用（面板被删 / 功能已被注册表移除）、去重、按上限截断。
 *
 * 渲染与设置页共用这一份，避免「设置页列得出来、底栏却渲染不出」这类双实现漂移。
 * 纯解析，不读配置、不写配置。
 */
object PanelDockCatalog {

    /** 底栏是否渲染：总开关 + 至少两块面板。不满足时连高度都不预留。 */
    fun isVisible(settings: ActionSettings): Boolean =
        settings.panelDock.enabled &&
            memberIds(settings).size >= PanelDockDefaults.MinVisibleMembers

    /** 有效的成员 id，顺序 = tab 顺序。 */
    fun memberIds(settings: ActionSettings): List<String> {
        val gridIds = settings.actionGridPanels.panels.mapTo(HashSet()) { it.id }
        val singleIds = PanelWidgetRegistry.singlePanelWidgets.mapTo(HashSet()) { it.id }
        val seen = HashSet<String>()
        return settings.panelDock.members
            .asSequence()
            .filter { id ->
                val widgetId = singlePanelWidgetId(id)
                val valid = if (widgetId != null) {
                    singleIds.contains(widgetId)
                } else {
                    gridIds.contains(id)
                }
                valid && seen.add(id)
            }
            .take(PanelDockDefaults.MaxMembers)
            .toList()
    }

    /** 已选成员（顺序即 tab 顺序）。 */
    fun entries(context: Context, settings: ActionSettings): List<PanelDockEntry> =
        memberIds(settings).mapNotNull { id -> entry(context, settings, id) }

    /**
     * 设置页的可选清单：先固定功能页（注册表顺序），再网格面板（配置顺序）。
     * 顺序只是列表呈现，成员的真实顺序以 [memberIds] 为准。
     */
    fun candidates(context: Context, settings: ActionSettings): List<PanelDockEntry> =
        buildList {
            PanelWidgetRegistry.singlePanelWidgets.forEach { widget ->
                add(
                    PanelDockEntry(
                        id = hunoia.luno.config.model.singlePanelRefId(widget.id),
                        label = context.getString(widget.labelRes),
                        icon = PanelDockIcon.Vector(widget.icon),
                    ),
                )
            }
            settings.actionGridPanels.panels.forEach { panel ->
                add(
                    PanelDockEntry(
                        id = panel.id,
                        label = gridLabel(context, panel),
                        icon = iconFor(panel),
                    ),
                )
            }
        }

    private fun entry(context: Context, settings: ActionSettings, id: String): PanelDockEntry? {
        val widgetId = singlePanelWidgetId(id)
        if (widgetId != null) {
            val widget = PanelWidgetRegistry.byId(widgetId)?.takeIf { it.supportsSinglePanel }
                ?: return null
            return PanelDockEntry(id, context.getString(widget.labelRes), PanelDockIcon.Vector(widget.icon))
        }
        val panel = settings.actionGridPanels.panelById(id) ?: return null
        return PanelDockEntry(id, gridLabel(context, panel), iconFor(panel))
    }

    private fun gridLabel(context: Context, panel: GridPanel): String =
        panel.name.trim().ifEmpty {
            context.getString(hunoia.luno.actions.R.string.grid_panel_unnamed)
        }

    /**
     * 网格面板的底栏图标：设过就用（图标库 key / 自定义文字），没设过用名称首字，
     * 名称也空就退回默认网格图标 —— 三层回退保证 tab 永远画得出来。
     * 设置页的图标入口用同一个函数出预览，不另写一套解析。
     */
    fun iconFor(panel: GridPanel): PanelDockIcon {
        val key = panel.icon?.trim()?.takeIf { it.isNotEmpty() }
        if (key != null) {
            IconPalette.byKey(key)?.let { return PanelDockIcon.Vector(it) }
            return PanelDockIcon.Glyph(firstGlyph(key))
        }
        val name = panel.name.trim()
        if (name.isNotEmpty()) return PanelDockIcon.Glyph(firstGlyph(name))
        return PanelDockIcon.Vector(Icons.Default.GridView)
    }

    /** 首个码位：中文取一个字，emoji 不会被切成半个代理对。 */
    private fun firstGlyph(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return String(Character.toChars(trimmed.codePointAt(0)))
    }
}
