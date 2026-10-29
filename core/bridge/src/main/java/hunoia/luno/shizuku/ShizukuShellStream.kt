package hunoia.luno.shizuku

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.Closeable

/**
 * 长生命周期的 Shizuku shell 子进程。
 *
 * [ShizukuManager.executeShell] 是「跑完即取结果」的一次性接口；监听输入设备这类
 * 永不退出的命令需要逐行读 stdout，因此单独收在这里。权限/进程存活仍以
 * [ShizukuManager.currentStatus] 为准，不另建一套事实源。
 */
object ShizukuShellStream {

    sealed interface OpenResult {
        data class Success(val session: ShizukuShellSession) : OpenResult
        data class Failure(val reason: String) : OpenResult
    }

    suspend fun open(command: String): OpenResult = withContext(Dispatchers.IO) {
        if (command.isBlank()) {
            return@withContext OpenResult.Failure("Command is blank")
        }
        val shell = if (runCatching { Shizuku.getUid() }.getOrDefault(-1) == 0) "su" else "sh"
        openProcess(
            argv = arrayOf(shell),
            stdin = command + "\nexit\n",
        )
    }

    /**
     * 直接以 argv 启动，不经过 shell。监听类命令应走这里：destroy() 杀的是目标进程本身，
     * 不会出现 `sh` 被杀、`getevent` 被 init 收养继续读设备的残留。
     */
    suspend fun openArgv(vararg argv: String): OpenResult = withContext(Dispatchers.IO) {
        if (argv.isEmpty() || argv.any { it.isBlank() }) {
            return@withContext OpenResult.Failure("Empty argv")
        }
        openProcess(argv = argv, stdin = null)
    }

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
                ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream).use { input ->
                    if (stdin != null) {
                        // 先写命令再写 exit：shell 会等命令退出后才读到 exit，
                        // 命令在流存活期间不会被 shell 的退出打断。
                        input.write(stdin.toByteArray())
                        input.flush()
                    }
                }
                OpenResult.Success(
                    ShizukuShellSession(
                        process = process,
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
 */
class ShizukuShellSession internal constructor(
    private val process: IRemoteProcess,
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

    override fun close() {
        runCatching { process.destroy() }
        stderrJob.cancel()
        stderrScope.cancel()
        runCatching { stdout.close() }
        runCatching { stderr.close() }
    }

    private companion object {
        const val STDERR_LIMIT = 8 * 1024
    }
}
