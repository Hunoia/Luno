package hunoia.luno

import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppSearch.key
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {

    @Test
    fun key_standardFormat() {
        val app = AppInfo("com.example.app", "MainActivity", "My App")
        assertEquals("com.example.app/MainActivity", app.key())
    }

    @Test
    fun key_emptyClassName() {
        val app = AppInfo("com.example.app", "", "My App")
        assertEquals("com.example.app/", app.key())
    }

    @Test
    fun key_emptyPackageName() {
        val app = AppInfo("", "MainActivity", "")
        assertEquals("/MainActivity", app.key())
    }

    @Test
    fun key_bothEmpty() {
        val app = AppInfo("", "", "")
        assertEquals("/", app.key())
    }
}
