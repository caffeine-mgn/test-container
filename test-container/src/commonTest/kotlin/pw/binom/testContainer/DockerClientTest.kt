package pw.binom.testContainer

import kotlinx.coroutines.delay
import pw.binom.db.postgresql.async.PGConnection
import pw.binom.docker.DockerClient
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.create
import pw.binom.io.socket.NetworkAddress
import pw.binom.io.use
import pw.binom.io.useAsync
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

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
        reuse = reuse
    )

    val pgReuse = PostgresTestContainer(reuse = true)
    val pgNonReuse = PostgresTestContainer(reuse = false)

    @Test
    fun resueTest() = binomTest {
        pgReuse {
            HttpClient.create(connectFactory = GLOBAL_DOCKER_CONNECTION_FACTORY).use { httpClient ->
                val dockerClient = DockerClient(httpClient)
                assertTrue(dockerClient.getContainers(ids = listOf(pgReuse.id!!)).any())
            }
            delay(5.seconds)
            val pgAddress = NetworkAddress.create(host = "127.0.0.1", port = it.ports[0].externalPort)
            val connection = PGConnection.connect(
                address = pgAddress,
                dataBase = "tmpDb",
                userName = "postgres",
                password = "postgres"
            )
            connection.createStatement().useAsync {
                it.executeUpdate("create table if not exists test_table (id bigint primary key)")
            }
        }
        assertNotNull(pgReuse.id)
    }

    @Test
    fun nonReuseTest() = binomTest {
        pgNonReuse {
            try {
                delay(5.seconds)
                val pgAddress = NetworkAddress.create(host = "127.0.0.1", port = it.ports[0].externalPort)
                val connection = PGConnection.connect(
                    address = pgAddress,
                    dataBase = "tmpDb",
                    userName = "postgres",
                    password = "postgres"
                )
                connection.createStatement().useAsync {
                    it.executeUpdate("create table if not exists test_table (id bigint primary key)")
                }
            } catch (e:Throwable) {
                e.printStackTrace()
                throw e
            }
        }
        assertNull(pgReuse.id)
    }
}
