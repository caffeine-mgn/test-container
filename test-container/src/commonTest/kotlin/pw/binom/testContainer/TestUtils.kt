package pw.binom.testContainer

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
fun binomTest(f: suspend CoroutineScope.() -> Unit) =
    runTest {
        withContext(Dispatchers.Default) {
            f()
        }
    }

suspend fun realDelay(timeMillis: Long) {
    withContext(Dispatchers.Default) {
        delay(timeMillis)
    }
}
