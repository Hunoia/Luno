package hunoia.luno.domain.runtime

import hunoia.luno.facade.RuntimeFacade
import android.content.Context
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.service.SendOutcome
object RuntimeActions : RuntimeFacade {

    override suspend fun dispatch(action: StoredAction, origin: DispatchOrigin): Boolean =
        hunoia.luno.service.DispatchClient.send(action, origin = origin) == SendOutcome.Delivered

    override fun restoreServicesOnce(context: Context) =
        hunoia.luno.service.ServiceAutoStart.restoreOnce(context)
}
