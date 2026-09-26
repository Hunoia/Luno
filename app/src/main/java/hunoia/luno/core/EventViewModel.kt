package hunoia.luno.core

import androidx.lifecycle.ViewModel
import hunoia.luno.core.Events
import kotlin.reflect.KClass



fun <T : Any> ViewModel.subscribeEvent(eventClass: KClass<T>, block: (T) -> Unit) {
    addCloseable {
        Events.unsubscribe(eventClass, block)
    }
    Events.subscribe(eventClass, block)
}
