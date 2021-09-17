package pw.binom.testContainer

import pw.binom.FreezableFuture
import pw.binom.concurrency.Worker
import pw.binom.concurrency.create
import pw.binom.concurrency.joinAndGetOrThrow
import pw.binom.coroutine.AsyncQueue
import pw.binom.doFreeze
import pw.binom.docker.*
import pw.binom.io.httpClient.HttpClient
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher

internal object TestContainerThread {
    private val w = Worker.create()
    private val q = AsyncQueue<Message>()

    init {
        doFreeze()
        w.execute(this) { self ->
            val nd = NetworkDispatcher()
            val httpClient = HttpClient.create(nd)
            val dockerClient = DockerClient(httpClient)
            nd.startCoroutine {
                val ryukController = RyukController.create(dockerClient)
                val ryukClient = RyukClient.connect(
                    networkDispatcher = nd,
                    addr = NetworkAddress.Immutable("127.0.0.1", ryukController.port)
                )
//                ryukClient.register(listOf(mapOf("id" to ryukController.container.id)))
                try {
                    while (true) {
                        val msg = q.pop()
                        when (msg) {
                            is Message.StartContainer -> startContainer(
                                dockerClient = dockerClient,
                                container = msg.container,
                                future = msg.future
                            )
                            is Message.StopContainer -> stopContainer(
                                dockerClient = dockerClient,
                                container = msg.container,
                                future = msg.future
                            )
                            is Message.RegisterForReuseContainer -> registerForReuse(
                                ryukClient = ryukClient,
                                container = msg.container,
                                future = msg.future
                            )
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    ryukClient.asyncClose()
                }
            }
            try {
                while (true) {
                    nd.select()
                }
            } finally {
                httpClient.close()
                nd.close()
            }
        }
    }

    private suspend fun registerForReuse(
        ryukClient: RyukClient,
        container: TestContainer,
        future: FreezableFuture<Unit>
    ) {
        val containerId = container.id
        if (containerId == null) {
            future.resume(Result.failure(IllegalStateException("Container \"${container.image}\" not started")))
            return
        }
        try {
            ryukClient.register(images = listOf(mapOf("id" to containerId)))
            future.resume(Result.success(Unit))
        } catch (e: Throwable) {
            future.resume(Result.failure(e))
        }
    }

    private suspend fun startContainer(
        dockerClient: DockerClient,
        container: TestContainer,
        future: FreezableFuture<Unit>
    ) {
        try {
            dockerClient.pullImage(image = container.image)
            val createdContainer = dockerClient.createContainer(
                CreateContainerRequest(
                    image = container.image,
                    exposedPorts = container.ports
                        .map { "${it.internalPort}/${it.type.code}" to mapOf<String, String>() }
                        .toMap(),
                    env = container.environments.map { Env(name = it.key, value = it.value) },
                    hostConfig = HostConfig(
                        portBindings = container.ports
                            .map { "${it.internalPort}/${it.type.code}" to listOf(PortBind(it.externalPort.toString())) }
                            .toMap(),
                        autoRemove = true,
                    )
                )
            )
            dockerClient.startContainer(createdContainer.id)
            container.idAtomic.value = createdContainer.id
            future.resume(Result.success(Unit))
        } catch (e: Throwable) {
            future.resume(Result.failure(e))
        }
    }

    private suspend fun stopContainer(
        dockerClient: DockerClient,
        container: TestContainer,
        future: FreezableFuture<Unit>
    ) {
        try {
            val containerId = container.id
            if (containerId == null) {
                future.resume(Result.failure(IllegalStateException("Container \"${container.image}\" not started")))
                return
            }
            dockerClient.stopContainer(containerId)
            future.resume(Result.success(Unit))
            container.idAtomic.value = null
        } catch (e: Throwable) {
            future.resume(Result.failure(e))
        }
    }

    fun start(container: TestContainer) {
        val f = FreezableFuture<Unit>()
        q.push(
            Message.StartContainer(
                container = container,
                future = f,
            )
        )
        f.joinAndGetOrThrow()
    }

    fun stop(container: TestContainer) {
        val f = FreezableFuture<Unit>()
        q.push(
            Message.StopContainer(
                container = container,
                future = f,
            )
        )
        f.joinAndGetOrThrow()
    }

    fun registerForReuse(container: TestContainer) {
        val f = FreezableFuture<Unit>()
        q.push(
            Message.RegisterForReuseContainer(
                container = container,
                future = f,
            )
        )
        f.joinAndGetOrThrow()
    }

    private sealed interface Message {
        data class StartContainer(val container: TestContainer, val future: FreezableFuture<Unit>) : Message {

        }

        data class StopContainer(val container: TestContainer, val future: FreezableFuture<Unit>) : Message {

        }

        data class RegisterForReuseContainer(val container: TestContainer, val future: FreezableFuture<Unit>) :
            Message {

        }
    }
}