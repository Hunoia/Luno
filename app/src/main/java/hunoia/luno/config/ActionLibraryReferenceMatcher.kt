package hunoia.luno.config

import hunoia.luno.config.model.Action
import hunoia.luno.config.model.actionLibraryRefId

object ActionLibraryReferenceMatcher {

    fun isReferenceTo(action: Action, entryId: String): Boolean {
        return action.actionLibraryRefId() == entryId
    }
}
