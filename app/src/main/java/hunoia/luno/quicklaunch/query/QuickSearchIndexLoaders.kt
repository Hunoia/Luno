package hunoia.luno.quicklaunch.query

import hunoia.luno.R
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.model.SettingsPage
import hunoia.luno.quicklaunch.query.AppSearch.key

fun QuickSearchIndex.hasContactsPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * 联系人加载：必须在后台线程调（调用方 LaunchedEffect + IO）。
     * 无权限直接记空（调用方展示授权行）；按人聚合去重，cap 2000 防大通讯录卡顿。
     */
fun QuickSearchIndex.ensureContacts() {
        if (contactsLoaded) return
        contactsLoaded = true
        if (!hasContactsPermission()) return
        val list = mutableListOf<QuickSearchResult.Contact>()
        runCatching {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                )
                val numIdx = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                )
                while (cursor.moveToNext() && list.size < 2000) {
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        list.add(QuickSearchResult.Contact(name, number))
                    }
                }
            }
        }
        contacts = list.distinctBy { it.name to it.number }
        for (c in contacts) {
            AppSearch.buildIndex(c.name)
            AppSearch.buildIndex(c.number)
        }
    }

    /** 联系人是否已就绪（权限 + 加载完成）：search 只查内存，不在主线程碰库。 */
fun QuickSearchIndex.contactsReady(): Boolean = contactsLoaded && hasContactsPermission()

fun QuickSearchIndex.hasMediaPermission(kind: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, kind) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

fun QuickSearchIndex.hasAllFilesAccess(): Boolean = android.os.Environment.isExternalStorageManager()

    /**
     * 音乐 + 图片视频 + 文档加载：必须在后台线程调。
     * 有啥权限装啥（部分授权也认）；文档只取非媒体 mime，避开跟图库/音乐重复行。
     */
fun QuickSearchIndex.ensureMedia() {
        val audioOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
        val imageOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
        val videoOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
        if (audioOk && !audioTried) {
            audioTried = true
            val list = mutableListOf<QuickSearchResult.AudioTrack>()
            runCatching {
                val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                )
                val sort = "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
                context.contentResolver.query(uri, projection, null, null, sort)?.use { c ->
                    val idIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                    val titleIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistIdx = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                    while (c.moveToNext() && list.size < 3000) {
                        val title = c.getString(titleIdx)?.trim().orEmpty()
                        if (title.isEmpty()) continue
                        val id = c.getLong(idIdx)
                        val artist = c.getString(artistIdx)?.trim().orEmpty()
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                        list.add(QuickSearchResult.AudioTrack(title, artist, contentUri))
                    }
                }
            }
            tracks = list.distinctBy { it.title to it.artist }
            for (t in tracks) {
                AppSearch.buildIndex(t.title)
                AppSearch.buildIndex(t.artist)
            }
        }
        if ((imageOk || videoOk) && !galleryTried) {
            galleryTried = true
            val list = mutableListOf<QuickSearchResult.ImageVideo>()
            runCatching {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns._ID,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.MIME_TYPE,
                    android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                )
                // 图库只取图片/视频（音频归音乐，文档归 DocFile，三方互斥不重复）。
                val sel = StringBuilder("(")
                if (imageOk) sel.append("${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%'")
                if (imageOk && videoOk) sel.append(" OR ")
                if (videoOk) sel.append("${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'")
                sel.append(")")
                val sort = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                context.contentResolver.query(uri, projection, sel.toString(), null, sort)?.use { c ->
                    val idIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns._ID)
                    val nameIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MIME_TYPE)
                    val bucketIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val modIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (c.moveToNext() && list.size < 5000) {
                        val name = c.getString(nameIdx)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val id = c.getLong(idIdx)
                        val mime = c.getString(mimeIdx).orEmpty()
                        val bucket = c.getString(bucketIdx)?.trim().orEmpty()
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                        list.add(QuickSearchResult.ImageVideo(name, bucket, contentUri, mime, c.getLong(modIdx)))
                    }
                }
            }
            mediaFiles = list.distinctBy { it.uri }
            for (f in mediaFiles) AppSearch.buildIndex(f.name)
        }
        if (hasAllFilesAccess() && !docsTried) {
            docsTried = true
            val list = mutableListOf<QuickSearchResult.DocFile>()
            runCatching {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns._ID,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.MIME_TYPE,
                    android.provider.MediaStore.Files.FileColumns.SIZE,
                    android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                )
                val mt = android.provider.MediaStore.Files.FileColumns.MIME_TYPE
                val sel = "($mt NOT LIKE 'image/%' AND $mt NOT LIKE 'video/%' AND $mt NOT LIKE 'audio/%')"
                val sort = "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                context.contentResolver.query(uri, projection, sel, null, sort)?.use { c ->
                    val idIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns._ID)
                    val nameIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.SIZE)
                    val modIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (c.moveToNext() && list.size < 5000) {
                        val name = c.getString(nameIdx)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val id = c.getLong(idIdx)
                        val mime = c.getString(mimeIdx).orEmpty()
                        val sizeText = formatSize(c.getLong(sizeIdx))
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id).toString()
                        list.add(QuickSearchResult.DocFile(name, sizeText, contentUri, mime, c.getLong(modIdx)))
                    }
                }
            }
            docs = list.distinctBy { it.uri }
            for (d in docs) AppSearch.buildIndex(d.name)
        }
    }

    /** 媒体是否已就绪（任一源试过）：search 只查内存，权限实时复查。 */
