package hunoia.luno.action.controller

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import hunoia.luno.action.model.ClipboardOperation
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.Action
import hunoia.luno.bridge.copySensitiveText

class ClipboardController(private val context: Context) {

    fun copyText(text: String, label: String = ""): Boolean {
        return copySensitiveText(context, label, text)
    }

    fun copyClipboard(action: Action.Clipboard): ActionResult {
        val text = when (action.operation) {
            ClipboardOperation.RANDOM_NAME -> generateRandomName()
            ClipboardOperation.GENERATE_PASSWORD -> generatePassword(action.length)
        }
        if (text == null) return ActionResult.Failed(ActionFailure.ExecutionFailed)
        val copied = copyText(text)
        return if (copied) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
    }

    private fun generateRandomName(): String? {
        val blockedNames = setOf("test", "null", "admin", "root", "system", "user")
        val regexThreeVowels = Regex("[aeiou]{3}")
        val regexThreeConsonants = Regex("[bcdfghjklmnpqrstvwxz]{3}")
        val openingSyllables = arrayOf(
            "ve", "se", "sa", "si", "ke", "ka", "ki", "me", "ma", "mi", "mo",
            "ne", "na", "ni", "no", "le", "la", "li", "lo", "re", "ra", "ri",
            "ro", "te", "ta", "ti", "to", "be", "ba", "bi", "bo", "de", "da",
            "di", "do", "fe", "fa", "fi", "fo", "ze", "za", "zi", "ly", "my",
            "ny", "ry", "ae", "ei", "ia", "io", "ey", "ya", "so", "lu",
            "ve", "se", "ke", "me", "ne", "le", "re", "te"
        )
        val middleSyllables = arrayOf(
            "so", "lo", "ro", "mo", "no", "si", "li", "ri", "mi", "ni",
            "sa", "la", "ra", "ma", "na", "ve", "le", "re", "me", "ne",
            "di", "da", "do", "ze", "za", "fa", "fe", "ta", "te", "to",
            "ka", "ki", "ke", "bi", "bo", "be", "ly", "my", "ny", "ry",
            "ae", "ei", "ia", "io"
        )
        val endingSyllables = arrayOf(
            "ra", "ria", "la", "na", "ya", "el", "iel", "li", "ri", "ni",
            "ly", "ny", "da", "ra", "la", "na", "lia", "nia", "ria",
            "lya", "nya", "ra", "la", "ya", "da", "ra", "la", "na"
        )
        repeat(20) {
            val useThreeSyllables = Math.random() < 0.4
            val opening = openingSyllables[(Math.random() * openingSyllables.size).toInt()]
            val ending = endingSyllables[(Math.random() * endingSyllables.size).toInt()]
            val name = if (useThreeSyllables) {
                val middle = middleSyllables[(Math.random() * middleSyllables.size).toInt()]
                if (opening == middle || middle == ending) return@repeat
                opening + middle + ending
            } else {
                if (opening == ending) return@repeat
                opening + ending
            }
            if (name.length in 4..8 && !regexThreeVowels.containsMatchIn(name) && !regexThreeConsonants.containsMatchIn(name)) {
                val formatted = name.replaceFirstChar { it.uppercase() }
                if (formatted.lowercase() !in blockedNames) return formatted
            }
        }
        return null
    }

    private fun generatePassword(length: Int): String? {
        return try {
            val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
            val random = java.security.SecureRandom()
            val sb = StringBuilder(length)
            for (i in 0 until length) {
                sb.append(charset[random.nextInt(charset.length)])
            }
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }
}
