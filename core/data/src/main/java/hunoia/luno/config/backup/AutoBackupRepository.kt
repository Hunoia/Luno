package hunoia.luno.config.backup

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.LOG_TAG
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 定时自动备份的 SAF 落盘：在用户选定的目录里建 zip、按保留策略滚动清理。
 *
 * 与手动备份（`BackupOperator.backup`）共用同一套打包逻辑，区别只有两件事：
 * 目标是「目录树里的新建文档」而不是用户当场选的文件，以及写完要清理旧份。
 *
 * 清理只碰**本模块命名规则**（见 [isAutoBackupName]）的文件，用户放进同一目录的
 * 其他文件一律不动——共享目录不是我们的地盘。
 */
object AutoBackupRepository {

    /** 自动备份文件名前缀；识别、清理、恢复列表都以它为准。 */
    const val FILE_PREFIX = "luno-backup-"

    const val ZIP_MIME = "application/zip"

    /** 目录里保留的自动备份份数（含最新一份）。 */
    const val KEEP = 7

    /** 备份失败的稳定原因码：存进 `AutoBackupSettings.lastError`，由 UI 映射成文案。 */
    enum class Failure {
        /** 持久化目录授权被系统/ROM 回收，或目录已被删除。 */
        PERMISSION_LOST,

        /** 目录树里建不出文档（无写权限 / 目录被删 / provider 异常）。 */
        CREATE_FAILED,

        /** 写入过程失败（读配置、打包、写流任一环节）。 */
        WRITE_FAILED,

        /** 备份超过 8MB 上限，被 `BackupOperator` 拒绝。 */
        TOO_LARGE,

        /**
         * SAF 在该 ROM 上无法创建文档，兜底直写需要「所有文件访问」，
         * 但用户还没授予（`Environment.isExternalStorageManager()` 为 false）。
         */
        NEED_ALL_FILES,

        /** 兜底直写只支持内部存储（primary 卷）；用户在外部存储/SD 卡上选的目录。 */
        UNSUPPORTED_LOCATION,

        /** 其余未分类异常。 */
        UNKNOWN,
    }

    class AutoBackupException(val failure: Failure, cause: Throwable? = null) :
        Exception("auto backup failed: $failure", cause)

