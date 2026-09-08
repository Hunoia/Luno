package hunoia.luno

import hunoia.luno.shizuku.BatchFrozenResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchFrozenResultTest {

    @Test
    fun emptyBatch() {
        val result = BatchFrozenResult(
            requestedCount = 0,
            attemptedCount = 0,
            successCount = 0,
            failedCount = 0,
            fallbackTriggered = false
        )
        assertEquals(0, result.requestedCount)
        assertEquals(0, result.successCount)
    }

    @Test
    fun allSucceeded() {
        val result = BatchFrozenResult(
            requestedCount = 5,
            attemptedCount = 5,
            successCount = 5,
            failedCount = 0,
            fallbackTriggered = false
        )
        assertEquals(5, result.successCount)
        assertEquals(0, result.failedCount)
    }

    @Test
    fun partialFailure() {
        val result = BatchFrozenResult(
            requestedCount = 5,
            attemptedCount = 5,
            successCount = 3,
            failedCount = 2,
            fallbackTriggered = false
        )
        assertEquals(3, result.successCount)
        assertEquals(2, result.failedCount)
    }

    @Test
    fun fallbackResults() {
        val result = BatchFrozenResult(
            requestedCount = 5,
            attemptedCount = 0,
            successCount = 0,
            failedCount = 5,
            fallbackTriggered = true,
            fallbackAttemptedCount = 5,
            fallbackSuccessCount = 4,
            fallbackFailedCount = 1,
            errorSummary = "timeout"
        )
        assertEquals(5, result.fallbackAttemptedCount)
        assertEquals(4, result.fallbackSuccessCount)
        assertEquals(1, result.fallbackFailedCount)
        assertEquals("timeout", result.errorSummary)
    }
}
