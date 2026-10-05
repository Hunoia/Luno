package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.material.icons.filled.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.MAX_RECENT_QUERIES
import hunoia.luno.config.model.MIN_HISTORY_CHARS
import hunoia.luno.quicklaunch.query.QuickSearchResult
import kotlinx.coroutines.launch

    /** 直拷文本 + 已复制 toast：计算器/网址/引擎长按共用。 */
internal fun QuickSearchActivity.copyText(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText("quicksearch", text),
        )
        showToast(R.string.quick_search_copied)
    }

    /** 确认搜索记历史：只记 6 字以上的，去重 cap 上限，顺手清掉已存短串，供各结果分支共用。 */
internal fun QuickSearchActivity.recordQuery(text: String) {
        if (text.isBlank() || text.trim().length < MIN_HISTORY_CHARS) return
        lifecycleScope.launch {
            runCatching {
                hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                    it.copy(
                        recentQueries = (listOf(text) + it.recentQueries)
                            .distinct()
                            .filter { q -> q.trim().length >= MIN_HISTORY_CHARS }
                            .take(MAX_RECENT_QUERIES),
                    )
                }
            }
        }
    }

    /** P3：直开 uri（音乐/图片/视频/文档），无 handler 接住 toast。 */
internal fun QuickSearchActivity.viewUri(uri: String, mime: String) {
        val ok = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(android.net.Uri.parse(uri), mime.ifBlank { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.isSuccess
        if (!ok) showToast(R.string.quick_search_no_handler)
    }

    /** P3：分享文件（图片/视频/文档长按）。 */
internal fun QuickSearchActivity.shareUri(uri: String, mime: String) {
        val ok = runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mime.ifBlank { "*/*" }
                        putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    null,
                ),
            )
        }.isSuccess
        if (!ok) showToast(R.string.quick_search_no_handler)
    }

internal fun QuickSearchActivity.toggleTorch() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            torchPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        flipTorch()
    }

internal fun QuickSearchActivity.flipTorch() {
        val id = flashCameraId ?: return
        val next = !torchState.value
        val ok = runCatching {
            getSystemService(android.hardware.camera2.CameraManager::class.java)?.setTorchMode(id, next)
        }.isSuccess
        if (ok) torchState.value = next
    }

    /**
     * 快捷开关分发：能直切的切（手电/DND 已授权），不能直切的跳面板或设置页
     * （WiFi/蓝牙/定位/热点/DND 未授权——Android 10+ 起第三方本就切不动，不硬调私有 API）。
     */
internal fun QuickSearchActivity.handleToggle(kind: QuickSearchResult.ToggleKind) {
        when (kind) {
            QuickSearchResult.ToggleKind.WIFI -> runCatching {
                startActivity(
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        Intent(android.provider.Settings.Panel.ACTION_WIFI)
                    } else {
                        Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                    },
                )
            }
            QuickSearchResult.ToggleKind.BLUETOOTH -> runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            QuickSearchResult.ToggleKind.TORCH -> toggleTorch()
            QuickSearchResult.ToggleKind.DND -> {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                if (nm?.isNotificationPolicyAccessGranted == true) {
                    runCatching {
                        nm.setInterruptionFilter(
                            if (nm.currentInterruptionFilter ==
                                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                            ) {
                                android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                            } else {
                                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                            },
                        )
                    }
                    requestClose()
                } else {
                    runCatching {
                        startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                        )
                    }
                }
            }
            QuickSearchResult.ToggleKind.LOCATION -> runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            QuickSearchResult.ToggleKind.HOTSPOT -> {
                val ok = runCatching {
                    startActivity(Intent("android.settings.TETHER_SETTINGS"))
                }.isSuccess
                if (!ok) {
                    runCatching {
                        startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                    }
                }
            }
        }
    }

    /** 通话记录/联系人点：只跳拨号盘不直拨（CALL_PHONE 太危险，不碰）。 */
internal fun QuickSearchActivity.openDial(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")))
        }
    }

    /** 短信点：打开跟该号码的会话；通话记录长按/联系人长沿用此入口。 */
internal fun QuickSearchActivity.openSmsThread(address: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$address")))
        }
    }

    /** 日历点：看事件详情（系统日历接）。 */
internal fun QuickSearchActivity.openCalendarEvent(eventId: Long) {
        val ok = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = android.content.ContentUris.withAppendedId(
                        android.provider.CalendarContract.Events.CONTENT_URI,
                        eventId,
                    )
                },
            )
        }.isSuccess
        if (!ok) showToast(R.string.quick_search_no_handler)
    }

    /**
     * 退场统一入口：只置 closing 位。finish() 由退场动画播完后亲自交接
     * （见 content 的 onExitFinished），不设固定 delay——动画和计时是并发的，
     * 固定 delay 只会在播完后多晾一段纯 blur。
     * 返回键也走这里（见 onCreate 的 onBackPressedDispatcher 拦截）。
     */

internal fun QuickSearchActivity.requestClose() {
        if (closingState.value) return
        if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "closeStart=${System.currentTimeMillis()}")
        closingState.value = true
        // blur 提前摘（背景立刻清晰，剩下 390ms 晾的是透明窗 + 清晰底，尾巴隐形）。
        // 穿透触摸试过，关闭已经够快，不需要，加状态位反而多风险，已删。
        // 重开恢复 blur 见 onWindowFocusChanged。
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }

    /** 退场动画播完后来调：拆窗 + 压住系统退场动画（0 = 禁用）。 */
internal fun QuickSearchActivity.finishAfterExit() {
        finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
