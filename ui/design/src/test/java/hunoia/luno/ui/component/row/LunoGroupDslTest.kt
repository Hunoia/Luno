package hunoia.luno.ui.component.row

import org.junit.Assert.assertEquals
import org.junit.Test

class LunoGroupDslTest {

    @Test
    fun `conditional rows participate in the group count`() {
        val scope = LunoGroupScope()
        var showAdvanced = false
        scope.row(title = "always", onClick = {})
        if (showAdvanced) scope.row(title = "advanced", onClick = {})
        assertEquals(1, scope.rows.size)

        showAdvanced = true
        val next = LunoGroupScope()
        next.row(title = "always", onClick = {})
        if (showAdvanced) next.row(title = "advanced", onClick = {})
        assertEquals(2, next.rows.size)
    }

    @Test
    fun `keys are preserved for row identity`() {
        val scope = LunoGroupScope()
        scope.row(title = "a", key = "row-a", onClick = {})
        scope.switchRow(
            title = "b",
            checked = false,
            onCheckedChange = {},
            key = "row-b",
        )
        assertEquals(listOf("row-a", "row-b"), scope.rows.map { it.key })
    }
}