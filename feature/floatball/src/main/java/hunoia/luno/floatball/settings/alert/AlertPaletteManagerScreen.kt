package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.config.model.rainbowRing
import hunoia.luno.config.model.removePalette
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.ListAddFab
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.PageGutter
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * 到达提示·调色板管理页：单列卡片 + 底部「新建」FAB。
 *
 * 内置卡主体不响应（不可编辑），右侧溢出菜单 = 以此新建 / 复制色值；
 * 自定义卡点主体进编辑（唯一编辑入口），右侧溢出菜单 = 复制一份 / 复制色值 / 删除。
 */
@Composable
fun AlertPaletteManagerScreen(
    onBack: () -> Unit,
    onEditPalette: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 本地即显状态：从编辑页 / 管理页返回时跟着 resume 刷新（写盘不保证回推活跃订阅者）。
    val editor = rememberAlertEditor()
    // 连点「新建」/「以此新建」会建出两个调色板并连进两次编辑页：用 creating 收口。
    var creating by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<String?>(null) } // 溢出菜单打开的卡
    var deleteTarget by remember { mutableStateOf<AlertPalette?>(null) }
    val duplicatedHint = stringResource(R.string.float_ball_capsule_alert_palette_duplicate_done)
    val defaultName = stringResource(R.string.float_ball_capsule_alert_palette_default_name)
    val hexCopiedHint = stringResource(R.string.float_ball_capsule_alert_palette_hex_copied)
    val hexCopyFailedHint = stringResource(R.string.float_ball_capsule_alert_palette_hex_copy_failed)
    // 「原名 副本」模板（带 %1$s）。stringResource 是 @Composable，只能在组合里取一次，
    // 下面 copyInPlace / duplicateFrom 这类普通函数里就直接复用这个值。
    val copyNameTemplate = stringResource(R.string.float_ball_preset_copy_name)

    /** 把 [template] 复制成自定义调色板（名称 = 原名；彩虹模板保持彩虹态），落盘后进编辑页。 */
    fun duplicateFrom(template: AlertPalette) {
        if (creating) return
        creating = true
        val newPalette = template.copy(
            id = "custom_${UUID.randomUUID()}",
            name = template.name.ifBlank { defaultName },
        )
        // 先等写盘完成并同步兜底缓存，再进编辑页：否则编辑页首帧读不到
        // 这个 id（写盘回推异步）会被判定为「已删除」直接弹回。
        scope.launch {
            persistLibraryNow { it.copy(palettes = it.palettes + newPalette) }
            TuneConfig.syncActionSnapshot()
            editor.refresh()
            showToast(duplicatedHint)
            onEditPalette(newPalette.id)
        }
    }

    /** 新建默认 3 色自定义调色板，落盘后进编辑页（同 [duplicateFrom] 的写盘时序约束）。 */
    fun createNew() {
        if (creating) return
        creating = true
        val newPalette = AlertPalette(
            id = "custom_${UUID.randomUUID()}",
            name = defaultName,
            colors = listOf(
                0xFFE53935.toInt(),
                0xFFFB8C00.toInt(),
                0xFF43A047.toInt(),
            ),
        )
        scope.launch {
            persistLibraryNow { it.copy(palettes = it.palettes + newPalette) }
            TuneConfig.syncActionSnapshot()
            editor.refresh()
            onEditPalette(newPalette.id)
        }
    }

    /** 复制一份（不跳编辑页）；名称 = 「原名 副本」，重名加序号避让。 */
    fun copyInPlace(source: AlertPalette) {
        if (creating) return
        creating = true
        val base = source.name.ifBlank { defaultName }
        val existing = editor.library.palettes.map { it.name }.toSet()
        val raw = String.format(copyNameTemplate, base)
        var name = raw
        var suffix = 2
        while (name in existing) {
            name = "$raw ($suffix)"
            suffix++
        }
        val copy = source.copy(id = "custom_${UUID.randomUUID()}", name = name)
        scope.launch {
            persistLibraryNow { it.copy(palettes = it.palettes + copy) }
            TuneConfig.syncActionSnapshot()
            editor.refresh()
            showToast(duplicatedHint)
        }
    }

    /** 色值逐行写入剪贴板（彩虹 = 当前环的采样色表）。 */
    fun copyHex(palette: AlertPalette) {
        val argbs = if (palette.rainbow) palette.rainbowRing() else palette.colors
        val text = argbs.joinToString("\n") { "#%06X".format(it and 0xFFFFFF) }
        val ok = ClipboardCenter.writeTextImmediate(text, "Luno palette")
        showToast(if (ok) hexCopiedHint else hexCopyFailedHint)
    }

    /** 删除落盘（弹窗确认后调）。 */
    fun remove(target: AlertPalette) {
        scope.launch {
            persistLibraryNow { it.removePalette(target.id) }
            TuneConfig.syncActionSnapshot()
            editor.refresh()
        }
    }

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.float_ball_capsule_alert_palette_manage),
onBack = onBack,
floatingContent = {
            ListAddFab(
                onClick = ::createNew,
                contentDescription = stringResource(R.string.float_ball_capsule_alert_palette_new),
            )
        },
