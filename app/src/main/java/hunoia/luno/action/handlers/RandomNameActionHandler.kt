package hunoia.luno.action.handlers

import hunoia.luno.R
import hunoia.luno.bridge.copySensitiveText
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action

object RandomNameActionHandler : ActionHandler {

    override val supportedActions = setOf(ActionFacade.RANDOM_NAME)

    private val regexThreeVowels = Regex("[aeiou]{3}")
    private val regexThreeConsonants = Regex("[bcdfghjklmnpqrstvwxz]{3}")

    private val blockedNames = setOf("test", "null", "admin", "root", "system", "user")

    private val openingSyllables = arrayOf(
        "ve", "se", "sa", "si", "ke", "ka", "ki", "me", "ma", "mi", "mo",
        "ne", "na", "ni", "no", "le", "la", "li", "lo", "re", "ra", "ri",
        "ro", "te", "ta", "ti", "to", "be", "ba", "bi", "bo", "de", "da",
        "di", "do", "fe", "fa", "fi", "fo", "ze", "za", "zi", "ly", "my",
        "ny", "ry", "ae", "ei", "ia", "io", "ey", "ya", "so", "lu",
        "ve", "se", "ke", "me", "ne", "le", "re", "te"
    )

    private val middleSyllables = arrayOf(
        "so", "lo", "ro", "mo", "no", "si", "li", "ri", "mi", "ni",
        "sa", "la", "ra", "ma", "na", "ve", "le", "re", "me", "ne",
        "di", "da", "do", "ze", "za", "fa", "fe", "ta", "te", "to",
        "ka", "ki", "ke", "bi", "bo", "be", "ly", "my", "ny", "ry",
        "ae", "ei", "ia", "io"
    )

    private val endingSyllables = arrayOf(
        "ra", "ria", "la", "na", "ya", "el", "iel", "li", "ri", "ni",
        "ly", "ny", "da", "ra", "la", "na", "lia", "nia", "ria",
        "lya", "nya", "ra", "la", "ya", "da", "ra", "la", "na"
    )

    private var lastRandomName: String? = null

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.RANDOM_NAME -> {
                val name = generateRandomName()
                if (name != null) {
                    if (copySensitiveText(context.appContext, "", name)) {
                        context.showToast(name)
                    } else {
                        context.showToast(context.appContext.getString(R.string.random_name_copy_failed))
                    }
                } else {
                    context.showToast(context.appContext.getString(R.string.random_name_generate_failed))
                }
            }
            else -> return ActionExecutionResult.Ignored
        }
        return ActionExecutionResult.Success
    }

    private fun generateRandomName(): String? {
        repeat(20) {
            val useThreeSyllables = Math.random() < 0.4
            val opening = pick(openingSyllables)
            val ending = pick(endingSyllables)
            if (useThreeSyllables) {
                val middle = pick(middleSyllables)
                if (opening == middle || middle == ending) return@repeat
                val name = opening + middle + ending
                if (name.length in 4..8 && passesQualityFilter(name) && name != lastRandomName) {
                    val formatted = name.replaceFirstChar { it.uppercase() }
                    if (formatted.lowercase() !in blockedNames && formatted != lastRandomName) {
                        lastRandomName = formatted
                        return formatted
                    }
                }
            } else {
                if (opening == ending) return@repeat
                val name = opening + ending
                if (name.length in 4..8 && passesQualityFilter(name) && name != lastRandomName) {
                    val formatted = name.replaceFirstChar { it.uppercase() }
                    if (formatted.lowercase() !in blockedNames && formatted != lastRandomName) {
                        lastRandomName = formatted
                        return formatted
                    }
                }
            }
        }
        return null
    }

    private fun passesQualityFilter(name: String): Boolean {
        if (regexThreeVowels.containsMatchIn(name)) return false
        if (regexThreeConsonants.containsMatchIn(name)) return false
        return true
    }

    private fun pick(array: Array<String>): String =
        array[(Math.random() * array.size).toInt()]
}
