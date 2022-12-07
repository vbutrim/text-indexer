package com.vbutrim.coroutine

import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExecutePeriodicallyKtTest {
    @Test
    fun shouldExecutePeriodically() = runTest {
        // Given
        val counter = AtomicInteger()

        // When
        val backgroundJob = launch(StandardTestDispatcher(testScheduler)) {
            executePeriodically(Duration.ofMinutes(10)) {
                counter.incrementAndGet()
            }
        }

        delay(Duration.ofMinutes(13).toMillis())
        backgroundJob.cancel()

        // Then
        Assertions.assertEquals(1, counter.get())
    }
}