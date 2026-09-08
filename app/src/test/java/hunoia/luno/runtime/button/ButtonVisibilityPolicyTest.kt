package hunoia.luno.runtime.button

import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonDefaults
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.runtime.GestureRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonVisibilityPolicyTest {

    private val defaultButton = GestureButton(
        id = "test_button",
        enabled = true,
        fitSoftKeyboard = false,
        hideLandscape = false,
        hideHomeScreen = false,
        hideScreenLock = false,
    )

    private val defaultSettings = InitialSettings(gestureEnabled = true)
    private val defaultAdvanced = AdvancedSettings()
    private val defaultRuntime = GestureRuntimeState(
        currentPackageName = "com.example.app",
        isNowInLockScreenPage = false,
        isLandscape = false,
        isInLauncher = false,
        isKeyboardInputActive = false,
        hiddenGestureButtons = emptyMap(),
        isMouseMode = false,
        nowMs = Long.MAX_VALUE,
    )

    @Test
    fun gestureDisabled_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            initialSettings = InitialSettings(gestureEnabled = false),
            advancedSettings = defaultAdvanced,
            runtimeState = defaultRuntime,
        )
        assertFalse(policy.shouldShow(defaultButton))
    }

    @Test
    fun buttonDisabled_hidesButton() {
        val policy = ButtonVisibilityPolicy(defaultSettings, defaultAdvanced, defaultRuntime)
        assertFalse(policy.shouldShow(defaultButton.copy(enabled = false)))
    }

    @Test
    fun mouseMode_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(isMouseMode = true),
        )
        assertFalse(policy.shouldShow(defaultButton))
    }

    @Test
    fun keyboardInput_withFitSoftKeyboard_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(isKeyboardInputActive = true),
        )
        assertFalse(policy.shouldShow(defaultButton.copy(fitSoftKeyboard = true)))
    }

    @Test
    fun keyboardInput_withoutFitSoftKeyboard_showsButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(isKeyboardInputActive = true),
        )
        assertTrue(policy.shouldShow(defaultButton.copy(fitSoftKeyboard = false)))
    }

    @Test
    fun landscapeWithHideLandscape_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(isLandscape = true),
        )
        assertFalse(policy.shouldShow(defaultButton.copy(hideLandscape = true)))
    }

    @Test
    fun launcherWithHideHomeScreen_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(isInLauncher = true),
        )
        assertFalse(policy.shouldShow(defaultButton.copy(hideHomeScreen = true)))
    }

    @Test
    fun lockScreenWithHideScreenLock_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(isNowInLockScreenPage = true),
        )
        assertFalse(policy.shouldShow(defaultButton.copy(hideScreenLock = true)))
    }

    @Test
    fun excludeApp_hidesButton() {
        val advanced = AdvancedSettings(excludeApps = listOf("com.example.app"))
        val policy = ButtonVisibilityPolicy(defaultSettings, advanced, defaultRuntime)
        assertFalse(policy.shouldShow(defaultButton))
    }

    @Test
    fun hiddenTemporarily_hidesButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(
                hiddenGestureButtons = mapOf("test_button" to Long.MAX_VALUE),
                nowMs = 0L,
            ),
        )
        assertFalse(policy.shouldShow(defaultButton))
    }

    @Test
    fun hiddenTemporarily_expired_showsButton() {
        val policy = ButtonVisibilityPolicy(
            defaultSettings, defaultAdvanced,
            defaultRuntime.copy(
                hiddenGestureButtons = mapOf("test_button" to 500L),
                nowMs = 1000L,
            ),
        )
        assertTrue(policy.shouldShow(defaultButton))
    }

    @Test
    fun allConditionsMet_showsButton() {
        val policy = ButtonVisibilityPolicy(defaultSettings, defaultAdvanced, defaultRuntime)
        assertTrue(policy.shouldShow(defaultButton))
    }
}
