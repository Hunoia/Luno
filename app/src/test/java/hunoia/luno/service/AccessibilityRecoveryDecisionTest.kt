package hunoia.luno.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AccessibilityRecovery.decideRecovery] 的判据。
 *
 * 核心语义：总闸开 + 有写权限，按「token 在不在 / 实例在不在 / 最近有没有崩」决定动作。
 * 判不准（实例未知）一律不动——乱重绑会打断正在使用的手势。
 */
class AccessibilityRecoveryDecisionTest {

    private fun decide(
        masterSwitchOn: Boolean = true,
        tokenPresent: Boolean = true,
        instanceOnline: Boolean? = false,
        recentCrash: Boolean = false,
        hasWriteSecureSettings: Boolean = true,
    ): AccessibilityRecovery.RecoveryAction = AccessibilityRecovery.decideRecovery(
        masterSwitchOn = masterSwitchOn,
        tokenPresent = tokenPresent,
        instanceOnline = instanceOnline,
        recentCrash = recentCrash,
        hasWriteSecureSettings = hasWriteSecureSettings,
    )

    @Test
    fun `被划掉后 token 被摘走时补回`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.Reattach,
            decide(tokenPresent = false, instanceOnline = false),
        )
    }

    @Test
    fun `token 被摘走时不看实例状态也要补回`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.Reattach,
            decide(tokenPresent = false, instanceOnline = true),
        )
    }

    @Test
    fun `token 在位但实例没了要强制重绑`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.ForceRebind,
            decide(tokenPresent = true, instanceOnline = false),
        )
    }

    @Test
    fun `实例在线时不动`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.None,
            decide(tokenPresent = true, instanceOnline = true),
        )
    }

    @Test
    fun `实例状态判不准时宁可不动`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.None,
            decide(tokenPresent = true, instanceOnline = null),
        )
    }

    @Test
    fun `总闸关着不自愈`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.None,
            decide(masterSwitchOn = false, tokenPresent = false, instanceOnline = false),
        )
    }

    @Test
    fun `没有写权限不自愈`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.None,
            decide(hasWriteSecureSettings = false, tokenPresent = false, instanceOnline = false),
        )
    }

    @Test
    fun `最近崩溃强制重绑且不要求 token 在位`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.ForceRebind,
            decide(recentCrash = true, tokenPresent = false, instanceOnline = null),
        )
    }

    @Test
    fun `既没崩也没掉线时不动`() {
        assertEquals(
            AccessibilityRecovery.RecoveryAction.None,
            decide(tokenPresent = true, instanceOnline = true, recentCrash = false),
        )
    }
}