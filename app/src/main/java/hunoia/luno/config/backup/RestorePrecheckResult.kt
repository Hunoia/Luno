package hunoia.luno.config.backup

import androidx.annotation.StringRes
import hunoia.luno.R

sealed interface RestorePrecheckResult {
    data object Passed : RestorePrecheckResult
    data class Failed(val reason: RestorePrecheckFailure) : RestorePrecheckResult
}

enum class RestorePrecheckFailure(@StringRes val stringRes: Int) {
    CannotReadFile(R.string.restore_precheck_cannot_read_file),
    InvalidFormat(R.string.restore_precheck_invalid_format),
    EmptyBackup(R.string.restore_precheck_empty_backup),
    IncompatibleVersion(R.string.restore_precheck_incompatible_version),
    VerificationFailed(R.string.restore_precheck_verification_failed),
}
