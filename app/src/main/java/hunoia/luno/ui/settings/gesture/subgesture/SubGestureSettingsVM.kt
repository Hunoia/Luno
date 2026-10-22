package hunoia.luno.ui.settings.gesture.subgesture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.ui.settings.gesture.button.GestureActionGroup
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinGestureSlideDistanceGap
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureTriggerDistance
import hunoia.luno.core.AppContext
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.ui.navigation.SubGestureEditor
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureAngle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SubGestureSettingsUiState(
    val subGesture: SubGesture? = null,
    val allSubGestures: List<SubGesture>? = null,
    val showDeleteWarningDialog: Boolean = false,
    val showMirrorCopyDialog: Boolean = false,
    /** 触发动作列表展开态：放 VM，动作选择页返回重组合不丢。 */
    val expandedActionGroup: GestureActionGroup? = null,
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

    fun setExpandedActionGroup(group: GestureActionGroup?) {
        updateUiState { it.copy(expandedActionGroup = group) }
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
                angle = original.angle.copy(
                    boundaries = original.angle.boundaries.let { b ->
                        // 四向扇区顺序 [Up,Left,Down,Right]：镜像后新起点取对侧旧起点
                        // [Up<-Left, Left<-Up, Down<-Right, Right<-Down]
                        listOf(1, 0, 3, 2).map { i -> ((0.5f - b[i]) + 1f) % 1f }
                    }
                ),
            )
            GestureConfig.updateSubGestures { settings ->
                settings.copy(subGestures = settings.subGestures + mirrored)
            }
        }
    }

    fun deleteSubGesture() {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                settings.copy(
                    subGestures = settings.subGestures.filter { it.id != subGestureEditor.subGestureId }
                )
            }
            cleanReferences(subGestureEditor.subGestureId)
            hunoia.luno.domain.gridpanel.PanelResources.cleanup()
        }.invokeOnCompletion {
            finish()
        }
    }

    private suspend fun cleanReferences(deletedId: String) {
        GestureConfig.cleanSubGestureReferences(
            shouldRemove = { GestureConfig.matchesDeletedSubGesture(it, deletedId) }
        )
    }

    fun updateAngle(angle: SubGestureAngle) {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map { gesture ->
                        if (gesture.id == subGestureEditor.subGestureId) gesture.copy(angle = angle)
                        else gesture
                    }
                )
            }
        }
    }

    private fun updateSubGesture(fieldUpdate: SubGesture.() -> SubGesture) {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map { gesture ->
                        if (gesture.id == subGestureEditor.subGestureId) gesture.fieldUpdate()
                        else gesture
                    }
                )
            }
        }
    }

    /** 滑块拖动期间只改内存，避免每帧写 DataStore 触发跨进程 overlay 重装。 */
    private fun updateSubGestureLive(fieldUpdate: SubGesture.() -> SubGesture) {
        updateUiState { state ->
            val gesture = state.subGesture ?: return@updateUiState state
            state.copy(
                subGesture = gesture.fieldUpdate(),
                allSubGestures = state.allSubGestures?.map {
                    if (it.id == gesture.id) gesture.fieldUpdate() else it
                },
            )
        }
    }

    fun persistSubGesture() {
        val gesture = uiState.subGesture ?: return
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map {
                        if (it.id == gesture.id) gesture else it
                    }
                )
            }
        }
    }

    fun onSubSlideVibrateChange(value: Boolean) = updateSubGesture { copy(slideVibrate = value) }
    fun onSubLongSlideVibrateChange(value: Boolean) = updateSubGesture { copy(longSlideVibrate = value) }
    fun onSubSlideHoldVibrateChange(value: Boolean) = updateSubGesture { copy(slideHoldVibrate = value) }
    fun onSubLongSlideHoldVibrateChange(value: Boolean) = updateSubGesture { copy(longSlideHoldVibrate = value) }
    fun onSubTriggerDistanceChange(value: Float) = updateSubGestureLive {
        val maxSlide = (longSlideTriggerDistance - MinGestureSlideDistanceGap)
            .coerceIn(MinSubGestureTriggerDistance, MaxSubGestureTriggerDistance)
        val slide = value.toInt().coerceIn(MinSubGestureTriggerDistance, maxSlide)
        copy(triggerDistance = slide)
    }
    fun onSubLongSlideTriggerDistanceChange(value: Float) = updateSubGestureLive {
        val minLongSlide = (triggerDistance + MinGestureSlideDistanceGap)
            .coerceIn(MinSubGestureLongSlideTriggerDistance, MaxSubGestureLongSlideTriggerDistance)
        val longSlide = value.toInt().coerceIn(minLongSlide, MaxSubGestureLongSlideTriggerDistance)
        copy(longSlideTriggerDistance = longSlide)
    }
    fun onSubSlideHoldTriggerDelayMsChange(value: Float) = updateSubGestureLive { copy(slideHoldTriggerDelayMs = value.toLong()) }
    fun onSubLongSlideHoldTriggerDelayMsChange(value: Float) = updateSubGestureLive { copy(longSlideHoldTriggerDelayMs = value.toLong()) }
    fun onSubTimeoutMsChange(value: Float) = updateSubGestureLive { copy(timeoutMs = value.toLong()) }


    private fun loadData() {
        viewModelScope.launch {
            GestureConfig.subGestures.collectLatest { settings ->
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
    GestureDirection.Up -> GestureDirection.Up
    GestureDirection.Down -> GestureDirection.Down
}
