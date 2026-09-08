package hunoia.luno.action.api

import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordDefaultLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordMaxLength
import hunoia.luno.config.defaults.ActionSettingsDefaults.PasswordMinLength
import hunoia.luno.config.model.ActionSettings
import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object PasswordGenerator {
    const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?"

    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val OTHER_CHAR_POOL_SIZE = 32
    private val secureRandom = SecureRandom()

    fun normalize(config: ActionSettings.PasswordGenerator): ActionSettings.PasswordGenerator {
        val hasAnyType = config.lowercase || config.uppercase || config.digits || config.symbols
        val enabledTypeCount = if (hasAnyType) enabledPools(config).size else 4
        val length = config.length
            .coerceIn(PasswordMinLength, PasswordMaxLength)
            .coerceAtLeast(enabledTypeCount)
        return if (hasAnyType) {
            config.copy(length = length)
        } else {
            ActionSettings.PasswordGenerator(length = PasswordDefaultLength.coerceAtLeast(enabledTypeCount))
        }
    }

    fun generate(config: ActionSettings.PasswordGenerator): String {
        val normalized = normalize(config)
        val pools = enabledPools(normalized)
        require(pools.isNotEmpty()) { "At least one character type is required" }

        val chars = mutableListOf<Char>()
        pools.forEach { pool -> chars += pool.randomChar() }
        val allChars = pools.joinToString(separator = "")
        repeat(normalized.length - chars.size) {
            chars += allChars.randomChar()
        }
        chars.shuffleSecure()
        return chars.joinToString(separator = "")
    }

    fun generatedEntropyBits(config: ActionSettings.PasswordGenerator): Int {
        val normalized = normalize(config)
        val poolSizes = enabledPools(normalized).map { it.length }
        if (poolSizes.isEmpty()) return 0
        val legalPasswordSpaceBits = constrainedEntropyBits(normalized.length, poolSizes)
        val generationProcessBits = generationProcessEntropyBits(normalized.length, poolSizes)
        return min(legalPasswordSpaceBits, generationProcessBits).roundToInt()
    }

    fun estimatedEntropyBits(text: String): Int {
        if (text.isEmpty()) return 0
        val dp = DoubleArray(text.length + 1) { Double.POSITIVE_INFINITY }
        dp[0] = 0.0
        for (start in text.indices) {
            if (!dp[start].isFinite()) continue
            for (end in start + 1..text.length) {
                val part = text.substring(start, end)
                val cost = estimatePatternCost(part)
                if (cost != null) {
                    dp[end] = min(dp[end], dp[start] + cost)
                }
            }
            val charCost = estimateSingleCharCost(text[start])
            dp[start + 1] = min(dp[start + 1], dp[start] + charCost)
        }
        return dp[text.length].roundToInt().coerceAtLeast(0)
    }

    fun enabledTypeCount(config: ActionSettings.PasswordGenerator): Int = enabledPools(config).size

    private fun enabledPools(config: ActionSettings.PasswordGenerator): List<String> = buildList {
        if (config.lowercase) add(LOWERCASE)
        if (config.uppercase) add(UPPERCASE)
        if (config.digits) add(DIGITS)
        if (config.symbols) add(SYMBOLS)
    }

    private fun constrainedEntropyBits(length: Int, poolSizes: List<Int>): Double {
        val totalPoolSize = poolSizes.sum()
        var validCount = 0.0
        val subsetCount = 1 shl poolSizes.size
        for (mask in 0 until subsetCount) {
            var excludedSize = 0
            for (index in poolSizes.indices) {
                if ((mask and (1 shl index)) != 0) {
                    excludedSize += poolSizes[index]
                }
            }
            val remainingSize = totalPoolSize - excludedSize
            if (remainingSize <= 0) continue
            val count = remainingSize.toDouble().pow(length)
            if (Integer.bitCount(mask) % 2 == 0) {
                validCount += count
            } else {
                validCount -= count
            }
        }
        return log2(max(validCount, 1.0))
    }

    private fun generationProcessEntropyBits(length: Int, poolSizes: List<Int>): Double {
        val requiredTypeCount = poolSizes.size
        val totalPoolSize = poolSizes.sum()
        val requiredCharBits = poolSizes.sumOf { log2(it.toDouble()) }
        val requiredPositionBits = log2Permutation(length, requiredTypeCount)
        val remainingCharBits = (length - requiredTypeCount) * log2(totalPoolSize.toDouble())
        return requiredPositionBits + requiredCharBits + remainingCharBits
    }

    private fun log2Permutation(n: Int, k: Int): Double {
        var result = 0.0
        repeat(k) { index ->
            result += log2((n - index).toDouble())
        }
        return result
    }

    private fun estimatePatternCost(text: String): Double? {
        return listOfNotNull(
            estimateCommonPasswordCost(text),
            estimateRepeatCost(text),
            estimateDigitCost(text),
            estimateSequenceCost(text),
        ).minOrNull()
    }

    private fun estimateSingleCharCost(char: Char): Double {
        val poolSize = when (char) {
            in LOWERCASE -> LOWERCASE.length
            in UPPERCASE -> UPPERCASE.length
            in DIGITS -> DIGITS.length
            in SYMBOLS -> SYMBOLS.length
            else -> OTHER_CHAR_POOL_SIZE
        }
        return log2(poolSize.toDouble()) * SINGLE_CHAR_DAMPING_BITS
    }

    private fun estimateCommonPasswordCost(text: String): Double? {
        val normalized = normalizeCommonPassword(text)
        val rank = COMMON_PASSWORDS.indexOf(normalized).takeIf { it >= 0 } ?: return null
        val caseCost = if (text.any { it.isUpperCase() }) 1.0 else 0.0
        val leetCost = if (text.any { it in LEET_CHARS }) 2.0 else 0.0
        return log2((rank + 2).toDouble()) + PATTERN_ID_BITS + caseCost + leetCost
    }

    private fun estimateRepeatCost(text: String): Double? {
        if (text.length < 3 || text.any { it != text.first() }) return null
        return estimateSingleCharCost(text.first()) + log2(text.length.toDouble()) + PATTERN_ID_BITS
    }

    private fun estimateDigitCost(text: String): Double? {
        if (text.length < 2 || text.any { it !in DIGITS }) return null
        return text.length * log2(DIGITS.length.toDouble()) + PATTERN_ID_BITS
    }

    private fun estimateSequenceCost(text: String): Double? {
        if (text.length < 3) return null
        val diffs = text.zipWithNext { a, b -> b.code - a.code }
        val diff = diffs.first()
        if (diff == 0 || diffs.any { it != diff }) return null
        if (kotlin.math.abs(diff) > 5) return null
        val startCost = estimateSingleCharCost(text.first())
        val diffCost = log2(11.0)
        return startCost + diffCost + log2(text.length.toDouble()) + PATTERN_ID_BITS
    }

    private fun normalizeCommonPassword(text: String): String {
        return text.lowercase().map { char ->
            when (char) {
                '0' -> 'o'
                '1', '!' -> 'i'
                '3' -> 'e'
                '4', '@' -> 'a'
                '5', '$' -> 's'
                '7' -> 't'
                else -> char
            }
        }.joinToString(separator = "")
    }

    private fun String.randomChar(): Char = this[secureRandom.nextInt(length)]

    private fun MutableList<Char>.shuffleSecure() {
        for (i in lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = this[i]
            this[i] = this[j]
            this[j] = tmp
        }
    }

    private const val PATTERN_ID_BITS = 3.0
    private const val SINGLE_CHAR_DAMPING_BITS = 0.85
    private const val LEET_CHARS = "013457!@$"
    private val COMMON_PASSWORDS = listOf(
        "password",
        "qwerty",
        "admin",
        "welcome",
        "letmein",
        "monkey",
        "dragon",
        "iloveyou",
        "abc",
        "master",
        "login",
        "passw0rd",
    )
}
