import kotlinx.coroutines.*

fun main() = runBlocking { // this: CoroutineScope
    doWorld();
    println("Done") // main coroutine continues while a previous one is delayed
}

suspend fun doWorld() = coroutineScope {
    val job = launch {
        delay(2000L)
        println("World 2")
    }
    launch { // launch a new coroutine and continue
        delay(1_000L) // non-blocking delay for 1 second (default time unit is ms)
        println("World 1!") // print after delay
    }
    println("Hello, ")
    job.join()
    println("Job is done")
}