package hunoia.luno.runtime.button

import hunoia.luno.config.model.ButtonVisibilitySettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.VisibilityProfile
import hunoia.luno.config.model.VisibilityScreen
import hunoia.luno.config.model.VisibilityTargetMode
import hunoia.luno.config.model.VisibilityTargets
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
                    mode = VisibilityTargetMode.ONLY,
                    targetIds = setOf("1"),
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
                VisibilityProfile(id = "hide1", visible = false, mode = VisibilityTargetMode.ONLY, targetIds = setOf("1")),
                VisibilityProfile(id = "hide2", visible = false, mode = VisibilityTargetMode.ONLY, targetIds = setOf("2")),
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
                    mode = VisibilityTargetMode.EXCEPT,
                    targetIds = setOf("2"),
                ),
            ),
        )
        assertFalse(policy.shouldShow(button1))
        assertTrue(policy.shouldShow(button2))
    }

    @Test
    fun floatBallProfile_hidesOnlyBallWhenOnlySelected() {
        val profile = VisibilityProfile(
            id = "ball",
            visible = false,
            mode = VisibilityTargetMode.ONLY,
            targetIds = setOf(VisibilityTargets.FLOAT_BALL),
        )
        val settings = InitialSettings(buttonVisibility = ButtonVisibilitySettings(listOf(profile)))
        assertFalse(shouldShowFloatBall(settings, state()))
        assertTrue(policy(listOf(profile)).shouldShow(button1))
    }

    @Test
    fun floatBallDefaultsVisibleWithoutProfile() {
        assertTrue(shouldShowFloatBall(InitialSettings(), state()))
        assertTrue(shouldShowNotificationCapsule(InitialSettings(), state()))
        assertTrue(shouldShowClipboardBall(InitialSettings(), state()))
    }

    @Test
    fun surfaceTargetsAreIndependent() {
        val hideNotification = InitialSettings(
            buttonVisibility = ButtonVisibilitySettings(
                listOf(
                    VisibilityProfile(
                        id = "notification",
                        visible = false,
                        mode = VisibilityTargetMode.ONLY,
                        targetIds = setOf(VisibilityTargets.NOTIFICATION_CAPSULE),
                    ),
                ),
            ),
        )
        assertTrue(shouldShowFloatBall(hideNotification, state()))
        assertFalse(shouldShowNotificationCapsule(hideNotification, state()))
        assertTrue(shouldShowClipboardBall(hideNotification, state()))

        val hideClipboard = InitialSettings(
            buttonVisibility = ButtonVisibilitySettings(
                listOf(
                    VisibilityProfile(
                        id = "clipboard",
                        visible = false,
                        mode = VisibilityTargetMode.ONLY,
                        targetIds = setOf(VisibilityTargets.CLIPBOARD_BALL),
                    ),
                ),
            ),
        )
        assertTrue(shouldShowFloatBall(hideClipboard, state()))
        assertTrue(shouldShowNotificationCapsule(hideClipboard, state()))
        assertFalse(shouldShowClipboardBall(hideClipboard, state()))
    }

    @Test
    fun floatBallShowProfileCannotBeatLaterHideProfile() {
        val settings = InitialSettings(
            buttonVisibility = ButtonVisibilitySettings(
                listOf(
                    VisibilityProfile(id = "show", visible = true),
                    VisibilityProfile(id = "hide", visible = false),
                ),
            ),
        )
        assertFalse(shouldShowFloatBall(settings, state()))
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
