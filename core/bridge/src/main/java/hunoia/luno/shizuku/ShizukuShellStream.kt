package hunoia.luno.shizuku

import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException

/**
 * 长生命周期的 Shizuku shell 子进程。
 *
 * [ShizukuManager.executeShell] 是「跑完即取结果」的一次性接口；监听输入设备这类
 * 永不退出的命令需要逐行读 stdout，因此单独收在这里。权限/进程存活仍以
 * [ShizukuManager.currentStatus] 为准，不另建一套事实源。
 */
object ShizukuShellStream {

    /**
     * 拼 shell 脚本用的美元符。
     *
     * 脚本里到处是 `$@` / `$!` / `$?`，直接写进 Kotlin 字符串会被当成模板解析；
     * 用常量拼装既避开这个坑，也不用满篇大括号转义。
     */
    private const val DOLLAR = "$"

    sealed interface OpenResult {
        data class Success(val session: ShizukuShellSession) : OpenResult
        data class Failure(val reason: String) : OpenResult
    }

    /**
     * 以 argv 启动命令（监听类命令走这里：`getevent` / `screencap` / `content read`）。
     *
     * 外面套一层 `sh`，目的不是「跑命令」而是**挂租约**：子进程由 Shizuku 服务端 fork，
     * 调用方死了它并不知道。没有租约时，`:service` 被 LMK 杀掉或被 force-stop（SIGKILL，
     * `trap` 不执行、`close()` 也不会跑）之后，远端 `getevent` 会永久挂在 `do_sys_poll`
     * 上（实测积累到 86 个，每个都占着 `/dev/input/eventX`）。
     *
     * 关键事实：force-stop 只杀 App 进程，**这层包装活在设备侧、活得好好的**。
     * 所以包装自己能轮询 `/proc/<调用方 pid>`，一旦对方消失就收掉子进程。
     */
    suspend fun openArgv(vararg argv: String): OpenResult = withContext(Dispatchers.IO) {
        if (argv.isEmpty() || argv.any { it.isBlank() }) {
            return@withContext OpenResult.Failure("Empty argv")
        }
        // $0 = 调用方 pid（包装靠 /proc/<pid> 判断调用方是否还活着）。
        openProcess(
            argv = arrayOf("sh", "-c", LEASED_WRAPPER, Process.myPid().toString(), *argv),
            stdin = null,
        )
    }

    /**
     * 租约包装：`$@` 是目标命令，`$0` 是调用方 pid。
     *
     * **只靠 `/proc/<pid>` 轮询收尾，不用 stdin**。曾经的 stdin-EOF 看门狗是个陷阱：
     * `getevent` 这类监听命令根本不需要 stdin（`openProcess` 传 `stdin = null`，写端立刻
     * 关闭），看门狗一启动就读到 EOF，于是把刚起的子进程当场杀掉 —— 表现是触摸流
     * 每 3 秒重连一次、识别区永远不触发。
     *
     * `/proc/<pid>` 判据同时覆盖两种死法：正常 `close()`（`destroy` 掉包装，包装的
     * `wait` 返回后一并收尾）与调用方暴毙（force-stop / LMK 是 SIGKILL，但包装活在
     * 设备侧、杀不到，它会自己发现 pid 消失并杀掉子进程）。
     */
    private val LEASED_WRAPPER: String = listOf(
        "cleanup() { kill \"${DOLLAR}child\" 2>/dev/null; }",
        "trap cleanup EXIT TERM INT HUP",
        "\"${DOLLAR}@\" &",
        "child=${DOLLAR}!",
        // 调用方消失（含被 SIGKILL）就收掉子进程；2s 一次，稳态开销可以忽略。
        "( while [ -d \"/proc/${DOLLAR}0\" ]; do sleep 2; done; kill -9 \"${DOLLAR}child\" 2>/dev/null ) " +
            ">/dev/null 2>&1 &",
        // 看门狗 pid：子进程正常结束后必须收掉它，否则包装永不退出、stdout 永不到 EOF。
        "watcher=${DOLLAR}!",
        "wait \"${DOLLAR}child\"",
        "status=${DOLLAR}?",
        // 子进程已经结束，看门狗只剩「等调用方死亡」这一件事，与本次收口无关：
        // 不杀它，包装就永远卡在 wait 上（实测 screencap -p 一次成功写完后，管道因
        // 包装未退出而永远等不到 EOF，readBytes 挂死；getevent 是长连接不受影响）。
        "kill \"${DOLLAR}watcher\" 2>/dev/null",
        "exit ${DOLLAR}status",
    ).joinToString("\n")

