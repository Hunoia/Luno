package hunoia.luno

import android.content.pm.ApplicationInfo
import hunoia.luno.freeze.api.FreezeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeStateTest {

    @Test
    fun isSystemApp_null() {
        assertFalse(FreezeState.isSystemApp(null))
    }

    @Test
    fun isSystemApp_regular() {
        val ai = ApplicationInfo().apply { flags = 0 }
        assertFalse(FreezeState.isSystemApp(ai))
    }

    @Test
    fun isSystemApp_flagSystem() {
        val ai = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
        assertTrue(FreezeState.isSystemApp(ai))
    }

    @Test
    fun isSystemApp_flagUpdatedSystem() {
        val ai = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP }
        assertTrue(FreezeState.isSystemApp(ai))
    }

    @Test
    fun isSystemApp_bothFlags() {
        val ai = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP }
        assertTrue(FreezeState.isSystemApp(ai))
    }

    @Test
    fun isSystemApp_otherFlagsOnly() {
        val ai = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA }
        assertFalse(FreezeState.isSystemApp(ai))
    }
}
