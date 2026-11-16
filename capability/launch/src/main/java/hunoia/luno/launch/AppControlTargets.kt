package hunoia.luno.launch

/**
 * 一键动作的目标集合（纯函数，便于单测）。
 *
 * - 冻结：名单 ∩ 已安装 − 已冻结（Luno disabled 或系统 suspend 都不重复冻结）；
 * - 解冻：名单 ∩ 当前 disabled（suspend 不在 disabled 集合里）。
 *
 * 不误伤系统里手动禁用的应用：解冻永远由名单收窄。
 */
object AppControlTargets {

    fun freezeTargets(
        oneKey: Set<String>,
        installed: Set<String>,
        frozen: Set<String>,
    ): Set<String> = oneKey.intersect(installed).subtract(frozen)

    fun unfreezeTargets(oneKey: Set<String>, disabled: Set<String>): Set<String> =
        oneKey.intersect(disabled)
}