    private fun openProcess(argv: Array<out String>, stdin: String?): OpenResult {
        val status = ShizukuManager.currentStatus()
        if (!status.binderAlive) {
            return OpenResult.Failure("Shizuku binder unavailable")
        }
        if (!status.permissionGranted) {
            return OpenResult.Failure("Shizuku permission denied")
        }
        return runCatching {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val process = service.newProcess(arrayOf(*argv), null, null)
            try {
                // 写端交给会话持有，**不能立刻关**：它同时是租约凭据（看门狗的判据）
                // 和防 GC 提前回收的锚点。
                val lease = ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream)
                if (stdin != null) {
                    lease.write(stdin.toByteArray())
                    lease.flush()
                }
                OpenResult.Success(
                    ShizukuShellSession(
                        process = process,
                        lease = lease,
                        stdout = process.inputStream,
                        stderr = process.errorStream,
                    )
                )
            } catch (t: Throwable) {
                runCatching { process.destroy() }
                throw t
            }
        }.getOrElse { t ->
            OpenResult.Failure(t.message ?: t.javaClass.simpleName)
        }
    }
}

/**
 * 单消费者 shell 会话：[lines] 只能被 collect 一次，关闭后进程一并销毁。
 * stderr 在独立协程里持续排空，避免子进程写满管道阻塞。
 *
 * @param lease 会话 stdin 的写端（仅 [ShizukuShellStream.openArgv] 之前的注入式命令会用）。
 *   它不再是「调用方活着」的凭据——那个判据在远端包装里用 `/proc/<pid>` 轮询实现。
 */
class ShizukuShellSession internal constructor(
    private val process: IRemoteProcess,
    private val lease: java.io.OutputStream,
    private val stdout: ParcelFileDescriptor,
    private val stderr: ParcelFileDescriptor,
) : Closeable {

    private val stderrBuffer = StringBuilder()
    private val stderrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stderrJob = stderrScope.launch {
        runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(stderr).bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    synchronized(stderrBuffer) {
                        if (stderrBuffer.length < STDERR_LIMIT) {
                            if (stderrBuffer.isNotEmpty()) stderrBuffer.append('\n')
                            stderrBuffer.append(line)
                        }
                    }
                }
            }
        }
    }

    val stderrText: String
        get() = synchronized(stderrBuffer) { stderrBuffer.toString() }

    /** stdout 的逐行流；流结束时子进程已经退出或 stdout 已关闭。 */
    val lines: Flow<String> = flow {
        val reader = ParcelFileDescriptor.AutoCloseInputStream(stdout).bufferedReader()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                emit(line)
            }
        } finally {
            runCatching { reader.close() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 一次性读取二进制 stdout 到 EOF（`screencap -p` 这类输出必须是字节，不能走 [lines]）。
     * 与 [lines] 相同是单消费者：读完后 stdout 即关闭，不能再 collect。
     *
     * 必须带超时：`write()` 卡在满管道上、远端进程不退出时，这条阻塞读**协程取消不掉**
     * （`InputStream.read` 不响应中断），只能关掉 stdout 让它抛错。没有这层保护，
     * 一次卡住的 `screencap` 会永久占着一根 IO 线程，截图链也就再也不会返回。
     */
    suspend fun readBytes(
        maxBytes: Int = 64 * 1024 * 1024,
        timeoutMs: Long = READ_TIMEOUT_MS,
    ): ByteArray = withContext(Dispatchers.IO) {
        val reader = async { drain(maxBytes) }
        val bytes = try {
            withTimeoutOrNull(timeoutMs) { reader.await() }
        } catch (cancel: CancellationException) {
            // 调用方取消（宿主总预算到点 / 用户退出）：阻塞中的 read 不会响应取消，
            // 必须自己关掉 stdout，否则这根 IO 线程和远端 `screencap` 会永久挂着。
            close()
            reader.cancel()
            throw cancel
        }
        if (bytes != null) return@withContext bytes
        // 先关 stdout 解阻塞（read 会立刻抛错返回），再收协程与远端进程。
        close()
        reader.cancel()
        throw IOException("stdout read timed out after ${timeoutMs}ms")
    }

    private fun drain(maxBytes: Int): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(stdout).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxBytes) {
                    throw IOException("stdout exceeded $maxBytes bytes")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }

    override fun close() {
        // 关 stdin 写端（若曾用过）再 destroy 包装：包装的 `wait` 会返回，
        // 它拉起的子进程随 EXIT trap 一起收掉。调用方暴毙走另一条路——
        // 包装自己轮询 /proc/<pid>，见 [ShizukuShellStream.openArgv] 的注释。
        runCatching { lease.close() }
        runCatching { process.destroy() }
        stderrJob.cancel()
        stderrScope.cancel()
        runCatching { stdout.close() }
        runCatching { stderr.close() }
    }

    private companion object {
        const val STDERR_LIMIT = 8 * 1024

        /** 二进制 stdout 的读取上限；`screencap -p` 全屏 PNG 一般 1–4MB。 */
        const val READ_TIMEOUT_MS = 5_000L
    }
}
