package hunoia.luno.actions.library

import androidx.annotation.StringRes
import hunoia.luno.actions.R
import hunoia.luno.core.domain.AppLaunchAction

/**
 * 动作库自己的三类：与选择页的语义分类解耦。
 *
 * 动作库只收「有必填参数、需要先填再存」的动作，当前恰好三类；
 * 主页动作库与动作库页共用本分类，选择页仍用 `ActionCategory`。
 */
enum class LibraryCategory(@get:StringRes val displayNameRes: Int) {
    APP(R.string.action_library_category_app),
    LINK(R.string.action_library_category_link),
    CUSTOM(R.string.action_library_category_custom),
}

/** 动作库可新建动作 → 动作库分类。新增库类型时必须在这里登记。 */
fun libraryCategoryOf(typeId: String): LibraryCategory = when (typeId) {
    AppLaunchAction.TYPE_ID -> LibraryCategory.APP
    "intent.openUrl" -> LibraryCategory.LINK
    "shell.custom" -> LibraryCategory.CUSTOM
    else -> error("unmapped action library category: $typeId")
}
