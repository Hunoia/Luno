package hunoia.luno.facade

/**
 * UI 能看到的全部门面。
 *
 * 由 `:app` 实现并在根部 `CompositionLocalProvider` 注入（`:ui:design` 的 `LocalFacades`）；
 * 页面 / 状态持有者不再直接引用组装根。
 */
interface Facades {
    val system: SystemFacade
    val runtime: RuntimeFacade
    val ocrModels: OcrModelFacade
    val logs: LogFacade
    val backup: BackupFacade
    val linkSettings: LinkSettingsFacade
}
