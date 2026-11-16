package hunoia.luno.shizuku

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import hunoia.luno.bridge.ui.assertNotMainThread
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 以 shell 身份直接注入 `MotionEvent`（`IInputManager.injectInputEvent`）。
 *
 * 与 [ShizukuInputInjection] 的区别：那个走 `input tap/swipe` shell 命令，一次往返要重新
 * fork 一个 `input` 进程，百毫秒级，且只有「完整手势」语义；这里注入单个 `MotionEvent`，
 * 能做到「按下去、等一会儿、取消」这种需要精确时序的组合，打断动作能在几十毫秒内完成。
 *
 * 为什么不需要 Shizuku UserService：本对象的特权身份来自 `ShizukuBinderWrapper`，与本包
 * 其它代理（`ShizukuManager` 的 `pm` / `am` / `activityTask`）完全同一机制。系统服务看到的
 * 调用者就是 Shizuku 自己（shell / root），`injectInputEvent` 的权限检查因此通过。
 *
 * 只提供平台原语：谁该注入、注入了算什么结果由调用方决定，Trigger 侧不得直接调用（架构 §2.3）。
 */
object ShizukuMotionInjector {

    /** `InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH`：等事件真正派发完再返回。 */
    private const val WAIT_FOR_FINISH = 2

    /**
     * 首次反射前安装隐藏 API 豁免（与 `BackgroundBlur` 同一手法）。
     *
     * `injectInputEvent` 是 `@hide`；`Method.invoke` 也会被隐藏 API 强制检查拦下，
     * 所以必须在第一次调用之前放开，而不是指望每次调用都绕过。
     */
    @Volatile
    private var exemptionState = 0

