package pw.binom.testContainer

import kotlinx.coroutines.*
import pw.binom.Environment
import pw.binom.OS
import pw.binom.atomic.AtomicBoolean
import pw.binom.concurrency.SpinLock
import pw.binom.concurrency.synchronize
import pw.binom.docker.DockerClient
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.ClosedException
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.create
import pw.binom.network.Network
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnectUnixSocket
import pw.binom.os

class UnixSocketFactory(val path: String) : ConnectionFactory {
    override suspend fun connect(
        networkManager: NetworkManager,
        schema: String,
        host: String,
        port: Int
    ): AsyncChannel =
        networkManager.tcpConnectUnixSocket(path)
}

val UNIX_SOCKET_FACTORY = UnixSocketFactory("/var/run/docker.sock")

var GLOBAL_DOCKER_CONNECTION_FACTORY = ConnectionFactory { networkManager, schema, host, port ->
    val factory = when (Environment.os) {
        OS.ANDROID, OS.WINDOWS -> ConnectionFactory.DEFAULT
        else -> UNIX_SOCKET_FACTORY
    }
    factory.connect(
        networkManager = networkManager,
        schema = schema,
        host = host,
        port = port
    )
}

object Globals : AsyncCloseable {
    private var closed = AtomicBoolean(false)
    private fun checkClosed() {
        if (closed.getValue()) {
            throw ClosedException()
        }
    }

    private var httpInited = false
    val httpClient by lazy {
        checkClosed()
        val httpClient = HttpClient.create(connectionFactory = GLOBAL_DOCKER_CONNECTION_FACTORY)
        httpInited = true
        httpClient
    }
    val dockerClient by lazy {
        checkClosed()
        DockerClient(httpClient)
    }
    private val lock = SpinLock()
    private var ryukClientJob: Deferred<RyukClient>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getRyukClient(): RyukClient {
        checkClosed()
        lock.synchronize {
            if (ryukClientJob == null) {
                ryukClientJob = createRyukClient()
            }
            return ryukClientJob!!.await()
        }
    }

    private fun createRyukClient() = run {
        checkClosed()
        GlobalScope.async(Dispatchers.Network) {
            val ryukController = RyukController.create(dockerClient)
            RyukClient.connect(
                addr = NetworkAddress.Immutable("127.0.0.1", ryukController.port)
            )
        }
    }

    override suspend fun asyncClose() {
        checkClosed()
        lock.synchronize {
            ryukClientJob?.also {
                when {
                    it.isCompleted -> it.await().asyncClose()
                    it.isActive -> it.cancel()
                }
            }
        }
        if (httpInited) {
            httpClient.close()
        }
        closed.setValue(true)
    }
}
