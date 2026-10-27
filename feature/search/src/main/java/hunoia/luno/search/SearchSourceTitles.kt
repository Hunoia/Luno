package hunoia.luno.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import hunoia.luno.search.R

/**
 * 搜索来源显示名：来源 key → 资源 id → 文案。
 *
 * 归在搜索模块（来源注册表与文案都在这里），面板与设置页共用，避免两处各写一份。
 */
@Composable
fun searchSourceTitle(key: String): String =
    stringResource(SearchSourceRegistry.titleRes(key))
