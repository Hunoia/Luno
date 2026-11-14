package hunoia.luno.runtime.overlay.text

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ScreenPreCaptureTest {

    @Test
    fun awaitTextWaitsForTheSnapshotCapturedAtDown() = runBlocking {
        val handle = ScreenPreCapture()
        val snapshot = TextSnapshot(capturedAtMs = 123L)
        var result: TextSnapshot? = null

        val waiter = launch { result = handle.awaitText(timeoutMs = 1_000L) }
        yield()
        handle.completeText(snapshot)
        waiter.join()

        assertSame(snapshot, result)
    }

    @Test
    fun awaitTextReturnsReadySnapshotWithoutWaiting() = runBlocking {
        val handle = ScreenPreCapture()
        val snapshot = TextSnapshot(capturedAtMs = 456L)

        handle.completeText(snapshot)

        assertSame(snapshot, handle.awaitText(timeoutMs = 0L))
    }

    @Test
    fun awaitTextTimesOutInsteadOfFallingBackToTriggerTime() = runBlocking {
        val handle = ScreenPreCapture()

        assertNull(handle.awaitText(timeoutMs = 0L))
    }
}
