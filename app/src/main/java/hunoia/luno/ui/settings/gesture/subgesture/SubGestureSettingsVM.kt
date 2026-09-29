package hunoia.luno.ui.settings.gesture.subgesture

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureSlideDistanceGap
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureTriggerDistance
import hunoia.luno.config.SubGestureCleaner
import hunoia.luno.core.AppContext
import hunoia.luno.config.model.ActionPanelStyles
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.ui.navigation.SubGestureEditor
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureAngle
import hunoia.luno.config.model.SubGestureSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SubGestureSettingsUiState(
    val subGesture: SubGesture? = null,
    val allSubGestures: List<SubGesture>? = null,
    val showDeleteWarningDialog: Boolean = false,
    val showMirrorCopyDialog: Boolean = false,
)

sealed interface SubGestureSettingsUiEvent

class SubGestureSettingsVM(savedStateHandle: SavedStateHandle) : BaseComposeVM<SubGestureSettingsUiState, SubGestureSettingsUiEvent>() {

    private val subGestureEditor = savedStateHandle.toRoute<SubGestureEditor>()

    override val initialState: SubGestureSettingsUiState = SubGestureSettingsUiState()

    init {
        loadData()
    }

    fun showDeleteWarningDialog(show: Boolean) {
        updateUiState { it.copy(showDeleteWarningDialog = show) }
    }

    fun showMirrorCopyDialog(show: Boolean) {
        updateUiState { it.copy(showMirrorCopyDialog = show) }
    }

    fun createMirroredCopy() {
        viewModelScope.launch {
            val original = uiState.subGesture ?: return@launch
            val newId = java.util.UUID.randomUUID().toString()
            val mirrored = original.copy(
                id = newId,
                name = AppContext.get().getString(
                    R.string.mirror_sub_gesture_name,
                    original.name.ifEmpty { AppContext.get().getString(R.string.sub_gesture) }
                ),
                slideActions = original.slideActions.copy(
                    actions = original.slideActions.actions.mapKeys { (direction, _) -> direction.mirrorHorizontal() }
                ),
                slideHoldActions = original.slideHoldActions.copy(
                    actions = original.slideHoldActions.actions.mapKeys { (direction, _) -> direction.mirrorHorizontal() }
                ),
                longSlideActions = original.longSlideActions.copy(
                    actions = original.longSlideActions.actions.mapKeys { (direction, _) -> direction.mirrorHorizontal() }
                ),
                longSlideHoldActions = original.longSlideHoldActions.copy(
                    actions = original.longSlideHoldActions.actions.mapKeys { (direction, _) -> direction.mirrorHorizontal() }
                ),
                longSlideActionPanelStyles = original.longSlideActionPanelStyles.copy(
                    styles = original.longSlideActionPanelStyles.styles.mapKeys { (direction, _) -> direction.mirrorHorizontal() }
                ),
                angle = original.angle.copy(
                    boundaries = original.angle.boundaries.let { b ->
                        listOf(3, 2, 1, 0, 7, 6, 5, 4).map { i -> ((0.5f - b[i]) + 1f) % 1f }
                    }
                ),
            )
            ConfigProvider.updateSubGestureSettings { settings ->
                settings.copy(subGestures = settings.subGestures + mirrored)
            }
        }
    }

    fun deleteSubGesture() {
        viewModelScope.launch {
            ConfigProvider.updateSubGestureSettings { settings ->
                settings.copy(
                    subGestures = settings.subGestures.filter { it.id != subGestureEditor.subGestureId }
                )
            }
            cleanReferences(subGestureEditor.subGestureId)
        }.invokeOnCompletion {
            finish()
        }
    }

    private suspend fun cleanReferences(deletedId: String) {
        SubGestureCleaner.cleanSubGestureReferences(
            deletedId = deletedId,
            shouldRemove = { SubGestureCleaner.matchesDeletedSubGesture(it, deletedId) }
        )
    }

