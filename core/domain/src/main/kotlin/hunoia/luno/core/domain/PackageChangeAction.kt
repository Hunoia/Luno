package hunoia.luno.core.domain

/** 应用包变化动作；null/空表示不限。 */
object PackageChangeAction {
    const val ADDED = "added"
    const val REMOVED = "removed"
    const val REPLACED = "replaced"
    const val CHANGED = "changed"

    val all: List<String> = listOf(ADDED, REMOVED, REPLACED, CHANGED)
}
