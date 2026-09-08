package hunoia.luno.launcher.query

import android.content.Context
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppQuery
import hunoia.luno.quicklaunch.query.QuickAppLauncherBaseQuery
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAppLauncherBaseQueryTest {

    private val context = mockk<Context>(relaxed = true)

    @After
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun queryApps_returnsLauncherAppsOnly_andDoesNotMergeFrozenApps() {
        val launcherApps = listOf(
            AppInfo("pkg.one", "MainActivity", "One"),
            AppInfo("pkg.two", "SecondActivity", "Two"),
        )

        mockkObject(AppQuery)
        every {
            AppQuery.queryLauncherActivities(context = context, allowRepeatPackage = false)
        } returns launcherApps

        val result = QuickAppLauncherBaseQuery.queryApps(context)

        assertEquals(launcherApps, result.apps)
        assertEquals(emptySet<String>(), result.frozenPkgs)
    }
}
