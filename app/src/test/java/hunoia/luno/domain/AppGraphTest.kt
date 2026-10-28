package hunoia.luno.domain

import android.content.Context
import hunoia.luno.domain.system.HomePermissionState
import hunoia.luno.domain.system.SystemFacade
import org.junit.Assert.assertSame
import org.junit.Test

class AppGraphTest {

    private class FakeSystem : SystemFacade {
        override suspend fun setKeepAliveEnabled(
            context: Context,
            enabled: Boolean,
            onPermissionRequired: (Int) -> Unit,
        ): Boolean = true

        override suspend fun enableAccessibility(context: Context): Boolean = true

        override fun setFloatBallEnabled(context: Context, enabled: Boolean) = Unit

        override fun refreshFloatBall(context: Context) = Unit

        override suspend fun loadHomePermissionState(context: Context): HomePermissionState =
            HomePermissionState(
                isGestureEnabled = false,
                isAccessibilityEnabled = false,
                isKeepAliveEnabled = false,
            )
    }

    @Test
    fun `overridden facade is used and can be reset`() {
        val fake = FakeSystem()
        AppGraph.overrideSystem(fake)
        try {
            assertSame(fake, AppGraph.system)
        } finally {
            AppGraph.overrideSystem(null)
        }
    }
}
