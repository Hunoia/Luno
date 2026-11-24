package hunoia.luno.floatball.settings.alert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.category
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import kotlinx.coroutines.launch

/**
 * 添加层：空白层（按类别）+ 内置素材（点击即快照进组合）。
 *
 * 空白层给一份能直接播的默认参数；内置素材复制参数并记来源，之后内置改动不影响组合。
 * 超过上限的行禁用并写明原因，不静默吞掉点击。
 */
@Composable
fun AlertLayerAddScreen(
    presetId: String,
    onBack: () -> Unit,
    onAdded: (layerId: String) -> Unit,
    onGone: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = editor.library.presetById(presetId)
    LaunchedEffect(presetId, current) {
        if (current == null) onGone()
    }
    if (current == null) return

    fun add(layer: AlertLayer) {
        if (current.addBlockedBy(layer.kind) != null) return
        scope.launch {
            editor.commitLibrary { lib ->
                lib.copy(
                    presets = lib.presets.map {
                        if (it.id == current.id) it.withAddedLayer(layer) else it
                    },
                )
            }
            onAdded(layer.id)
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_layer_add_title),
        onBack = onBack,
    ) {
        item(key = "blank") {
            LunoGroup(subtitle = stringResource(R.string.float_ball_layer_add_blank_hint)) {
                val rows = segmentedRows {
                    // 按类别顺序列（Ball 与 Capsule 同属球体类，放一起）。
                    AlertKindOrder.forEach { kind ->
                        item { shape ->
                            val blocked = current.addBlockedBy(kind)
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_layer_add_blank, stringResource(kindLabel(kind))),
                                subtitle = blocked?.let { context.getString(it.reasonRes()) }
                                    ?: stringResource(kindHint(kind)),
                                shape = shape,
                                enabled = blocked == null,
                                onClick = { add(blankLayer(kind)) },
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }
        item(key = "builtin") {
            LunoGroup(
                title = stringResource(R.string.float_ball_layer_add_from_builtin),
                subtitle = stringResource(R.string.float_ball_layer_add_from_builtin_hint),
            ) {
                val rows = segmentedRows {
                    editor.library.pickerPresets().filter { it.builtin }.forEach { builtin ->
                        val layer = builtin.layers.firstOrNull() ?: return@forEach
                        item { shape ->
                            val blocked = current.addBlockedBy(layer.kind)
                            LunoSettingsRow(
                                title = builtin.displayName(),
                                subtitle = blocked?.let { context.getString(it.reasonRes()) }
                                    ?: (layer.patternLabel()
                                        ?: stringResource(layer.kind.category.titleRes())),
                                shape = shape,
                                enabled = blocked == null,
                                onClick = {
                                    add(
                                        layer.copy(
                                            id = newLayerId(),
                                            name = "",
                                            enabled = true,
                                            sourcePresetId = builtin.id,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }
    }
}

/** 添加层的排列顺序（按类别，球体与胶囊相邻）。 */
private val AlertKindOrder = listOf(
    AlertKind.Edge,
    AlertKind.Particle,
    AlertKind.Ball,
    AlertKind.Capsule,
    AlertKind.Screen,
    AlertKind.Reveal,
)

/** 类别 → 层参数说明（空白层的副标题），按 kind 给出「它会画在哪」。 */
private fun kindHint(kind: AlertKind): Int = when (kind) {
    AlertKind.Edge -> R.string.float_ball_layer_hint_edge
    AlertKind.Particle -> R.string.float_ball_layer_hint_particle
    AlertKind.Ball -> R.string.float_ball_layer_hint_ball
    AlertKind.Capsule -> R.string.float_ball_layer_hint_capsule
    AlertKind.Screen -> R.string.float_ball_layer_hint_screen
    AlertKind.Reveal -> R.string.float_ball_layer_hint_reveal
}

/** 单个形态的短名（拼进「添加空白 X 层」标题）；球体 / 胶囊不能用合并后的类别名。 */
@Composable
private fun kindLabel(kind: AlertKind): Int = when (kind) {
    AlertKind.Edge -> R.string.float_ball_layer_kind_edge
    AlertKind.Particle -> R.string.float_ball_layer_kind_particle
    AlertKind.Ball -> R.string.float_ball_layer_kind_ball
    AlertKind.Capsule -> R.string.float_ball_layer_kind_capsule
    AlertKind.Screen -> R.string.float_ball_layer_kind_screen
    AlertKind.Reveal -> R.string.float_ball_layer_kind_reveal
}
