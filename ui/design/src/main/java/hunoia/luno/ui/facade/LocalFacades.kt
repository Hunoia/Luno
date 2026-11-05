package hunoia.luno.ui.facade

import androidx.compose.runtime.staticCompositionLocalOf
import hunoia.luno.facade.Facades

/**
 * 门面注入点（architecture §8.4）。
 *
 * 页面与状态持有者只读 [LocalFacades]，不直接引用 `:app` 的组装根；`:app` 在根部
 * `CompositionLocalProvider(LocalFacades provides AppGraph)` 注入实现。
 *
 * 用 `staticCompositionLocalOf`：值在进程内稳定，变化时整棵子树重建是可接受的代价。
 */
val LocalFacades = staticCompositionLocalOf<Facades> {
    error("LocalFacades 未注入：请在 root 用 CompositionLocalProvider 提供")
}
