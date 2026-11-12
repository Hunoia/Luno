package hunoia.luno.quickaccess.launch

import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.shizuku.ShizukuManager
import java.util.Locale

/**
 * MIUI/HyperOS freeform 合成缩放系数（可见尺寸 / WindowManager 帧尺寸）。
 *
 * 系统在合成层把 freeform 窗口再缩一次：WindowManager 里仍是我们请求的 bounds，
 * 屏幕上看到的却小一圈。补偿启动时要把宽高乘以 1/scale 抵消，所以这个 coeff
 * 必须取**系统实际值**，不能写死 —— 不同版本 / 横竖屏会变（实测竖屏 1.4285713，
 * 横屏 1/0.56≈1.7857）。
 *
 * 来源：`dumpsys window windows` 的 `Display #` 可见窗口列表，每个窗口带
 * `transform=Matrix{[<sx>, ...]}`。普通窗口 sx=1.0，只有 freeform 小窗被放大到
 * 1.05..3.0 区间，因此按区间过滤即可。
 *
 * 缓存只在进程内、按方向各存一份：转屏后第一次小窗仍用旧值/常量，启动完成后
 * 由 [Launcher] 异步 [learn] 刷新，下一次触发即生效。
 *
 * 调试覆盖：
 * ```
 * adb shell setprop debug.luno.freeform_scale_portrait 1.4285714
 * adb shell setprop debug.luno.freeform_scale_landscape 1.7857143
 * ```
 * 属性为空 / 非法时忽略。
 */
internal object FreeformScale {

    private const val DEFAULT_PORTRAIT = 1f / 0.7f
    private const val DEFAULT_LANDSCAPE = 1f / 0.56f

    private const val MIN_VALID = 1.05f
    private const val MAX_VALID = 3.0f

    @Volatile
    private var portraitScale: Float? = null

    @Volatile
    private var landscapeScale: Float? = null

    /** 当前应使用的合成系数：调试属性 > 系统采样缓存 > 常量兜底。 */
    fun current(landscape: Boolean): Float {
        override(landscape)?.let { return it }
        cached(landscape)?.let { return it }
        return default(landscape)
    }

    fun cached(landscape: Boolean): Float? =
        if (landscape) landscapeScale else portraitScale

    /**
     * 从系统读取当前 freeform 缩放系数并写入缓存。返回读到的值（读不到为 null）。
     *
     * [expectedPackage] 是刚打开的应用；优先取它的窗口，找不到再退回任意
     * 处于 freeform 区间比例的窗口（同一时刻通常只有一个小窗）。
     */
    suspend fun learn(landscape: Boolean, expectedPackage: String?): Float? {
        if (!ShizukuManager.currentStatus().isReady) return null
        val output = runCatching {
            ShizukuManager.executeShell(
                "dumpsys window windows | grep '^  Display #'",
                maxChars = 64_000,
            ).stdout
        }.getOrNull().orEmpty()
        val scale = parse(output, expectedPackage) ?: run {
            Log.i(LOG_TAG, "freeformScale not-found landscape=$landscape pkg=$expectedPackage")
            return null
        }
        if (landscape) landscapeScale = scale else portraitScale = scale
        Log.i(
            LOG_TAG,
            "freeformScale learned landscape=$landscape comp=$scale src=system pkg=$expectedPackage",
        )
        return scale
    }

    /** 解析 `Display #` 行里的窗口 transform。包名优先，其次任意合法值。 */
    internal fun parse(output: String, expectedPackage: String?): Float? {
        val entries = WINDOW_REGEX.findAll(output).mapNotNull { match ->
            val pkg = match.groupValues[1]
            // 竖屏是 [sx,0][0,sy]，横屏是旋转矩阵 [0,sy][-sx,0]；
            // 取前四项的最大绝对值，横竖屏都能拿到同一个缩放系数。
            val sx = listOf(2, 3, 4, 5)
                .mapNotNull { match.groupValues[it].toFloatOrNull() }
                .maxOfOrNull { kotlin.math.abs(it) }
                ?: return@mapNotNull null
            if (sx < MIN_VALID || sx > MAX_VALID) return@mapNotNull null
            pkg to sx
        }.toList()
        if (entries.isEmpty()) return null
        if (!expectedPackage.isNullOrBlank()) {
            entries.firstOrNull { it.first == expectedPackage }?.let { return it.second }
        }
        return entries.first().second
    }

    private fun override(landscape: Boolean): Float? {
        val key = if (landscape) {
            "debug.luno.freeform_scale_landscape"
        } else {
            "debug.luno.freeform_scale_portrait"
        }
        val value = systemProperty(key)?.toFloatOrNull() ?: return null
        return value.takeIf { it >= MIN_VALID && it <= MAX_VALID }
    }

    private fun default(landscape: Boolean): Float =
        if (landscape) DEFAULT_LANDSCAPE else DEFAULT_PORTRAIT

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()?.lowercase(Locale.ROOT)
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * 一条可见窗口记录：
     * ```
     * a381df8 com.foo/com.foo.MainActivity, frame=[Rect(...)], touchableRegion=...,
     * scaleFactor=1.0, transform=Matrix{[1.4285713, 0.0, ...]}
     * ```
     * `frame` 里可能有负号；横屏时 transform 是旋转矩阵，缩放值不在第一项，因此
     * 捕获矩阵前四项（m00/m01/m10/m11）再取绝对值最大者。
     */
    private val WINDOW_REGEX = Regex(
        """\S+ ([\w.]+)/[\w.$]+, frame=\[Rect\([^)]*\)\], touchableRegion=\S+, """ +
            """scaleFactor=[^,]+, transform=Matrix\{\[([-\d.]+),\s*([-\d.]+),\s*[-\d.]+\]""" +
            """\[([-\d.]+),\s*([-\d.]+),""",
    )
}
