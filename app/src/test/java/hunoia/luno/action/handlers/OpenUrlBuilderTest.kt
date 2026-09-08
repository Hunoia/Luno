package hunoia.luno.quicklaunch.launch

import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenUrlBuilderTest {

    @Test
    fun build_appendsWithQuestionWhenOriginalHasNoQuery() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path",
            data = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, miniWindow = true),
        )

        assertEquals("https://example.com/path?miniWindow=true", result)
    }

    @Test
    fun build_appendsWithAmpersandWhenOriginalHasQuery() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path?x=1",
            data = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, miniWindow = true),
        )

        assertEquals("https://example.com/path?x=1&miniWindow=true", result)
    }

    @Test
    fun build_appendsOnlyEnabledCustomParameters() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path",
            data = OpenAppOrUrlData(
                type = OpenAppOrUrlData.TYPE_URL,
                queryParameters = listOf(
                    OpenUrlQueryParameter(name = "enabled", value = "1", enabled = true),
                    OpenUrlQueryParameter(name = "disabled", value = "2", enabled = false),
                ),
            ),
        )

        assertEquals("https://example.com/path?enabled=1", result)
    }

    @Test
    fun build_skipsBlankParameterNamesAndKeepsBlankValues() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path",
            data = OpenAppOrUrlData(
                type = OpenAppOrUrlData.TYPE_URL,
                queryParameters = listOf(
                    OpenUrlQueryParameter(name = "", value = "ignored"),
                    OpenUrlQueryParameter(name = "empty", value = ""),
                ),
            ),
        )

        assertEquals("https://example.com/path?empty=", result)
    }

    @Test
    fun build_insertsBeforeFragment() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path#top",
            data = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, miniWindow = true),
        )

        assertEquals("https://example.com/path?miniWindow=true#top", result)
    }

    @Test
    fun build_insertsBeforeIntentSuffix() {
        val result = OpenUrlBuilder.build(
            rawUrl = "intent://example.com/path#Intent;scheme=https;end",
            data = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, miniWindow = true),
        )

        assertEquals("intent://example.com/path?miniWindow=true#Intent;scheme=https;end", result)
    }

    @Test
    fun build_encodesCustomParameterNamesAndValues() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path",
            data = OpenAppOrUrlData(
                type = OpenAppOrUrlData.TYPE_URL,
                queryParameters = listOf(OpenUrlQueryParameter(name = "中文 key", value = "a&b=c")),
            ),
        )

        assertEquals("https://example.com/path?%E4%B8%AD%E6%96%87%20key=a%26b%3Dc", result)
    }

    @Test
    fun build_doesNotAppendMiniWindowWhenDisabled() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path",
            data = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL, miniWindow = false),
        )

        assertEquals("https://example.com/path", result)
    }

    @Test
    fun build_appendsMiniWindowBeforeCustomParameters() {
        val result = OpenUrlBuilder.build(
            rawUrl = "https://example.com/path",
            data = OpenAppOrUrlData(
                type = OpenAppOrUrlData.TYPE_URL,
                miniWindow = true,
                queryParameters = listOf(OpenUrlQueryParameter(name = "k", value = "v")),
            ),
        )

        assertEquals("https://example.com/path?miniWindow=true&k=v", result)
    }
}
