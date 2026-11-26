package hunoia.luno.floatball.settings.alert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertFamily
import hunoia.luno.config.model.AlertHost
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.host
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import kotlinx.coroutines.launch

/**
 * 添加层：按渲染器族列出空白层。
 *
 * v6 的「形态」是族内的 `shape` 单选项（屏缘呼吸 / 滚动 / 波纹 / 雷电，
 * 胶囊扫光 / 呼吸 / 翻转），添加层只选族。
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
    val resources = LocalResources.current
    val current = editor.library.presetById(presetId)
    LaunchedEffect(presetId, current) {
        if (current == null) onGone()
    }
    if (current == null) return

    fun add(layer: AlertLayer) {
        if (current.addBlockedBy(layer.family) != null) return
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

    LunoPage(title = stringResource(R.string.float_ball_layer_add_title),
onBack = onBack) {
    list() {
        lazy {

        item(key = "blank") {
            LunoGroup(subtitle = stringResource(R.string.float_ball_layer_add_blank_hint)) {
                val rows = segmentedRows {
                    AlertFamilyOrder.forEach { family ->
                        item { shape ->
                            val blocked = current.addBlockedBy(family)
                            LunoSettingsRow(
                                title = stringResource(
                                    R.string.float_ball_layer_add_blank,
                                    stringResource(family.labelRes()),
                                ),
                                subtitle = blocked?.let { resources.getString(it.reasonRes()) }
                                    ?: stringResource(familyHint(family)),
                                shape = shape,
                                enabled = blocked == null,
                                onClick = { add(blankLayer(family)) },
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }

        }
    }
}
}

/** 添加层的排列顺序（先宿主后族，胶囊三族相邻）。 */
private val AlertFamilyOrder = listOf(
    AlertFamily.Edge,
    AlertFamily.Ring,
    AlertFamily.Capsule,
    AlertFamily.Particles,
    AlertFamily.Wash,
    AlertFamily.Cover,
    AlertFamily.Reveal,
)

/** 族 → 参数说明（空白层的副标题），按宿主给出「它会画在哪」。 */
private fun familyHint(family: AlertFamily): Int = when (family.host) {
    AlertHost.Edge -> R.string.float_ball_layer_hint_edge
    AlertHost.Ball -> R.string.float_ball_layer_hint_ball
    AlertHost.Capsule -> R.string.float_ball_layer_hint_capsule
    AlertHost.Screen -> R.string.float_ball_layer_hint_screen
    AlertHost.Stage -> if (family == AlertFamily.Reveal) {
        R.string.float_ball_layer_hint_reveal
    } else {
        R.string.float_ball_layer_hint_particle
    }
}
