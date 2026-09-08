package hunoia.luno

import hunoia.luno.freeze.api.FreezeAction
import hunoia.luno.quicklaunch.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeActionTest {

    @Test
    fun computeOneKeyTargetsInRange_emptyInput() {
        val result = FreezeAction.computeOneKeyTargetsInRange(
            apps = emptyList(),
            oneKeyPackageNames = setOf("com.example.app")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun computeOneKeyTargetsInRange_returnsMatchingTargets() {
        val apps = listOf(
            AppInfo("com.example.app1", "", "App 1"),
            AppInfo("com.example.app2", "", "App 2"),
            AppInfo("com.example.app3", "", "App 3"),
        )
        val result = FreezeAction.computeOneKeyTargetsInRange(
            apps = apps,
            oneKeyPackageNames = setOf("com.example.app1", "com.example.app3")
        )
        assertEquals(listOf("com.example.app1", "com.example.app3"), result)
    }

    @Test
    fun computeOneKeyTargetsInRange_deduplicatesByPackageName() {
        val apps = listOf(
            AppInfo("com.example.app1", "MainActivity", "App 1"),
            AppInfo("com.example.app1", "SecondActivity", "App 1"),
            AppInfo("com.example.app2", "", "App 2"),
        )
        val result = FreezeAction.computeOneKeyTargetsInRange(
            apps = apps,
            oneKeyPackageNames = setOf("com.example.app1", "com.example.app2")
        )
        assertEquals(listOf("com.example.app1", "com.example.app2"), result)
    }

    @Test
    fun computeOneKeyTargetsInRange_skipsNonTargets() {
        val apps = listOf(
            AppInfo("com.example.other", "", "Other App"),
            AppInfo("com.example.app1", "", "App 1"),
        )
        val result = FreezeAction.computeOneKeyTargetsInRange(
            apps = apps,
            oneKeyPackageNames = setOf("com.example.app1")
        )
        assertEquals(listOf("com.example.app1"), result)
    }
}
