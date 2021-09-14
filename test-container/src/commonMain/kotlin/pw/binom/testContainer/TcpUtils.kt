package pw.binom.testContainer

import pw.binom.concurrency.DeadlineTimer
import pw.binom.io.IOException
import pw.binom.io.use
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
suspend fun NetworkDispatcher.waitTcp(networkAddress: NetworkAddress, timeout: Duration? = null) {
    val now = TimeSource.Monotonic.markNow()
    DeadlineTimer.create().use { dt ->
        do {
            try {
                tcpConnect(networkAddress).asyncClose()
                println("$networkAddress done")
                break
            } catch (e: IOException) {
                if (timeout != null && now.elapsedNow() > timeout) {
                    throw IOException("Wait tcp connection timeout")
                }
                dt.delay(Duration.milliseconds(1000))
            }
            println("Retry $networkAddress")
        } while (true)
    }
}