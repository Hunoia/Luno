package hunoia.luno.runtime.button

import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.config.model.ButtonVisibilitySettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.runtime.GestureRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonVisibilityPolicyTest {

    private val button1 = GestureButton(id = "1")
    private val button2 = GestureButton(id = "2")

    private fun state(
        packageName: String = "com.example",
        lock: Boolean = false,
        launcher: Boolean = false,
        landscape: Boolean = false,
        keyboard: Boolean = false,
        hidden: Map<String, Long> = emptyMap(),
        nowMs: Long = 1_000L,
    ) = GestureRuntimeState(
        currentPackageName = packageName,
        isNowInLockScreenPage = lock,
        isLandscape = landscape,
        isInLauncher = launcher,
        isKeyboardInputActive = keyboard,
        hiddenGestureButtons = hidden,
        nowMs = nowMs,
    )

    private fun policy(
        profiles: List<VisibilityProfile>,
        state: GestureRuntimeState = state(),
        gestureEnabled: Boolean = true,
    ) = ButtonVisibilityPolicy(
        initialSettings = InitialSettings(
            gestureEnabled = gestureEnabled,
            buttonVisibility = ButtonVisibilitySettings(profiles),
        ),
        runtimeState = state,
    )

    @Test
    fun noProfiles_keepsButtonVisible() {
        val policy = policy(emptyList())
        assertTrue(policy.shouldShow(button1))
    }

    @Test
    fun appMatch_hidesOnlyTargetedButton() {
        val policy = policy(
            listOf(
                VisibilityProfile(
                    id = "p",
                    packageNames = setOf("com.example"),
                    visible = false,
                    target = ButtonTarget.ONLY,
                    buttonIds = listOf("1"),
                ),
            ),
        )
        assertFalse(policy.shouldShow(button1))
        assertTrue(policy.shouldShow(button2))
    }

    @Test
    fun nonMatchingApp_keepsVisible() {
        val policy = policy(
            listOf(
                VisibilityProfile(id = "p", packageNames = setOf("com.other"), visible = false),
            ),
        )
        assertTrue(policy.shouldShow(button1))
    }

    @Test
    fun lastMatchingProfileWins() {
        val policy = policy(
            listOf(
                VisibilityProfile(id = "hide", packageNames = setOf("com.example"), visible = false),
                VisibilityProfile(id = "show", packageNames = setOf("com.example"), visible = true),
            ),
        )
        assertTrue(policy.shouldShow(button1))

        val reverse = policy(
            listOf(
                VisibilityProfile(id = "show", packageNames = setOf("com.example"), visible = true),
                VisibilityProfile(id = "hide", packageNames = setOf("com.example"), visible = false),
            ),
        )
        assertFalse(reverse.shouldShow(button1))
    }

    @Test
    fun laterProfileNotAffectingButton_doesNotResetEarlierDecision() {
        val policy = policy(
            listOf(
                VisibilityProfile(id = "hide1", visible = false, target = ButtonTarget.ONLY, buttonIds = listOf("1")),
                VisibilityProfile(id = "hide2", visible = false, target = ButtonTarget.ONLY, buttonIds = listOf("2")),
            ),
        )
        assertFalse(policy.shouldShow(button1))
        assertFalse(policy.shouldShow(button2))
    }

    @Test
    fun screenDimensionMatchesAndIsAndedWithApp() {
        val hideLandscape = VisibilityProfile(
            id = "landscape",
            screens = setOf(VisibilityScreen.LANDSCAPE),
            visible = false,
        )
        assertFalse(policy(listOf(hideLandscape), state(landscape = true)).shouldShow(button1))
        assertTrue(policy(listOf(hideLandscape), state(landscape = false)).shouldShow(button1))

        val hideInAppLandscape = hideLandscape.copy(packageNames = setOf("com.example"))
        assertFalse(policy(listOf(hideInAppLandscape), state(landscape = true)).shouldShow(button1))
        assertTrue(
            policy(listOf(hideInAppLandscape), state(packageName = "com.other", landscape = true))
                .shouldShow(button1),
        )
    }

    @Test
    fun screenDimensions_coverLockLauncherKeyboard() {
        val profile = VisibilityProfile(
            id = "screens",
            screens = setOf(
                VisibilityScreen.LOCK_SCREEN,
                VisibilityScreen.LAUNCHER,
                VisibilityScreen.KEYBOARD_INPUT,
            ),
            visible = false,
        )
        assertFalse(policy(listOf(profile), state(lock = true)).shouldShow(button1))
        assertFalse(policy(listOf(profile), state(launcher = true)).shouldShow(button1))
        assertFalse(policy(listOf(profile), state(keyboard = true)).shouldShow(button1))
        assertTrue(policy(listOf(profile), state()).shouldShow(button1))
    }

    @Test
    fun userDisabledBaselineCannotBeReopenedByProfile() {
        val disabled = GestureButton(id = "1", enabled = false)
        val policy = policy(listOf(VisibilityProfile(id = "show", visible = true)))
        assertFalse(policy.shouldShow(disabled))
    }

    @Test
    fun tempHideWinsOverProfile() {
        val policy = policy(
            listOf(VisibilityProfile(id = "show", visible = true)),
            state(hidden = mapOf("1" to 2_000L), nowMs = 1_000L),
        )
        assertFalse(policy.shouldShow(button1))
    }

    @Test
    fun exceptTarget_onlyExcludesListedButtons() {
        val policy = policy(
            listOf(
                VisibilityProfile(
                    id = "except",
                    visible = false,
                    target = ButtonTarget.EXCEPT,
                    buttonIds = listOf("2"),
                ),
            ),
        )
        assertFalse(policy.shouldShow(button1))
        assertTrue(policy.shouldShow(button2))
    }

    @Test
    fun gestureDisabled_hidesEverything() {
        val policy = policy(
            listOf(VisibilityProfile(id = "show", visible = true)),
            gestureEnabled = false,
        )
        assertFalse(policy.shouldShow(button1))
    }
}
