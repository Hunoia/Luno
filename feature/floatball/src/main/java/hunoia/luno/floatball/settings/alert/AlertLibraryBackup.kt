package hunoia.luno.floatball.settings.alert

import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLibraryExport
import hunoia.luno.config.model.AlertLibraryExportVersion
import java.util.UUID

internal const val AlertLibraryImportMaxPresets = 200
internal const val AlertLibraryImportMaxPalettes = 100
internal const val AlertLibraryImportMaxBytes = 2 * 1024 * 1024

internal fun AlertLibrary.toExport(now: Long): AlertLibraryExport = AlertLibraryExport(
    exportedAt = now,
    palettes = palettes,
    presets = presets.filter { !it.builtin },
)

/**
 * 合并导入：调色板先合并（冲突 id 重建，名称去重），再把预设里的调色板引用改到新 id。
 *
 * 返回合并后的库；[AlertLibraryExport] 不合法（版本更高 / 空）时返回 null。
 */
internal fun AlertLibrary.mergeImport(export: AlertLibraryExport): AlertLibrary? {
    if (export.schemaVersion > AlertLibraryExportVersion) return null
    // 外部文件不可信：一份素材库不可能有上千个预设。
    if (export.presets.size > AlertLibraryImportMaxPresets) return null
    if (export.palettes.size > AlertLibraryImportMaxPalettes) return null
    if (export.presets.isEmpty() && export.palettes.isEmpty()) return this

    val paletteIdMap = HashMap<String, String>()
    val paletteNames = palettes.map { it.name }.toMutableList()
    val newPalettes = palettes.toMutableList()
    export.palettes.forEach { incoming ->
        val id = if (palettes.any { it.id == incoming.id }) {
            "custom_${UUID.randomUUID()}".also { newId -> paletteIdMap[incoming.id] = newId }
        } else {
            incoming.id
        }
        newPalettes += incoming.copy(id = id, name = uniquePresetName(incoming.name, paletteNames))
        paletteNames += newPalettes.last().name
    }

    val presetNames = presets.map { it.name }.toMutableList()
    val imported = export.presets.map { preset ->
        val mappedLayers = preset.layers.map { layer ->
            layer.copy(
                id = "layer_${UUID.randomUUID()}",
                color = layer.color.copy(
                    paletteId = layer.color.paletteId?.let { paletteIdMap[it] ?: it },
                ),
            )
        }
        val name = uniquePresetName(preset.name, presetNames)
        presetNames += name
        preset.copy(
            id = "user_${UUID.randomUUID()}",
            name = name,
            builtin = false,
            paletteId = preset.paletteId?.let { paletteIdMap[it] ?: it },
            layers = mappedLayers,
        )
    }
    return copy(palettes = newPalettes, presets = presets + imported)
}
