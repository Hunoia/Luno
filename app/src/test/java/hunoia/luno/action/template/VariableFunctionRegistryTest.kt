package hunoia.luno.action.template

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VariableFunctionRegistryTest {

    @Test
    fun `every editor token has a registered function`() {
        val missing = RuntimeVariables.all
            .map { it.token }
            .filterNot { VariableFunctionRegistry.contains(it) }
        assertTrue("缺少变量实现：${missing.joinToString()}", missing.isEmpty())
    }

    @Test
    fun `duplicate token fails fast`() {
        val token = RuntimeVariables.all.first().token
        try {
            VariableFunctionRegistry.register(token) { "" }
            fail("重复 token 应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }
}