    private fun ensureExemptions(): Boolean {
        if (exemptionState == 1) return true
        if (exemptionState == -1) return false
        synchronized(this) {
            if (exemptionState != 0) return exemptionState == 1
            val ok = runCatching {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/hardware/input/IInputManager",
                    "Landroid/view/InputEvent",
                    "Landroid/view/MotionEvent",
                )
            }.onFailure {
                Log.w(LOG_TAG, "hidden api exemption failed type=${it.javaClass.simpleName}")
            }.getOrDefault(false)
            exemptionState = if (ok) 1 else -1
            if (!ok) Log.w(LOG_TAG, "hidden api exemption rejected, motion injection disabled")
            return ok
        }
    }

    private class InputApi {
        /** 接口本身：`injectInputEvent` 等实例方法挂在它上面。 */
        val type: Class<*> = Class.forName("android.hardware.input.IInputManager")

        /**
         * `asInterface` 在 `IInputManager$Stub` 上，**不在接口上**。
         *
         * 这里曾经把 `type` 传给 `HiddenApiBypass.invoke` 做 `asInterface`，于是构造阶段就抛
         * `NoSuchMethodException`，而失败点在 `inject` 自省**之前**，导致排查日志全都打不出来
         * （症状就是只看到一句 `interruptTouch failed type=NoSuchMethodException`）。
         */
        val manager: Any = HiddenApiBypass.invoke(
            Class.forName("android.hardware.input.IInputManager\$Stub"),
            null,
            "asInterface",
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input")),
        )

        /**
         * `injectInputEvent` 的真实 `Method`。
         *
         * 不能用 `HiddenApiBypass.invoke(Class, Object, name, args...)`：那个重载按
         * 「方法名 + 参数个数」匹配，`IInputManager` 上 `injectInputEvent` 有多个重载
         * （带 / 不带 `verify`、带 / 不带 `policyFlags`），匹配不是我们想要的那个就会抛
         * `NoSuchMethodException`。所以按**参数类型**精确挑，并且兼容不同版本的参数表。
         */
        val inject: Method = run {
            // 一次性把自省结果打出来：这是「注入失败」时唯一能自证挑没挑对方法的证据。
            // 注意不要在 throw 表达式上挂 .also{}——那时异常已经抛出，日志永远不执行。
            val declared = runCatching { HiddenApiBypass.getDeclaredMethods(type) }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "IInputManager methods unavailable type=${error.javaClass.simpleName}")
                    throw NoSuchMethodException("IInputManager unavailable: ${error.message}")
                }
            val candidates = declared.filterIsInstance<Method>().filter { it.name == "injectInputEvent" }
            Log.i(
                LOG_TAG,
                "injectInputEvent candidates=" + candidates.joinToString(" | ") { m ->
                    m.parameterTypes.joinToString(",", "(", ")") { it.simpleName }
                },
            )
            // 首选 (InputEvent, int[, int])：末尾的 int 是 INJECT_* mode。
            val picked = candidates.firstOrNull { method ->
                val params = method.parameterTypes
                params.isNotEmpty() &&
                    params[0] == InputEvent::class.java &&
                    params.drop(1).all { it == Int::class.javaPrimitiveType }
            } ?: candidates.firstOrNull()
            if (picked == null) {
                Log.w(LOG_TAG, "injectInputEvent not found; declared=${declared.size} methods")
                throw NoSuchMethodException("IInputManager.injectInputEvent not found")
            }
            Log.i(
                LOG_TAG,
                "injectInputEvent picked=" + picked.parameterTypes.joinToString(",", "(", ")") { it.simpleName },
            )
            picked
        }

        /** 我们实际要传的参数个数（不同版本末尾的 `policyFlags` / `verify` 有无不同）。 */
        val injectArity: Int = inject.parameterTypes.size
    }

    private val api: InputApi by lazy {
        check(ensureExemptions()) { "hidden api exemptions unavailable" }
        // 构造失败必须留下可读原因：失败点在自省之前时，下游只会看到一句
        // `interruptTouch failed type=NoSuchMethodException`，完全定位不到是哪一步。
        try {
            InputApi()
        } catch (error: Throwable) {
            Log.w(
                LOG_TAG,
                "motion injector init failed at=" +
                    (error.stackTrace.firstOrNull { it.className.contains("ShizukuMotionInjector") }?.methodName
                        ?: "unknown") +
                    " type=${error.javaClass.simpleName} msg=${error.message}",
            )
            throw error
        }
    }

    /**
     * 注入一个单指触摸事件。[action] 取 `MotionEvent.ACTION_DOWN` / `ACTION_CANCEL` 等。
     *
     * 每次调用都是一次独立手势（内部生成新的 `downTime`）。要在同一段手势里连发多个事件，
     * 用 [interruptTouch]——同一次手势的 `downTime` 必须一致，否则系统会当成两次按下。
     *
     * 阻塞调用，必须在 IO 线程执行（架构 §4.6）。返回值 = 系统是否接受该事件。
     */
    suspend fun injectTouch(
        action: Int,
        x: Int,
        y: Int,
        displayId: Int = 0,
    ): Boolean = withContext(Dispatchers.IO) {
        assertNotMainThread("shizuku.injectTouch")
        if (!canInject(x, y, displayId)) return@withContext false
        runCatching {
            inject(api, SystemClock.uptimeMillis(), action, x, y, displayId)
        }.getOrElse { error ->
            logFailure("injectTouch", error)
            false
        }
    }

    /**
     * 在 ([x], [y]) 处按下再取消，让目标窗口放弃它自己的手势。
     *
     * 用 CANCEL 而不是 UP：UP 会被当作真实点击（可能打开链接 / 按到按钮），CANCEL 才是
     * 「这次触摸作废」的语义。时序与「必须补 CANCEL」的约束在 [TouchInterruptionSequence] 里。
     *
     * **调用方必须先调本函数、拿到结果后再挂屏蔽触摸的遮罩窗**。`injectInputEvent` 走的是
     * 正常的窗口命中测试：遮罩窗（`TYPE_ACCESSIBILITY_OVERLAY`）z-order 在源应用之上，
     * 一旦挂上，注入的 DOWN 就落在遮罩自己身上，源应用什么也收不到。
     *
     * [expectedPackage] 非空时只在**能确定**前台已经不是它时才放弃（返回
     * [TouchInterruption.NotAttempted]）。读不到前台事实源时**不拦**：
     * 宁可让一次多余注入发生，也不要因为读不到信息白做一次接管。
     *
     * 阻塞调用，必须在 IO 线程执行（架构 §4.6）。
     */
    suspend fun interruptTouch(
        x: Int,
        y: Int,
        displayId: Int,
        expectedPackage: String? = null,
        isCurrent: () -> Boolean = { true },
    ): TouchInterruption = withContext(Dispatchers.IO) {
        assertNotMainThread("shizuku.interruptTouch")
        if (!canInject(x, y, displayId)) return@withContext TouchInterruption.NotAttempted
        if (!isCurrent()) return@withContext TouchInterruption.NotAttempted
        if (expectedPackage != null) {
            // 只有「读到了前台且不是它」才放弃；读不到（null）按未知处理，继续注入。
            val focused = ShizukuManager.focusedTaskPackage()
            if (focused != null && focused != expectedPackage) {
                return@withContext TouchInterruption.NotAttempted
            }
        }
        val downTime = SystemClock.uptimeMillis()
        try {
            TouchInterruptionSequence.run(
                isCurrent = isCurrent,
                down = { inject(api, downTime, MotionEvent.ACTION_DOWN, x, y, displayId) },
                cancel = { inject(api, downTime, MotionEvent.ACTION_CANCEL, x, y, displayId) },
                pause = { SystemClock.sleep(TouchInterruptionSequence.PauseMs) },
            )
        } catch (error: Exception) {
            logFailure("interruptTouch", error)
            TouchInterruption.Failed
        }
    }

    private fun canInject(x: Int, y: Int, displayId: Int): Boolean =
        displayId >= 0 && x >= 0 && y >= 0 &&
            ShizukuManager.canInjectInput() && ensureExemptions()
    private fun inject(
        injected: InputApi,
        downTime: Long,
        action: Int,
        x: Int,
        y: Int,
        displayId: Int,
    ): Boolean {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x.toFloat()
            this.y = y.toFloat()
            pressure = 1f
            size = 1f
        })
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            -1,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        return try {
            // setDisplayId 只有一个 (int) 重载，字符串形式在这里是安全的。
            HiddenApiBypass.invoke(InputEvent::class.java, event, "setDisplayId", displayId)
            // injectInputEvent 会按实际参数表补齐：不同版本末尾可能多一个 policyFlags。
            val args = ArrayList<Any?>(injected.injectArity).apply {
                add(event)
                add(WAIT_FOR_FINISH)
                // 只多出的那个位置（policyFlags）补 0。
                repeat((injected.injectArity - 2).coerceAtLeast(0)) { add(0) }
            }.toTypedArray()
            injected.inject.invoke(injected.manager, *args) as? Boolean ?: false
        } finally {
            event.recycle()
        }
    }

    /** 不打坐标、不打原始异常文案：注入通道的日志会进诊断导出。 */
    private fun logFailure(tag: String, error: Throwable) {
        val cause = (error as? InvocationTargetException)?.targetException ?: error
        Log.w(LOG_TAG, "$tag failed type=${cause.javaClass.simpleName}")
    }
}
