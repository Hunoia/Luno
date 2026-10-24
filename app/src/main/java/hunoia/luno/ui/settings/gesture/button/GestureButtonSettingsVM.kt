package hunoia.luno.ui.settings.gesture.button

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureSlideDistanceGap
import hunoia.luno.config.defaults.SettingsUiDefaults.MinLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureButtonLength
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxGestureButtonArea
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonAngle
import hunoia.luno.ui.navigation.GestureButtonSettings

import hunoia.luno.domain.gesture.GestureConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


data class GestureButtonSettingsUiState(
    val gestureButtonSettings: GestureButtonSettings,
    val gestureButtons: List<GestureButton> = emptyList(),
    val mirrorHorizontal: Boolean = true,
    val showDeleteWarningDialog: Boolean = false,
    val isGestureButtonAdjusting: Boolean = false,
    /** 触发动作列表展开态：放 VM，动作选择页返回重组合不丢。 */
    val expandedActionGroup: GestureActionGroup? = null,
) {
    val gestureButton: GestureButton? = gestureButtons.find { it.id == gestureButtonSettings.buttonId }
}

sealed interface GestureButtonSettingsUiEvent

class GestureButtonSettingsVM(savedStateHandle: SavedStateHandle) : BaseComposeVM<GestureButtonSettingsUiState, GestureButtonSettingsUiEvent>() {

    private val gestureButtonSettings = savedStateHandle.toRoute<GestureButtonSettings>()

    override val initialState: GestureButtonSettingsUiState = GestureButtonSettingsUiState(gestureButtonSettings)

    private var loadDataJob: Job? = null

    init {
        loadData()
    }

    fun showDeleteWarningDialog(show: Boolean) {
        updateUiState {
            it.copy(showDeleteWarningDialog = show)
        }
    }

    fun setExpandedActionGroup(group: GestureActionGroup?) {
        updateUiState {
            it.copy(expandedActionGroup = group)
        }
    }

    fun deleteGestureButton() {
        viewModelScope.launch {
            loadDataJob?.cancel()
            GestureConfig.updateButtons {
                it.toMutableList().apply {
                    removeAll { item ->
                        item.id == uiState.gestureButton?.id
                    }
                }
            }
            hunoia.luno.domain.gridpanel.PanelResources.cleanup()
        }.invokeOnCompletion {
            finish()
        }
    }

    /** 拖动调整的 live 更新：同 [updateButtonLive]，另置 adjusting 标志（落盘等 onGestureButtonAdjustFinish）。 */
    private fun updateBoundsLive(fieldUpdate: GestureButton.() -> GestureButton) {
        updateUiState {
            val l = it.gestureButtons.toMutableList().also { list ->
                list.forEachIndexed { index, b ->
                    if (b.id == gestureButtonSettings.buttonId) {
                        list[index] = b.fieldUpdate()
                    }
                }
            }
            it.copy(
                gestureButtons = l,
                isGestureButtonAdjusting = true
            )
        }
    }

    fun onGestureButtonWidthChange(width: Float) = updateBoundsLive {
        copy(bounds = bounds.copy(width = width.coerceIn(MinGestureButtonLength, maxGestureButtonWidth(this))))
    }

    fun onGestureButtonHeightChange(height: Float) = updateBoundsLive {
        copy(bounds = bounds.copy(height = height.coerceIn(MinGestureButtonLength, maxGestureButtonHeight(this))))
    }

    fun onGestureButtonXChange(x: Float) = updateBoundsLive {
        copy(bounds = bounds.copy(x = x.coerceIn(0f, (1f - bounds.width).coerceAtLeast(0f))))
    }

    fun onGestureButtonYChange(y: Float) = updateBoundsLive {
        copy(bounds = bounds.copy(y = y.coerceIn(0f, (1f - bounds.height).coerceAtLeast(0f))))
    }

    fun onGestureButtonAdjustFinish() {
        updateUiState {
            it.copy(isGestureButtonAdjusting = false)
        }
        saveSettings()
    }


    fun updateGestureButtonAngle(angle: GestureButtonAngle) = updateButton { copy(angle = angle) }