fun QuickSearchIndex.mediaReady(): Boolean = audioTried || galleryTried || docsTried

fun QuickSearchIndex.calendarReady(): Boolean = calendarTried && hasMediaPermission(android.Manifest.permission.READ_CALENDAR)
fun QuickSearchIndex.smsReady(): Boolean = smsTried && hasMediaPermission(android.Manifest.permission.READ_SMS)
fun QuickSearchIndex.callsReady(): Boolean = callsTried && hasMediaPermission(android.Manifest.permission.READ_CALL_LOG)

    /** 日历前后各 30 天：必须在后台线程调。 */
fun QuickSearchIndex.ensureCalendar() {
        if (calendarTried) return
        calendarTried = true
        if (!hasMediaPermission(android.Manifest.permission.READ_CALENDAR)) return
        val list = mutableListOf<QuickSearchResult.CalendarEvent>()
        runCatching {
            val now = System.currentTimeMillis()
            val day = 86400000L
            val builder = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now - 30 * day)
            android.content.ContentUris.appendId(builder, now + 30 * day)
            val proj = arrayOf(
                android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.EVENT_LOCATION,
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.EVENT_ID,
            )
            context.contentResolver.query(
                builder.build(), proj, null, null,
                "${android.provider.CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                val tIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                val lIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_LOCATION)
                val bIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                val eIdx = c.getColumnIndex(android.provider.CalendarContract.Instances.EVENT_ID)
                while (c.moveToNext() && list.size < 500) {
                    val title = c.getString(tIdx)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val begin = c.getLong(bIdx)
                    list.add(
                        QuickSearchResult.CalendarEvent(
                            title,
                            formatTime(begin),
                            c.getString(lIdx)?.trim().orEmpty(),
                            c.getLong(eIdx),
                            begin,
                        ),
                    )
                }
            }
        }
        events = list.distinctBy { it.eventId to it.timeText }
        for (e in events) {
            AppSearch.buildIndex(e.title_)
            AppSearch.buildIndex(e.location)
        }
    }

    /** 收件箱 500 条：必须在后台线程调。 */
fun QuickSearchIndex.ensureSms() {
        if (smsTried) return
        smsTried = true
        if (!hasMediaPermission(android.Manifest.permission.READ_SMS)) return
        val list = mutableListOf<QuickSearchResult.SmsMsg>()
        runCatching {
            val uri = android.provider.Telephony.Sms.Inbox.CONTENT_URI
            val proj = arrayOf(
                android.provider.Telephony.Sms.ADDRESS,
                android.provider.Telephony.Sms.BODY,
                android.provider.Telephony.Sms.DATE,
            )
            context.contentResolver.query(
                uri, proj, null, null, "${android.provider.Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val aIdx = c.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bIdx = c.getColumnIndex(android.provider.Telephony.Sms.BODY)
                val dIdx = c.getColumnIndex(android.provider.Telephony.Sms.DATE)
                while (c.moveToNext() && list.size < 500) {
                    val body = c.getString(bIdx)?.trim().orEmpty()
                    if (body.isEmpty()) continue
                    list.add(
                        QuickSearchResult.SmsMsg(
                            c.getString(aIdx)?.trim().orEmpty(),
                            body,
                            formatTime(c.getLong(dIdx)),
                        ),
                    )
                }
            }
        }
        smsList = list
        for (m in smsList) {
            AppSearch.buildIndex(m.address)
            AppSearch.buildIndex(m.body)
        }
    }

    /** 通话记录 200 条：必须在后台线程调。 */
