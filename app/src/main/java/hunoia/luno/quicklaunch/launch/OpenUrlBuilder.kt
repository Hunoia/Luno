package hunoia.luno.quicklaunch.launch

import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object OpenUrlBuilder {
    fun build(rawUrl: String, data: OpenAppOrUrlData): String {
        val additions = buildList {
            if (data.miniWindow) {
                add(OpenUrlQueryParameter(name = "miniWindow", value = "true"))
            }
            data.queryParameters
                .filter { it.enabled && it.name.isNotBlank() }
                .forEach { add(it) }
        }
        if (additions.isEmpty()) return rawUrl

        val fragmentIndex = rawUrl.indexOf('#')
        val base = if (fragmentIndex >= 0) rawUrl.substring(0, fragmentIndex) else rawUrl
        val suffix = if (fragmentIndex >= 0) rawUrl.substring(fragmentIndex) else ""
        val separator = when {
            base.endsWith("?") || base.endsWith("&") -> ""
            base.contains("?") -> "&"
            else -> "?"
        }
        return base + separator + additions.joinToString("&") { parameter ->
            "${parameter.name.encodeQueryComponent()}=${parameter.value.encodeQueryComponent()}"
        } + suffix
    }

    private fun String.encodeQueryComponent(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
