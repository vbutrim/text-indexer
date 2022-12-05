package com.vbutrim.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration

suspend fun executePeriodically(delayTime: Duration, action: suspend CoroutineScope.() -> Unit) = coroutineScope {
    while (isActive) {
        delay(delayTime.toMillis())
        action()
    }
}