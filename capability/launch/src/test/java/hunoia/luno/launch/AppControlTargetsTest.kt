package hunoia.luno.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class AppControlTargetsTest {

    private val oneKey = setOf("a", "b", "uninstalled")

    @Test
    fun `freeze targets only installed members`() {
        assertEquals(
            setOf("a", "b"),
            AppControlTargets.freezeTargets(oneKey, setOf("a", "b", "other")),
        )
    }

    @Test
    fun `unfreeze targets one-key list intersect disabled`() {
        assertEquals(
            setOf("b"),
            AppControlTargets.unfreezeTargets(oneKey, setOf("b", "manually-disabled")),
        )
    }

    @Test
    fun `empty one-key list has no targets`() {
        assertEquals(emptySet<String>(), AppControlTargets.freezeTargets(emptySet(), setOf("a")))
        assertEquals(emptySet<String>(), AppControlTargets.unfreezeTargets(emptySet(), setOf("a")))
    }
}
