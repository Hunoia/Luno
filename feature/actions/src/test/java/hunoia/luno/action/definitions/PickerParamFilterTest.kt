package hunoia.luno.action.definitions

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选择页 / 动作库的「参数口径」门禁（architecture §14.1）。
 *
 * 规则：**选择页只列无参 / 参数有限（全可选）的动作**；需要自由填写参数的（必填参数）
 * 只能进动作库——库新建页能填任意参数，建好再以库引用选进来。
 * 这条口径以前只写在 `pickerDefinitions` 的实现里，双向不对称没人拦得住，这里钉住两端：
 * 必填参数的动作既不能出现在选择页，也不能从动作库里消失。
 */
class PickerParamFilterTest {

    private val pickerTypeIds = ActionDefinitions.pickerDefinitions().map { it.typeId }.toSet()
    private val libraryTypeIds = ActionDefinitions.libraryDefinitions().map { it.typeId }.toSet()

    @Test
    fun `选择页不列任何带必填参数的动作`() {
        val leaked = ActionDefinitions.pickerDefinitions()
            .filter { def -> def.parameters.any { it.required } }
            .map { it.typeId }
        assertTrue("必填参数的动作不能出现在选择页：$leaked", leaked.isEmpty())
    }

    @Test
    fun `带必填参数的动作必须能从动作库新建`() {
        val freeForm = ActionDefinitions.libraryDefinitions()
            .filter { def -> def.parameters.any { it.required } }
            .map { it.typeId }
        // 现在只有三个：启动应用 / 应用详情 / 自定义命令。数字变化说明定义表动了，回头确认口径。
        assertTrue("带必填参数的动作应可从库新建，实际：$freeForm", freeForm.isNotEmpty())
        freeForm.forEach { typeId ->
            assertTrue("$typeId 必须在库新建列表里", typeId in libraryTypeIds)
        }
    }

    @Test
    fun `动态具名行类型不出现在选择页与动作库`() {
        // 面板/子手势/无动作/快捷方式的行由选择页合成，不能作为裸类型出现。
        val dynamic = listOf(
            hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID,
            hunoia.luno.core.domain.GRID_PANEL_TYPE_ID,
            hunoia.luno.action.model.Action.NONE_TYPE_ID,
            "internal.subGesture",
            "app.launchShortcut",
        )
        dynamic.forEach { typeId ->
            assertTrue("$typeId 不能在选择页裸列", typeId !in pickerTypeIds)
            assertTrue("$typeId 不能从动作库新建", typeId !in libraryTypeIds)
        }
    }
}