    private fun updateButtonLive(fieldUpdate: GestureButton.() -> GestureButton) {
        updateUiState {
            val l = it.gestureButtons.toMutableList().also { list ->
                list.forEachIndexed { index, b ->
                    if (b.id == gestureButtonSettings.buttonId) {
                        list[index] = b.fieldUpdate()
                    }
                }
            }
            it.copy(gestureButtons = l)
        }
    }

    private fun updateButton(fieldUpdate: GestureButton.() -> GestureButton) {
        updateButtonLive(fieldUpdate)
        saveSettings()
    }

    fun onSlideVibrateChange(value: Boolean) = updateButton { copy(slideVibrate = value) }
    fun onLongSlideVibrateChange(value: Boolean) = updateButton { copy(longSlideVibrate = value) }
    fun onTapVibrateChange(value: Boolean) = updateButton { copy(tapVibrate = value) }
    fun onLongPressVibrateChange(value: Boolean) = updateButton { copy(longPressVibrate = value) }
    fun onSlideHoldVibrateChange(value: Boolean) = updateButton { copy(slideHoldVibrate = value) }
    fun onLongSlideHoldVibrateChange(value: Boolean) = updateButton { copy(longSlideHoldVibrate = value) }
    fun onSlideTriggerDistanceChange(value: Float) = updateButtonLive {
        val maxSlide = (longSlideTriggerDistance - MinGestureSlideDistanceGap)
            .coerceIn(MinSlideTriggerDistance, MaxSlideTriggerDistance)
        val slide = value.toInt().coerceIn(MinSlideTriggerDistance, maxSlide)
        copy(slideTriggerDistance = slide)
    }
    fun onLongSlideTriggerDistanceChange(value: Float) = updateButtonLive {
        val minLongSlide = (slideTriggerDistance + MinGestureSlideDistanceGap)
            .coerceIn(MinLongSlideTriggerDistance, MaxLongSlideTriggerDistance)
        val longSlide = value.toInt().coerceIn(minLongSlide, MaxLongSlideTriggerDistance)
        copy(longSlideTriggerDistance = longSlide)
    }
    fun onLongPressTriggerDelayMsChange(value: Float) = updateButtonLive { copy(longPressTriggerDelayMs = value.toLong()) }
    fun onDoubleTapTriggerDelayMsChange(value: Float) = updateButtonLive { copy(doubleTapTriggerDelayMs = value.toLong()) }
    fun onSlideHoldTriggerDelayMsChange(value: Float) = updateButtonLive { copy(slideHoldTriggerDelayMs = value.toLong()) }
    fun onLongSlideHoldTriggerDelayMsChange(value: Float) = updateButtonLive { copy(longSlideHoldTriggerDelayMs = value.toLong()) }

    fun onGestureButtonMirrorHorizontalChange(value: Boolean) = updateButton { copy(mirrorHorizontal = value) }

    fun saveSettings() {
        viewModelScope.launch {
            launch {
                GestureConfig.updateButtons { uiState.gestureButtons }
            }
        }
    }

    private fun maxGestureButtonWidth(button: GestureButton): Float {
        val byBounds = (1f - button.bounds.x).coerceAtLeast(MinGestureButtonLength)
        val byArea = MaxGestureButtonArea / button.bounds.height.coerceAtLeast(MinGestureButtonLength)
        return minOf(byBounds, byArea).coerceAtLeast(MinGestureButtonLength)
    }

    private fun maxGestureButtonHeight(button: GestureButton): Float {
        val byBounds = (1f - button.bounds.y).coerceAtLeast(MinGestureButtonLength)
        val byArea = MaxGestureButtonArea / button.bounds.width.coerceAtLeast(MinGestureButtonLength)
        return minOf(byBounds, byArea).coerceAtLeast(MinGestureButtonLength)
    }

    private fun loadData() {
        val gestureButtonSettings = gestureButtonSettings
        loadDataJob = viewModelScope.launch {
            launch {
                GestureConfig
                    .buttons
                    .collectLatest { items ->
                        val button = items.find { it.id == gestureButtonSettings.buttonId }
                        updateUiState {
                            it.copy(
                                gestureButtons = items,
                                mirrorHorizontal = button?.mirrorHorizontal ?: true,
                            )
                        }
                    }
            }
        }
    }
}
