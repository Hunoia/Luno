package hunoia.luno.gesture.touchsource

import hunoia.luno.gesture.touch.GetEventDevice
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 触摸设备探测：Shizuku shell 跑 `getevent` 逐节点枚举 /dev/input，解析出触摸屏。
 *
 * 为什么不用无参 `getevent -lp`：它靠 inotify 扫 /dev/input 目录，部分 ROM
 * （实测 HyperOS 某版本）直接报 "could not add watch for /dev/input, Bad file
 * descriptor" 且一个设备都不吐。逐节点指定文件不触发目录 watch，输出格式相同。
 *
 * 为什么必须带 `-l`：[hunoia.luno.gesture.touch.GetEventParser] 的 ABS 段解析只认
 * 带标签的 `ABS_MT_POSITION_X : value ...`；不带 `-l` 输出的是十六进制码（0035），
 * range 会解析为空，坐标映射永远失败（表现为流在跑但一帧都进不了长按检测）。
 *
 * 设置页「识屏诊断」与 [ShizukuTouchInputSource] 的扫描共用本实现，避免两份口径。
 */
object TouchDeviceScanner {

    const val PROBE_COMMAND = "for f in /dev/input/event*; do getevent -pl \"\$f\" 2>/dev/null; done"

    /** [usable] = 找到了可用的触摸屏（有 X/Y range 且具备多点/直接/按键任一特征）。 */
    data class Report(
        val shellOk: Boolean,
        val devices: List<GetEventDevice>,
        val touchscreen: GetEventDevice?,
    ) {
        val usable: Boolean get() = touchscreen != null
    }

    /** 纯解析，便于 JVM 单测覆盖真实设备输出。 */
    fun parse(shellOk: Boolean, output: String): Report {
        if (!shellOk) return Report(shellOk = false, devices = emptyList(), touchscreen = null)
        val devices = hunoia.luno.gesture.touch.GetEventParser.parseDevices(output)
        return Report(
            shellOk = true,
            devices = devices,
            touchscreen = devices.filter { it.isTouchscreen }.maxByOrNull { it.score },
        )
    }

    suspend fun scan(): Report = withContext(Dispatchers.IO) {
        val result = ShizukuManager.executeShell(PROBE_COMMAND, maxChars = 256 * 1024)
        parse(result.isSuccess, result.stdout)
    }
}