dialogs = {
            val target = deleteTarget
            if (target != null) {
                ConfirmDialog(
                    title = stringResource(
                        R.string.float_ball_capsule_alert_palette_delete_confirm_title,
                        target.name,
                    ),
                    text = stringResource(R.string.float_ball_capsule_alert_palette_delete_subtitle),
                    confirmLabel = stringResource(
                        R.string.float_ball_capsule_alert_palette_delete_confirm,
                    ),
                    destructive = true,
                    onDismissRequest = { deleteTarget = null },
                    onConfirm = {
                        deleteTarget = null
                        remove(target)
                    },
                )
            }
        }) {
    list(bottomContentPadding = FloatingContentBottom) {
        lazy {

        // 自定义在上：用户自己的调色板是常用项，内置只作模板来源。
        item(key = "custom_title") {
            SectionLabel(stringResource(R.string.float_ball_capsule_alert_palette_custom))
        }
        val custom = editor.library.palettes
        if (custom.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.float_ball_capsule_alert_palette_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                )
            }
        } else {
            items(custom, key = { it.id }) { palette ->
                PaletteCard(
                    palette = palette,
                    trailing = {
                        PaletteCardMenu(
                            opened = menuId == palette.id,
                            onOpen = { menuId = palette.id },
                            onDismiss = { menuId = null },
                            builtin = false,
                            onDuplicate = { copyInPlace(palette) },
                            onCopyHex = { copyHex(palette) },
                            onDelete = { deleteTarget = palette },
                        )
                    },
                    onClick = { onEditPalette(palette.id) },
                )
            }
        }

        item(key = "builtin_title") {
            SectionLabel(stringResource(R.string.float_ball_capsule_alert_palette_builtin))
        }
        items(BuiltinAlertPalettes.all, key = { it.id }) { palette ->
            PaletteCard(
                palette = palette,
                trailing = {
                    PaletteCardMenu(
                        opened = menuId == palette.id,
                        onOpen = { menuId = palette.id },
                        onDismiss = { menuId = null },
                        builtin = true,
                        onDuplicate = { duplicateFrom(palette) },
                        onCopyHex = { copyHex(palette) },
                        onDelete = {},
                    )
                },
                // 内置卡主体不响应：不可编辑，入口只有溢出菜单，防误触复制。
                onClick = null,
            )
        }

        }
    }
}
}

/** 分组标签（内置 / 自定义）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PageGutter),
    )
}

/**
 * 卡片右侧溢出菜单：⋮ 按钮 + 下拉。内置卡 = 以此新建 / 复制色值；
 * 自定义卡 = 复制一份 / 复制色值 / 删除。
 *
 * [opened] / [onOpen] / [onDismiss] 都由调用方按卡片 id 管，保证同一时刻只开一个菜单
 * （菜单自身不知道自己是哪张卡，拿不到那个 state）。
 */
@Composable
private fun PaletteCardMenu(
    opened: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    builtin: Boolean,
    onDuplicate: () -> Unit,
    onCopyHex: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val duplicateLabel = stringResource(
        if (builtin) R.string.float_ball_capsule_alert_palette_duplicate
        else R.string.float_ball_capsule_alert_palette_copy,
    )
    val copyHexLabel = stringResource(R.string.float_ball_capsule_alert_palette_copy_hex)
    val deleteLabel = stringResource(R.string.float_ball_capsule_alert_palette_delete)

    // 不用 fillMaxWidth：这个 Box 是卡片右侧的 trailing 槽位，宽度由 Row 分配。
    Box {
        PressIconButton(
            onClick = onOpen,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.float_ball_alert_more),
                tint = scheme.onSurfaceVariant,
            )
        }
        LunoDropdownMenu(
            expanded = opened,
            onDismissRequest = onDismiss,
        ) {
            LunoDropdownMenuItem(
                text = duplicateLabel,
                leadingIcon = {
                    MenuItemIcon(Icons.Default.Add)
                },
                onClick = {
                    onDismiss()
                    onDuplicate()
                },
            )
            LunoDropdownMenuItem(
                text = copyHexLabel,
                leadingIcon = {
                    MenuItemIcon(Icons.Default.ContentCopy)
                },
                onClick = {
                    onDismiss()
                    onCopyHex()
                },
            )
            if (!builtin) {
                LunoDropdownMenuItem(
                    text = deleteLabel,
                    isError = true,
                    leadingIcon = {
                        MenuItemIcon(Icons.Default.Delete)
                    },
                    onClick = {
                        onDismiss()
                        onDelete()
                    },
                )
            }
        }
    }
}

/** 溢出菜单项的图标（统一尺寸 / 色调，避免各调用点重复写 tint）。 */
@Composable
private fun MenuItemIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
    )
}