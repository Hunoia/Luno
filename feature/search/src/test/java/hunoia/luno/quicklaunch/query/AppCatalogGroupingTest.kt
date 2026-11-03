package hunoia.luno.quicklaunch.query

import hunoia.luno.core.domain.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** 分身行必须紧跟主应用（标题相同，乱序会看起来像重复项）。 */
class AppCatalogGroupingTest {

    @Test
    fun `clone rows follow their primary app`() {
        val apps = listOf(
            app("com.a", "A"),
            app("com.b", "B"),
            app("com.a", "A", userId = 999),
            app("com.c", "C"),
        )

        val grouped = AppCatalog.groupClonesAfterPrimary(apps)

        assertEquals(
            listOf("A@0", "A@999", "B@0", "C@0"),
            grouped.map { "${it.label}@${it.userId}" },
        )
    }

    @Test
    fun `orphan clone is appended when primary is missing`() {
        val apps = listOf(
            app("com.a", "A"),
            app("com.b", "B", userId = 999),
        )

        val grouped = AppCatalog.groupClonesAfterPrimary(apps)

        assertEquals(
            listOf("A@0", "B@999"),
            grouped.map { "${it.label}@${it.userId}" },
        )
    }

    private fun app(pkg: String, label: String, userId: Int = 0) = AppInfo(
        packageName = pkg,
        className = "$pkg.Main",
        label = label,
        userId = userId,
    )
}
