package hunoia.luno

import hunoia.luno.core.Events
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsTest {

    @Test
    fun subscribeAndDispatch() {
        val results = mutableListOf<String>()
        val subscriber: (TestEvent) -> Unit = { results.add(it.value) }

        Events.subscribe(TestEvent::class, subscriber)
        Events.dispatch(TestEvent("hello"))

        assertEquals(listOf("hello"), results)
    }

    @Test
    fun subscribeMultipleListeners() {
        val results1 = mutableListOf<String>()
        val results2 = mutableListOf<String>()
        val sub1: (TestEvent) -> Unit = { results1.add(it.value) }
        val sub2: (TestEvent) -> Unit = { results2.add(it.value) }

        Events.subscribe(TestEvent::class, sub1)
        Events.subscribe(TestEvent::class, sub2)
        Events.dispatch(TestEvent("multi"))

        assertEquals(listOf("multi"), results1)
        assertEquals(listOf("multi"), results2)
    }

    @Test
    fun unsubscribe_stopsReceiving() {
        val results = mutableListOf<String>()
        val subscriber: (TestEvent) -> Unit = { results.add(it.value) }

        Events.subscribe(TestEvent::class, subscriber)
        Events.dispatch(TestEvent("before"))
        Events.unsubscribe(TestEvent::class, subscriber)
        Events.dispatch(TestEvent("after"))

        assertEquals(listOf("before"), results)
    }

    @Test
    fun unsubscribe_notSubscribed_doesNotThrow() {
        val subscriber: (TestEvent) -> Unit = {}
        Events.unsubscribe(TestEvent::class, subscriber)
    }

    @Test
    fun dispatch_wrongType_ignored() {
        val results = mutableListOf<String>()
        val subscriber: (TestEvent) -> Unit = { results.add(it.value) }

        Events.subscribe(TestEvent::class, subscriber)
        Events.dispatch(OtherEvent("ignored"))

        assertTrue(results.isEmpty())
    }

    @Test
    fun dispatch_multipleEvents() {
        val results = mutableListOf<String>()
        val subscriber: (TestEvent) -> Unit = { results.add(it.value) }

        Events.subscribe(TestEvent::class, subscriber)
        Events.dispatch(TestEvent("a"))
        Events.dispatch(TestEvent("b"))
        Events.dispatch(TestEvent("c"))

        assertEquals(listOf("a", "b", "c"), results)
    }

    @Test
    fun subscribeSameListenerTwice() {
        val results = mutableListOf<String>()
        val subscriber: (TestEvent) -> Unit = { results.add(it.value) }

        Events.subscribe(TestEvent::class, subscriber)
        Events.subscribe(TestEvent::class, subscriber)
        Events.dispatch(TestEvent("once"))

        assertEquals(2, results.size)
    }

    @Test
    fun subscribeAndUnsubscribeAllCleansUp() {
        val results = mutableListOf<String>()
        val subscriber: (TestEvent) -> Unit = { results.add(it.value) }

        Events.subscribe(TestEvent::class, subscriber)
        Events.unsubscribe(TestEvent::class, subscriber)
        Events.dispatch(TestEvent(""))

        assertTrue(results.isEmpty())
    }

    private class TestEvent(val value: String)
    private class OtherEvent(val value: String)
}
