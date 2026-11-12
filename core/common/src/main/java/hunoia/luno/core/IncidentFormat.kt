package hunoia.luno.core

/**
 * 故障记录的纯文本格式：便于人看，也便于按固定标记解析。
 *
 * 一条记录形如：
 * ```
 * ### luno_incident v1
 * epoch: 1760000000000
 * kind: CRASH
 * process: main
 * thread: main
 * tag: Crash
 * message: java.lang.IllegalStateException: boom
 * stack:
 *   at ...
 * ### end luno_incident
 * ```
 *
 * 字段值里的换行会转义成 `\n`，堆栈保持原样。这里不依赖 Android，方便单测。
 */
internal object IncidentFormat {

    const val HEADER = "### luno_incident v1"
    const val FOOTER = "### end luno_incident"

    private const val FIELD_EPOCH = "epoch: "
    private const val FIELD_KIND = "kind: "
    private const val FIELD_PROCESS = "process: "
    private const val FIELD_THREAD = "thread: "
    private const val FIELD_TAG = "tag: "
    private const val FIELD_MESSAGE = "message: "
    private const val FIELD_STACK = "stack:"

    fun format(incident: Incident): String {
        val builder = StringBuilder(256)
        builder.append(HEADER).append('\n')
        builder.append(FIELD_EPOCH).append(incident.timeMs).append('\n')
        builder.append(FIELD_KIND).append(incident.kind.name).append('\n')
        builder.append(FIELD_PROCESS).append(oneLine(incident.process)).append('\n')
        builder.append(FIELD_THREAD).append(oneLine(incident.thread)).append('\n')
        builder.append(FIELD_TAG).append(oneLine(incident.tag)).append('\n')
        builder.append(FIELD_MESSAGE).append(oneLine(incident.message)).append('\n')
        builder.append(FIELD_STACK).append('\n')
        if (incident.stackTrace.isNotBlank()) {
            builder.append(incident.stackTrace.trimEnd()).append('\n')
        }
        builder.append(FOOTER).append('\n')
        return builder.toString()
    }

    /** 解析全部完整记录（按文件中的先后顺序，旧 → 新）；残缺块直接跳过。 */
    fun parse(text: String): List<Incident> {
        val result = ArrayList<Incident>()
        var current: Incident? = null
        val stack = StringBuilder()
        var inStack = false

        fun flush() {
            val base = current ?: return
            result.add(base.copy(stackTrace = stack.toString().trimEnd()))
            current = null
            stack.clear()
            inStack = false
        }

        text.lineSequence().forEach { line ->
            when {
                line == HEADER -> {
                    flush()
                    current = Incident(
                        timeMs = 0L,
                        kind = IncidentKind.ERROR,
                        process = "",
                        thread = "",
                        tag = "",
                        message = "",
                        stackTrace = "",
                    )
                }
                line == FOOTER -> flush()
                inStack -> stack.append(line).append('\n')
                current == null -> Unit
                line.startsWith(FIELD_EPOCH) -> current = current?.copy(
                    timeMs = line.removePrefix(FIELD_EPOCH).trim().toLongOrNull() ?: 0L,
                )
                line.startsWith(FIELD_KIND) -> current = current?.copy(
                    kind = IncidentKind.fromName(line.removePrefix(FIELD_KIND)),
                )
                line.startsWith(FIELD_PROCESS) -> current = current?.copy(
                    process = line.removePrefix(FIELD_PROCESS),
                )
                line.startsWith(FIELD_THREAD) -> current = current?.copy(
                    thread = line.removePrefix(FIELD_THREAD),
                )
                line.startsWith(FIELD_TAG) -> current = current?.copy(
                    tag = line.removePrefix(FIELD_TAG),
                )
                line.startsWith(FIELD_MESSAGE) -> current = current?.copy(
                    message = line.removePrefix(FIELD_MESSAGE),
                )
                line == FIELD_STACK -> inStack = true
            }
        }
        // 没有 footer 的残缺块不输出，避免把轮转截断的半条记录当成有效故障。
        return result
    }

    /**
     * 通知去重指纹：同类、同 tag、同消息、同栈顶帧视为同一条故障。
     * 消息里的数字/地址可能每次不同，但栈顶通常稳定，足够区分。
     */
    fun fingerprint(incident: Incident): String {
        val topFrame = incident.stackTrace.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("at ") }
            .orEmpty()
        return buildString {
            append(incident.kind.name).append('|')
            append(incident.tag).append('|')
            append(incident.message).append('|')
            append(topFrame)
        }
    }

    private fun oneLine(value: String): String =
        value.replace("\r\n", "\n").replace('\n', ' ').replace('\r', ' ')
}
