package hunoia.luno.link

import hunoia.luno.runtime.recognition.LinkUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPresetsTest {

    @Test
    fun `presets are well formed and unique`() {
        val all = LinkPresets.all
        assertTrue(all.isNotEmpty())
        assertTrue(
            all.all {
                it.pattern.isNotBlank() && it.name.isNotBlank() &&
                    (it.targetPackages.any { pkg -> pkg.contains('.') } || it.viaBrowser)
            },
        )
        assertEquals(all.size, all.map { it.pattern.lowercase() }.distinct().size)
        assertTrue(all.all { LinkUrls.isDispatchable("https://${it.pattern}/x") })
    }
}
