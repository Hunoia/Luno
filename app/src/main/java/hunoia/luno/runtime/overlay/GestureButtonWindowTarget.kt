package hunoia.luno.runtime.overlay

import hunoia.luno.config.model.GestureButton

internal data class GestureButtonWindowTarget(
    val sourceButton: GestureButton,
    val windowButton: GestureButton,
)