    private val nameFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** `luno-backup-20261108-031500.zip`：秒级时间戳，同秒互撞再走 [uniqueName]。 */
    fun newDisplayName(nowMs: Long): String {
        val stamp = nameFormatter.format(Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()))
        return "$FILE_PREFIX$stamp.zip"
    }

    /**
     * 是否是我们产出的自动备份文件名。
     *
     * 形如 `luno-backup-<yyyyMMdd>-<HHmmss>[-<序号>].zip`：序号是同名撞车时由
     * [uniqueName] 追加的，必须一并认下——否则这些文件永远躲过保留清理，会无限堆积。
     */
    fun isAutoBackupName(name: String): Boolean {
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(".zip")) return false
        val stamp = name.removePrefix(FILE_PREFIX).removeSuffix(".zip")
        val parts = stamp.split("-")
        if (parts.size != 2 && parts.size != 3) return false
        val (date, time) = parts
        if (date.length != 8 || time.length != 6) return false
        if (!date.all { it.isDigit() } || !time.all { it.isDigit() }) return false
        if (parts.size == 3) {
            val sequence = parts[2]
            if (sequence.isEmpty() || !sequence.all { it.isDigit() }) return false
        }
        return true
    }

    /**
     * 同名不覆盖：时钟回拨 / 同秒重试会撞名，追加 `-1`、`-2`…直到不撞。
     * 返回的是完整文件名（含前缀与扩展名）。
     */
    fun uniqueName(base: String, existing: Collection<String>): String {
        if (base !in existing) return base
        val dot = base.lastIndexOf('.')
        val stem = base.substring(0, dot)
        val suffix = base.substring(dot)
        for (i in 1..999) {
            val candidate = "$stem-$i$suffix"
            if (candidate !in existing) return candidate
        }
        // 撞满 1000 次：正常用不到，兜底不返回已存在的名字。
        return "$stem-${System.currentTimeMillis()}$suffix"
    }

    /**
     * 需要删除的文件名（超出 [KEEP] 的最旧若干份）。
     *
     * 纯函数：入参是目录里所有文件名，只返回匹配我们命名规则的、除最新 [keep] 份之外的名字。
     * 文件名含时间戳且定长，字典序 == 时间序，故按名称降序即按新旧排序。
     */
    fun selectPrunable(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.filter { isAutoBackupName(it) }
            .sortedDescending()
            .drop(keep.coerceAtLeast(0))

    /** 持久化授权是否还持有该 tree（系统可能在存储清理时回收）。 */
    fun hasPersistedTree(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    /**
     * 目录的显示名（`Luno`、`SD 卡/backup` …），用于子页展示。
     * 拿不到（授权已失效 / provider 不给）返回 null，调用方退回原始 uri 或「未选择」。
     */
    fun treeDisplayName(context: Context, treeUri: Uri): String? {
        if (treeUri.authority == null) return null
        // tree 派生的 document URI：查询用（受 tree 授权覆盖，比裸 document URI 稳）。
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return runCatching {
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    /**
     * 目录里的自动备份列表（新 → 旧）。
     * 用于子页「从自动备份恢复」；查询失败按空列表处理，调用方据此提示。
     */
    fun listBackups(context: Context, treeUri: Uri): List<AutoBackupFile> {
        val authority = treeUri.authority ?: return emptyList()
        return childDocs(context, treeUri)
            .filter { isAutoBackupName(it.name) }
            .map { AutoBackupFile(it.name, it.documentUri(treeUri), it.lastModifiedMs) }
            .sortedByDescending { it.name }
    }

    /** 目录里全部条目（含用户自己的文件）：撞名判定要看得见别人，不能只看自己产出的。 */
    private data class ChildDoc(
        val docId: String,
        val name: String,
        val lastModifiedMs: Long,
    ) {
        /**
         * tree 授权覆盖的 document URI：必须用 `buildDocumentUriUsingTree` 从 tree 派生。
         * 裸 `buildDocumentUri` 不在 tree 授权范围内，openInputStream 会被
         * `FileSystemProvider.checkFileOpenPermission` 拒为 SecurityException（MANAGE_DOCUMENTS）。
         */
        fun documentUri(treeUri: Uri): Uri =
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

    private fun childDocs(context: Context, treeUri: Uri): List<ChildDoc> {
        // 子树查询必须用 tree 形式（标准 API）；该形式在本 ROM 上已验证可查询。
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val result = mutableListOf<ChildDoc>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val modified = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                result += ChildDoc(docId, name, modified)
            }
        }
        return result
    }

    /**
     * 在 [treeUri] 目录里新建一份整机备份，返回写入的文件名。
     *
     * 写完立即按保留策略清理旧份；清理失败只记日志，不影响「这次备份成功」的结论。
     *
     * SAF 建文档失败时（个别 ROM 的 provider 无法通过授权创建，见 [createBackupDocument]）
     * 走**直写兜底**：把 tree 解析成真实路径直接写文件（仅 primary 卷、需「所有文件访问」）。
     */
    suspend fun writeBackup(context: Context, treeUri: Uri): String {
        if (!hasPersistedTree(context, treeUri)) {
            throw AutoBackupException(Failure.PERMISSION_LOST)
        }
        // 撞名判定要看得见目录里所有文件（含用户自己放的），不能只看我们产出的。
        val existing = runCatching { childDocs(context, treeUri).map { it.name } }
            .getOrElse { emptyList() }
        val name = uniqueName(newDisplayName(System.currentTimeMillis()), existing)

        val safDoc = createBackupDocument(context, treeUri, name)
        if (safDoc != null) {
            try {
                BackupOperator.backup(context, safDoc)
            } catch (e: SecurityException) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, safDoc) }
                throw AutoBackupException(Failure.PERMISSION_LOST, e)
            } catch (e: BackupOperator.ExportTooLargeException) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, safDoc) }
                throw AutoBackupException(Failure.TOO_LARGE, e)
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, safDoc) }
                throw AutoBackupException(Failure.WRITE_FAILED, e)
            }
            runCatching { prune(context, treeUri) }
                .onFailure { Log.w(LOG_TAG, "auto backup prune failed: ${it.message}") }
        } else {
            writeDirect(context, treeUri, name)
        }
        return name
    }

    /** 直写兜底：SAF 建文档不可用时的文件系统写入 + 文件级清理。 */
    private suspend fun writeDirect(context: Context, treeUri: Uri, name: String) {
        val dir = resolvePrimaryDir(treeUri)
            ?: throw AutoBackupException(Failure.UNSUPPORTED_LOCATION)
        if (!Environment.isExternalStorageManager()) {
            throw AutoBackupException(Failure.NEED_ALL_FILES)
        }
        val target = File(dir, name)
        try {
            BackupOperator.backupToFile(context, target)
        } catch (e: SecurityException) {
            throw AutoBackupException(Failure.PERMISSION_LOST, e)
        } catch (e: BackupOperator.ExportTooLargeException) {
            target.delete()
            throw AutoBackupException(Failure.TOO_LARGE, e)
        } catch (e: Exception) {
            target.delete()
            throw AutoBackupException(Failure.WRITE_FAILED, e)
        }
        pruneDirect(dir)
    }

    /**
     * tree 文档 id（`primary:Hunoia/lunobackup`）→ 真实目录。
     * 只支持 primary 卷（内部存储）；外部存储 / SD 卡没有可靠的可写路径映射，返回 null。
     */
    @Suppress("DEPRECATION")
    private fun resolvePrimaryDir(treeUri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val volume = docId.substring(0, colon)
        val relative = docId.substring(colon + 1)
        if (volume != "primary" || relative.isBlank()) return null
        return File(Environment.getExternalStorageDirectory(), relative)
    }

    /** 文件级保留清理（直写路径）；只删我们命名规则内的旧文件。 */
    private fun pruneDirect(dir: File) {
        val files = dir.listFiles().orEmpty()
        val byName = files.associateBy { it.name }
        for (name in selectPrunable(files.map { it.name })) {
            runCatching { byName[name]?.delete() }
                .onFailure { Log.w(LOG_TAG, "auto backup delete $name failed: ${it.message}") }
        }
    }

    /**
     * 新建文档，返回可直接写入的 document URI。
     *
     * 先走标准形式（framework API，parent 走 call uri + extras）。部分 ROM 的
     * `DocumentsProvider.callUnchecked` 会做 `enforceTreeForExtraUris`，其中 `enforceTree`
     * 用 `getDocumentId` 校验 extras 里的 parent URI——tree URI 会被判为「Invalid URI」，
     * 裸 document URI 又不在 tree 授权内（SecurityException：MANAGE_DOCUMENTS）。
     * 这类 ROM 上改用**手动组装 call**：extras 只放 mime / displayName（**不放 parent**），
     * parent 继续由 call uri 携带——与 AOSP 语义等价，正常设备同样成立。
     * 返回的仍是可直接 `openOutputStream` 的 document URI（tree 授权覆盖其下 document URI）。
     */
    private fun createBackupDocument(context: Context, treeUri: Uri, name: String): Uri? {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val standard = runCatching {
            // createDocument 的 parent 必须是 document URI（tree URI 会被 provider 的
            // getDocumentId 判为 Invalid URI），这里从 tree 派生。
            DocumentsContract.createDocument(resolver, parent, ZIP_MIME, name)
        }.getOrElse { e ->
            Log.w(LOG_TAG, "createDocument(tree) failed: ${e::class.simpleName}: ${e.message}")
            null
        }
        if (standard != null) return standard

        val extras = Bundle().apply {
            // 常量在 framework 里是 hidden，字面量与 AOSP 一致：
            // DocumentsContract.EXTRA_MIME_TYPE / EXTRA_DISPLAY_NAME / EXTRA_PARENT_URI /
            // METHOD_CREATE_DOCUMENT。parent 必须给（call 语义要求），且得是 tree 派生的
            // document URI——裸 document URI 过不了 enforceTree 校验。
            putString("android.provider.extra.MIME_TYPE", ZIP_MIME)
            putString("android.provider.extra.DISPLAY_NAME", name)
            putParcelable("android.provider.extra.PARENT_URI", parent)
        }
        val out = runCatching {
            resolver.call(treeUri, "android.content.createDocument", null, extras)
        }.getOrElse { e ->
            Log.w(LOG_TAG, "createDocument(manual call) failed: ${e::class.simpleName}: ${e.message}")
            null
        } ?: return null
        val created = out.getParcelable("android.provider.extra.URI", Uri::class.java)
        if (created == null) Log.w(LOG_TAG, "createDocument(manual call) returned no EXTRA_URI: $out")
        return created
    }

    /** 只删超出保留份数的、匹配我们命名规则的旧备份。 */
    private fun prune(context: Context, treeUri: Uri) {
        val docs = childDocs(context, treeUri)
        val byName = docs.associateBy { it.name }
        for (name in selectPrunable(docs.map { it.name })) {
            val doc = byName[name] ?: continue
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc.documentUri(treeUri)) }
                .onFailure { Log.w(LOG_TAG, "auto backup delete $name failed: ${it.message}") }
        }
    }
}

/** 目录里的一份自动备份。 */
data class AutoBackupFile(
    /** 文件名（`luno-backup-*.zip`）。 */
    val name: String,
    /** tree 派生的 document URI（`content://…/tree/<id>/document/<docId>`）：可直接交给 precheck / restore。 */
    val uri: Uri,
    /** provider 报告的最后修改时刻（epoch ms）；拿不到为 0。 */
    val lastModifiedMs: Long,
)
