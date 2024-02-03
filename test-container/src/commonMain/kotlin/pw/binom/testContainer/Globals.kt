package pw.binom.testContainer

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import pw.binom.Environment
import pw.binom.OS
import pw.binom.atomic.AtomicBoolean
import pw.binom.coroutines.AsyncReentrantLock
import pw.binom.docker.DockerClient
import pw.binom.getEnv
import pw.binom.io.AsyncChannel
import pw.binom.io.AsyncCloseable
import pw.binom.io.ClosedException
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.io.httpClient.HttpClient
import pw.binom.io.httpClient.create
import pw.binom.io.socket.InetNetworkAddress
import pw.binom.network.Network
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

internal val UNIX_SOCKET_ADDRESS = run {
    val customPath = Environment.getEnv("DOCKER_HOST")
    if (customPath?.startsWith("unix://") == true) {
        customPath.removePrefix("unix://")
    } else {
        when (Environment.os) {
            OS.LINUX, OS.MACOS -> "/var/run/docker.sock"
            OS.WINDOWS -> "//var/run/docker.sock"
            else -> throw IllegalStateException("Can't determinate path to docker sock")
        }
    }
}

val UNIX_SOCKET_FACTORY = run {
    if (Environment.os == OS.LINUX || Environment.os == OS.MACOS) {
        UnixSocketFactory(UNIX_SOCKET_ADDRESS)
    } else {
        null
    }
}

var GLOBAL_DOCKER_CONNECTION_FACTORY = object : ConnectionFactory {
    override suspend fun connect(
        networkManager: NetworkManager,
        schema: String,
        host: String,
        port: Int
    ): AsyncChannel {
        val factory = when (Environment.os) {
            OS.ANDROID, OS.WINDOWS -> ConnectionFactory.DEFAULT
            else -> UNIX_SOCKET_FACTORY
        }
        return factory!!.connect(
            networkManager = networkManager,
            schema = schema,
            host = host,
            port = port
        )
    }
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
        val httpClient = HttpClient.create(connectFactory = GLOBAL_DOCKER_CONNECTION_FACTORY)
        httpInited = true
        httpClient
    }
    val dockerClient by lazy {
        checkClosed()
        DockerClient(httpClient)
    }
    private val lock = AsyncReentrantLock()
    private var ryukClientJob: Deferred<RyukClient>? = null

    suspend fun getRyukClient(): RyukClient {
        checkClosed()
        return lock.synchronize {
            var ryukClientJob = ryukClientJob
            if (ryukClientJob == null) {
                ryukClientJob = GlobalScope.async(Dispatchers.Network) { createRyukClient() }
                this.ryukClientJob = ryukClientJob
            }
            ryukClientJob.await()
        }
    }

    private suspend fun createRyukClient(): RyukClient {
        checkClosed()
        val ryukController = RyukController.create(dockerClient)
        return RyukClient.connect(
            addr = InetNetworkAddress.create("127.0.0.1", ryukController.port)
        )
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
