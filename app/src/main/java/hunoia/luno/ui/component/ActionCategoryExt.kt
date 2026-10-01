package hunoia.luno.ui.component

import androidx.annotation.StringRes
import hunoia.luno.R
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.action.model.Capability

@get:StringRes
val ActionCategory.displayNameRes: Int
    get() = when (this) {
        ActionCategory.SYSTEM -> R.string.action_category_system
        ActionCategory.APP -> R.string.action_category_app
        ActionCategory.INTENT -> R.string.action_category_intent
        ActionCategory.ACCESSIBILITY -> R.string.action_category_accessibility
        ActionCategory.PACKAGE -> R.string.action_category_package
        ActionCategory.SETTINGS -> R.string.action_category_settings
        ActionCategory.SYSTEMCMD -> R.string.action_category_system_cmd
        ActionCategory.SHELL -> R.string.action_category_shell
        ActionCategory.INTERNAL -> R.string.action_category_internal
    }

@get:StringRes
val Capability.displayNameRes: Int
    get() = when (this) {
        Capability.Accessibility -> R.string.action_capability_accessibility
        Capability.Shizuku -> R.string.action_capability_shizuku
        Capability.None -> R.string.action_none
    }
