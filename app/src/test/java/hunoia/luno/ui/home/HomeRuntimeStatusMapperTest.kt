package hunoia.luno.ui.home

import hunoia.luno.shizuku.ShizukuStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRuntimeStatusMapperTest {

    @Test
    fun `accessibility has highest priority`() {
        val status = map(
            accessibility = false,
            gesture = false,
            keepAlive = false,
            shizuku = ShizukuStatus(false, false, false, null),
        )

        assertEquals(HomePrimaryIssue.AccessibilityDisabled, status.primaryIssue)
        assertEquals(HomeRuntimeStatusLevel.Unavailable, status.level)
        assertEquals(HomeRuntimeAction.OpenAccessibility, status.primaryAction)
    }

    @Test
    fun `accessibility disabled takes priority while gesture switch remains enabled`() {
        val status = map(
            accessibility = false,
            gesture = true,
            keepAlive = true,
            shizuku = ShizukuStatus(true, true, true, 2000),
        )

        assertEquals(HomePrimaryIssue.AccessibilityDisabled, status.primaryIssue)
        assertEquals(HomeRuntimeStatusLevel.Unavailable, status.level)
        assertEquals(HomeRuntimeAction.OpenAccessibility, status.primaryAction)
        assertEquals(true, status.items.first { it.type == HomeStatusItemType.Gesture }.available)
    }

    @Test
    fun `gesture disabled follows accessibility priority`() {
        val status = map(
            accessibility = true,
            gesture = false,
            keepAlive = false,
            shizuku = ShizukuStatus(false, false, false, null),
        )

        assertEquals(HomePrimaryIssue.GestureDisabled, status.primaryIssue)
        assertEquals(HomeRuntimeAction.EnableGesture, status.primaryAction)
    }

    @Test
    fun `shizuku not installed maps to needs attention`() {
        val status = map(shizuku = ShizukuStatus(false, false, false, null))

        assertEquals(HomeShizukuUiStatus.NotInstalled, status.shizuku)
        assertEquals(HomePrimaryIssue.ShizukuNotInstalled, status.primaryIssue)
        assertEquals(HomeRuntimeStatusLevel.NeedsAttention, status.level)
        assertEquals(HomeRuntimeAction.RefreshStatus, status.primaryAction)
    }

    @Test
    fun `shizuku not running maps to needs attention`() {
        val status = map(shizuku = ShizukuStatus(true, false, false, null))

        assertEquals(HomeShizukuUiStatus.NotRunning, status.shizuku)
        assertEquals(HomePrimaryIssue.ShizukuNotRunning, status.primaryIssue)
    }

    @Test
    fun `shizuku not authorized maps to request permission`() {
        val status = map(shizuku = ShizukuStatus(true, true, false, 2000))

        assertEquals(HomeShizukuUiStatus.NotAuthorized, status.shizuku)
        assertEquals(HomePrimaryIssue.ShizukuNotAuthorized, status.primaryIssue)
        assertEquals(HomeRuntimeAction.RequestShizukuPermission, status.primaryAction)
    }

    @Test
    fun `keep alive follows shizuku priority`() {
        val status = map(
            keepAlive = false,
            shizuku = ShizukuStatus(true, true, true, 2000),
        )

        assertEquals(HomePrimaryIssue.KeepAliveDisabled, status.primaryIssue)
        assertEquals(HomeRuntimeAction.EnableKeepAlive, status.primaryAction)
    }

    @Test
    fun `all states ready maps to running`() {
        val status = map(shizuku = ShizukuStatus(true, true, true, 2000))

        assertEquals(HomePrimaryIssue.None, status.primaryIssue)
        assertEquals(HomeRuntimeStatusLevel.Running, status.level)
        assertEquals(HomeRuntimeAction.None, status.primaryAction)
    }

    private fun map(
        accessibility: Boolean = true,
        gesture: Boolean = true,
        keepAlive: Boolean = true,
        shizuku: ShizukuStatus,
    ): HomeRuntimeStatus {
        return HomeRuntimeStatusMapper.map(
            isAccessibilityEnabled = accessibility,
            isGestureSwitchEnabled = gesture,
            isKeepAliveEnabled = keepAlive,
            shizukuStatus = shizuku,
        )
    }
}
