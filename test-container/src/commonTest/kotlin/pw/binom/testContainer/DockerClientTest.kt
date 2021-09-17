package pw.binom.testContainer

import pw.binom.concurrency.DeadlineTimer
import pw.binom.db.postgresql.async.PGConnection
import pw.binom.docker.DockerClient
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.use
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DockerClientTest {
    class PostgresTestContainer(reuse: Boolean) : TestContainer(
        image = "postgres:11",
        environments = mapOf(
            "POSTGRES_USER" to "postgres",
            "POSTGRES_PASSWORD" to "postgres",
            "POSTGRES_DB" to "tmpDb"
        ),
        ports = listOf(
            TestContainer.Port(internalPort = 5432)
        ),
        reuse = reuse,
    )

    val pgReuse = PostgresTestContainer(reuse = true)
    val pgNonReuse = PostgresTestContainer(reuse = false)

    @Test
    fun resueTest() {
        pgReuse {
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
        assertNotNull(pgReuse.id)
        NetworkDispatcher().use {
            it.runSingle {
                HttpClient.create(it).use { httpClient ->
                    val dockerClient = DockerClient(httpClient)
                    assertTrue(dockerClient.getContainers(ids = listOf(pgReuse.id!!)).any())
                }
            }
        }
    }

    @Test
    fun nonReuseTest(){
        pgNonReuse {
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
        assertNull(pgReuse.id)
    }
}