    fun updateAngle(angle: SubGestureAngle) {
        viewModelScope.launch {
            ConfigProvider.updateSubGestureSettings { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map { gesture ->
                        if (gesture.id == subGestureEditor.subGestureId) gesture.copy(angle = angle)
                        else gesture
                    }
                )
            }
        }
    }

    fun updateColor(color: Int) {
        viewModelScope.launch {
            ConfigProvider.updateSubGestureSettings { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map { gesture ->
                        if (gesture.id == subGestureEditor.subGestureId) gesture.copy(color = color)
                        else gesture
                    }
                )
            }
        }
    }

    private fun updateSubGesture(fieldUpdate: SubGesture.() -> SubGesture) {
        viewModelScope.launch {
            ConfigProvider.updateSubGestureSettings { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map { gesture ->
                        if (gesture.id == subGestureEditor.subGestureId) gesture.fieldUpdate()
                        else gesture
                    }
                )
            }
        }
    }

    fun onSubSlideVibrateChange(value: Boolean) = updateSubGesture { copy(slideVibrate = value) }
    fun onSubLongSlideVibrateChange(value: Boolean) = updateSubGesture { copy(longSlideVibrate = value) }
    fun onSubSlideHoldVibrateChange(value: Boolean) = updateSubGesture { copy(slideHoldVibrate = value) }
    fun onSubLongSlideHoldVibrateChange(value: Boolean) = updateSubGesture { copy(longSlideHoldVibrate = value) }
    fun onSubTriggerDistanceChange(value: Float) = updateSubGesture {
        val maxSlide = (longSlideTriggerDistance - MinGestureSlideDistanceGap)
            .coerceIn(MinSubGestureTriggerDistance, MaxSubGestureTriggerDistance)
        val slide = value.toInt().coerceIn(MinSubGestureTriggerDistance, maxSlide)
        copy(triggerDistance = slide)
    }
    fun onSubLongSlideTriggerDistanceChange(value: Float) = updateSubGesture {
        val minLongSlide = (triggerDistance + MinGestureSlideDistanceGap)
            .coerceIn(MinSubGestureLongSlideTriggerDistance, MaxSubGestureLongSlideTriggerDistance)
        val longSlide = value.toInt().coerceIn(minLongSlide, MaxSubGestureLongSlideTriggerDistance)
        copy(longSlideTriggerDistance = longSlide)
    }
    fun onSubSlideHoldTriggerDelayMsChange(value: Float) = updateSubGesture { copy(slideHoldTriggerDelayMs = value.toLong()) }
    fun onSubLongSlideHoldTriggerDelayMsChange(value: Float) = updateSubGesture { copy(longSlideHoldTriggerDelayMs = value.toLong()) }
    fun onSubTimeoutMsChange(value: Float) = updateSubGesture { copy(timeoutMs = value.toLong()) }

    fun updateLongSlideActionPanelStyle(direction: GestureDirection, style: ActionPanelStyles) {
        updateSubGesture {
            copy(longSlideActionPanelStyles = longSlideActionPanelStyles.withStyle(direction, style))
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            ConfigProvider.subGestureSettings.collectLatest { settings ->
                val gesture = settings.subGestures.find { it.id == subGestureEditor.subGestureId }
                updateUiState {
                    it.copy(
                        subGesture = gesture,
                        allSubGestures = settings.subGestures,
                    )
                }
            }
        }
    }
}

private fun GestureDirection.mirrorHorizontal(): GestureDirection = when (this) {
    GestureDirection.Left -> GestureDirection.Right
    GestureDirection.Right -> GestureDirection.Left
    GestureDirection.UpLeft -> GestureDirection.UpRight
    GestureDirection.UpRight -> GestureDirection.UpLeft
    GestureDirection.DownLeft -> GestureDirection.DownRight
    GestureDirection.DownRight -> GestureDirection.DownLeft
    GestureDirection.Up -> GestureDirection.Up
    GestureDirection.Down -> GestureDirection.Down
}
