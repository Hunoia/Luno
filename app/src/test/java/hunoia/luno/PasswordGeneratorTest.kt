package hunoia.luno

import hunoia.luno.action.api.PasswordGenerator
import hunoia.luno.config.model.ActionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun generate_defaultLength() {
        val password = PasswordGenerator.generate(ActionSettings.PasswordGenerator())

        assertEquals(16, password.length)
    }

    @Test
    fun normalize_clampsLength() {
        assertEquals(4, PasswordGenerator.normalize(ActionSettings.PasswordGenerator(length = 1)).length)
        assertEquals(32, PasswordGenerator.normalize(ActionSettings.PasswordGenerator(length = 99)).length)
    }

    @Test
    fun normalize_restoresDefaultsWhenAllTypesDisabled() {
        val normalized = PasswordGenerator.normalize(
            ActionSettings.PasswordGenerator(
                lowercase = false,
                uppercase = false,
                digits = false,
                symbols = false
            )
        )

        assertEquals(ActionSettings.PasswordGenerator(), normalized)
    }

    @Test
    fun generate_coversEnabledCharacterTypes() {
        val password = PasswordGenerator.generate(ActionSettings.PasswordGenerator())

        assertTrue(password.any { it in 'a'..'z' })
        assertTrue(password.any { it in 'A'..'Z' })
        assertTrue(password.any { it in '0'..'9' })
        assertTrue(password.any { it in PasswordGenerator.SYMBOLS })
    }

    @Test
    fun generate_digitsOnly() {
        val password = PasswordGenerator.generate(
            ActionSettings.PasswordGenerator(
                lowercase = false,
                uppercase = false,
                digits = true,
                symbols = false
            )
        )

        assertTrue(password.all { it in '0'..'9' })
    }

    @Test
    fun entropy_generatedUsesEnabledPoolSize() {
        assertEquals(103, PasswordGenerator.generatedEntropyBits(ActionSettings.PasswordGenerator()))
    }

    @Test
    fun entropy_generatedAccountsForRequiredCharacterTypes() {
        val entropy = PasswordGenerator.generatedEntropyBits(ActionSettings.PasswordGenerator(length = 4))

        assertEquals(22, entropy)
    }

    @Test
    fun entropy_generatedDoesNotExceedUnconstrainedPool() {
        val entropy = PasswordGenerator.generatedEntropyBits(ActionSettings.PasswordGenerator())

        assertTrue(entropy <= 103)
    }

    @Test
    fun entropy_estimatedEmptyPassword() {
        assertEquals(0, PasswordGenerator.estimatedEntropyBits(""))
    }

    @Test
    fun entropy_estimatedIncludesOtherCharacters() {
        val entropy = PasswordGenerator.estimatedEntropyBits("a中")

        assertTrue(entropy > 0)
    }

    @Test
    fun entropy_estimatedPenalizesCommonPasswords() {
        val common = PasswordGenerator.estimatedEntropyBits("P@ssw0rd")
        val randomLike = PasswordGenerator.estimatedEntropyBits("P@8xQ0rd")

        assertTrue(common < randomLike)
    }

    @Test
    fun entropy_estimatedPenalizesRepeatsAndSequences() {
        assertTrue(PasswordGenerator.estimatedEntropyBits("aaaaaaaa") < PasswordGenerator.estimatedEntropyBits("aB8#kL2@"))
        assertTrue(PasswordGenerator.estimatedEntropyBits("abcdef") < PasswordGenerator.estimatedEntropyBits("a8C#f2"))
    }
}