fun QuickSearchIndex.ensureCalls() {
        if (callsTried) return
        callsTried = true
        if (!hasMediaPermission(android.Manifest.permission.READ_CALL_LOG)) return
        val list = mutableListOf<QuickSearchResult.CallLogEntry>()
        runCatching {
            val uri = android.provider.CallLog.Calls.CONTENT_URI
            val proj = arrayOf(
                android.provider.CallLog.Calls.CACHED_NAME,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE,
            )
            context.contentResolver.query(
                uri, proj, null, null, "${android.provider.CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val nIdx = c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                val numIdx = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val tIdx = c.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                val dIdx = c.getColumnIndex(android.provider.CallLog.Calls.DATE)
                while (c.moveToNext() && list.size < 200) {
                    val number = c.getString(numIdx)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val typeText = when (c.getInt(tIdx)) {
                        android.provider.CallLog.Calls.INCOMING_TYPE -> "呼入"
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> "呼出"
                        android.provider.CallLog.Calls.MISSED_TYPE -> "未接"
                        android.provider.CallLog.Calls.REJECTED_TYPE -> "拒接"
                        else -> ""
                    }
                    list.add(
                        QuickSearchResult.CallLogEntry(
                            c.getString(nIdx)?.trim().orEmpty(),
                            number,
                            typeText,
                            formatTime(c.getLong(dIdx)),
                        ),
                    )
                }
            }
        }
        callList = list
        for (c in callList) {
            AppSearch.buildIndex(c.name)
            AppSearch.buildIndex(c.number)
        }
    }

private fun QuickSearchIndex.formatTime(millis: Long): String = runCatching {
        java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }.getOrDefault("")

    /**
     * 最近文件（空态段）：图库 + 文档按修改时间倒序取前 N。
     * 权限实时复查：关掉权限后 stale 缓存不再参展；调用方只在 searchFiles 开时调。
     */
fun QuickSearchIndex.recentFiles(limit: Int = 10): List<QuickSearchResult> {
        val out = mutableListOf<Pair<Long, QuickSearchResult>>()
        val galleryOk = hasMediaPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ||
            hasMediaPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
        if (galleryOk) for (f in mediaFiles) out.add(f.modified to f)
        if (hasAllFilesAccess()) for (d in docs) out.add(d.modified to d)
        return out.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /**
     * 快捷开关行：读状态全是便宜 binder/IPC（异常直接 null），每次 search 现算，
     * 副标题诚实显示开/关/去设置。关键词跟标题一起匹配。
     */
fun QuickSearchIndex.toggleRows(): List<QuickSearchResult.PhoneToggle> {
        val on = context.getString(R.string.quick_search_state_on)
        val off = context.getString(R.string.quick_search_state_off)
        val rows = mutableListOf<QuickSearchResult.PhoneToggle>()
        val wifi = runCatching {
            val wm = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            if (wm?.isWifiEnabled == true) on else off
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.WIFI, "Wi-Fi", "wifi 无线 局域网 wlan", wifi))
        val bt = runCatching {
            val bm = context.applicationContext.getSystemService(android.bluetooth.BluetoothManager::class.java)
            if (bm?.adapter?.isEnabled == true) on else off
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.BLUETOOTH, "蓝牙", "蓝牙 bluetooth", bt))
        rows.add(
            QuickSearchResult.PhoneToggle(
                QuickSearchResult.ToggleKind.TORCH, "手电筒", "手电 闪光灯 torch", if (torchOn) on else off,
            ),
        )
        val dnd = runCatching {
            val nm = context.applicationContext.getSystemService(android.app.NotificationManager::class.java)
            if (nm?.isNotificationPolicyAccessGranted != true) {
                context.getString(R.string.quick_search_open_settings)
            } else if (nm.currentInterruptionFilter ==
                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            ) {
                on
            } else {
                off
            }
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.DND, "勿扰", "勿扰 免打扰 dnd", dnd))
        val loc = runCatching {
            val lm = context.applicationContext.getSystemService(android.location.LocationManager::class.java)
            if (lm != null && (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))
            ) {
                on
            } else {
                off
            }
        }.getOrNull().orEmpty()
        rows.add(QuickSearchResult.PhoneToggle(QuickSearchResult.ToggleKind.LOCATION, "定位", "定位 位置 gps", loc))
        rows.add(
            QuickSearchResult.PhoneToggle(
                QuickSearchResult.ToggleKind.HOTSPOT,
                "热点",
                "热点 hotspot 共享 网络",
                context.getString(R.string.quick_search_open_settings),
            ),
        )
        return rows
    }

private fun QuickSearchIndex.formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        if (kb < 1024) return "${if (kb < 10) String.format("%.1f", kb) else kb.toLong()} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${if (mb < 10) String.format("%.1f", mb) else mb.toLong()} MB"
        return "${String.format("%.1f", mb / 1024.0)} GB"
    }

    /** 设置页 label 与动作选择页同源：从 system.openSettings 的 Enum options 动态取。 */
internal fun QuickSearchIndex.systemSettingsPages(): List<QuickSearchResult.Settings> {
        val def = ActionDefinitions.byTypeId("system.openSettings") ?: return emptyList()
        val enum = def.parameters.filterIsInstance<ParameterDefinition.Enum>()
            .firstOrNull { it.key == "page" } ?: return emptyList()
        return enum.options.mapNotNull { opt ->
            runCatching { SettingsPage.valueOf(opt.value) }.getOrNull()?.let {
                QuickSearchResult.Settings(it, opt.label)
            }
        }
    }
