package hunoia.luno.domain.runtime

import hunoia.luno.facade.RuntimeFacade
import android.content.Context
import hunoia.luno.core.domain.StoredAction
object RuntimeActions : RuntimeFacade {

    override fun dispatch(action: StoredAction): Boolean =
        hunoia.luno.service.DispatchClient.dispatch(action)

    override fun restoreServicesOnce(context: Context) =
        hunoia.luno.service.ServiceAutoStart.restoreOnce(context)
}
