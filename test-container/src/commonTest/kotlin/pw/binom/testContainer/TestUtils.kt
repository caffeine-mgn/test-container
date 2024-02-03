package pw.binom.testContainer

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
fun binomTest(f: suspend CoroutineScope.() -> Unit) =
    runTest(timeout = 30.seconds) {
        withContext(Dispatchers.Default) {
            f()
        }
    }

suspend fun realDelay(timeMillis: Long) {
    withContext(Dispatchers.Default) {
        delay(timeMillis)
    }
}
