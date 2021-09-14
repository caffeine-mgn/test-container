package pw.binom.testContainer

import pw.binom.concurrency.DeadlineTimer
import pw.binom.db.postgresql.async.PGConnection
import pw.binom.io.use
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class DockerClientTest {

    val pg = pw.binom.testContainer.Container(
        image = "postgres:11",
        environments = mapOf(
            "POSTGRES_USER" to "postgres",
            "POSTGRES_PASSWORD" to "postgres",
            "POSTGRES_DB" to "tmpDb"
        ),
        ports = listOf(
            Container.Port(internalPort = 5432)
        ),
        reuse = true
    )

    @OptIn(ExperimentalTime::class)
    @Test
    fun test2() {
        pg {
            val nd = NetworkDispatcher()
            val dt = DeadlineTimer.create()
            nd.runSingle {
                dt.delay(Duration.seconds(5))
                val pgAddress = NetworkAddress.Immutable(host = "127.0.0.1", port = it.ports[0].externalPort)
                val connection = PGConnection.connect(
                    address = pgAddress,
                    dataBase = "tmpDb",
                    userName = "postgres",
                    password = "postgres",
                    networkDispatcher = nd,
                )
                connection.createStatement().use {
                    it.executeUpdate("create table if not exists test_table (id bigint primary key)")
                }
            }
        }
    }
}