package com.vbutrim.coroutine

import kotlinx.coroutines.*
import java.time.Duration

suspend fun scheduleRepeatedly(delayTime: Duration, action: suspend CoroutineScope.() -> Unit) = coroutineScope {
    while (isActive) {
        delay(delayTime.toMillis())
        launch(this.coroutineContext) {
            action()
        }
    }
}