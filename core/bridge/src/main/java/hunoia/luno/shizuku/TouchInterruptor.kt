package hunoia.luno.shizuku

/**
 * 一次触摸打断的结果。
 *
 * 打断是 **best-effort**：注入能不能被系统接受、能不能真的让底层应用放弃它自己那段手势，
 * 取决于 ROM 的输入管线。调用方必须按「可能失败」写后续逻辑，不能假设源应用一定被取消。
 */
enum class TouchInterruption {
    /** DOWN 与 CANCEL 都被系统接受：源应用这一段手势已被取消。 */
    Interrupted,

    /** 没有注入：能力不可用 / 守卫已失效 / 坐标非法。源应用仍在继续它自己的手势。 */
    NotAttempted,

    /** 注入了但至少一个事件没被接受：源应用状态未知，按「仍在继续」处理。 */
    Failed,
}

/**
 * 注入时序的唯一实现：先 DOWN、再 CANCEL，且「只要尝试过 DOWN 就必须尝试 CANCEL」。
 *
 * 为什么单独抽出来：这是整个打断能力里唯一不能出错的地方。漏掉 CANCEL 会让源应用
 * 永久停在「按下了但没抬起」——长按菜单、按压态、拖拽都会被挂住，比不打断更糟。
 * 放到纯函数里就能用 JVM 单测覆盖，不用碰真机与 Shizuku。
 */
internal object TouchInterruptionSequence {
    /**
     * DOWN 与 CANCEL 之间的停留。
     *
     * 太短（一帧以内）系统会把 DOWN/CANCEL 合进同一个批次，目标窗口可能只看到 CANCEL、
     * 甚至两者都丢——那是「注入了但没取消」的典型成因。取两帧（~33ms）已经足够可靠，
     * 又不会让整段接管变得可感知（整段目标 < 150ms）。
     */
    const val PauseMs = 32L

    /**
     * @param down 注入 ACTION_DOWN，返回系统是否接受
     * @param cancel 注入 ACTION_CANCEL，返回系统是否接受
     * @param pause DOWN 与 CANCEL 之间的等待
     */
    fun run(
        isCurrent: () -> Boolean,
        down: () -> Boolean,
        cancel: () -> Boolean,
        pause: () -> Unit = {},
    ): TouchInterruption {
        if (!isCurrent()) return TouchInterruption.NotAttempted
        var cancelAccepted = false
        // 只要 DOWN 发出去了，无论它是否被接受、无论上面是否抛异常，都必须尝试 CANCEL：
        // 被接受的 DOWN 会留下按下态，没被接受的 DOWN 也不该让我们跳过收尾。
        val downAccepted = try {
            down().also { if (it) pause() }
        } finally {
            cancelAccepted = cancel()
        }
        return if (downAccepted && cancelAccepted) {
            TouchInterruption.Interrupted
        } else {
            TouchInterruption.Failed
        }
    }
}

/**
 * 接管的两个步骤，以及它们**不可颠倒**的顺序。
 *
 * 为什么单独抽成函数：`injectInputEvent` 走正常窗口命中测试，而遮罩窗是
 * `TYPE_ACCESSIBILITY_OVERLAY`、z-order 在源应用之上。遮罩先挂的话，注入的 DOWN 会落在
 * 遮罩自己身上——源应用收不到、手势没被取消，遮罩又盖住了后续触摸，表现为
 * 「接管了但底下应用照样滚动」。这个顺序靠注释与记忆守不住（实际就写反过一次），
 * 所以放进纯函数用单测钉住。
 */
object TouchTakeoverOrder {

    /**
     * 先 [inject] 并在其完全结束后才 [attachShield]。
     *
     * @return 注入结果；[attachShield] 无论注入成败都会执行（失败时更需要拦住新手势）。
     */
    suspend fun run(
        inject: suspend () -> TouchInterruption,
        attachShield: suspend () -> Unit,
    ): TouchInterruption {
        // 必须先拿到注入结果再挂盾：任何情况下都不允许盾在注入完成前存在。
        val result = inject()
        attachShield()
        return result
    }
}
