package hunoia.luno.action.model

import hunoia.luno.action.definitions.ActionDefinitions
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ActionMappingRegistryTest {

    @Test
    fun `every built-in definition has a registered decoder`() {
        // 触发内置映射安装：取一个最简动作即可。
        ActionMapper.fromStored(StoredAction("app.popup"))
        val missing = ActionDefinitions.definitions
            .map { it.typeId }
            .filterNot { ActionMappingRegistry.contains(it) }
        assertTrue("缺少映射的动作：${missing.joinToString()}", missing.isEmpty())
    }

    @Test
    fun `duplicate typeId registration fails fast`() {
        ActionMapper.fromStored(StoredAction("app.popup"))
        try {
            ActionMappingRegistry.register("app.popup") { Action.None }
            fail("重复注册应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }

    @Test
    fun `duplicate action definition registration fails fast`() {
        ActionMapper.fromStored(StoredAction("app.popup"))
        val existing = ActionDefinitions.byTypeId("app.popup")
        assertTrue("内置定义 app.popup 应已注册", existing != null)
        if (existing == null) return
        try {
            hunoia.luno.action.definitions.ActionDefinitionRegistry.register(existing)
            fail("重复注册动作定义应直接失败")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }
    }

    @Test
    fun `unknown typeId maps to a controlled unsupported exception`() {
        try {
            ActionMapper.fromStored(StoredAction("no.such.action"))
            fail("未知 typeId 应抛 UnknownActionTypeException")
        } catch (expected: UnknownActionTypeException) {
            assertTrue(expected.typeId == "no.such.action")
        }
    }
}
