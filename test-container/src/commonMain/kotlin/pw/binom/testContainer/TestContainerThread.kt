package pw.binom.testContainer

import pw.binom.FreezableFuture
import pw.binom.concurrency.Worker
import pw.binom.concurrency.create
import pw.binom.concurrency.joinAndGetOrThrow
import pw.binom.coroutine.AsyncQueue
import pw.binom.doFreeze
import pw.binom.docker.*
import pw.binom.io.httpClient.HttpClient
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher

object TestContainerThread {
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
                        when (val msg = q.pop()) {
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

    private suspend fun registerForReuse(ryukClient: RyukClient, container: Container, future: FreezableFuture<Unit>) {
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
        container: Container,
        future: FreezableFuture<Unit>
    ) {
        try {
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

    private suspend fun stopContainer(dockerClient: DockerClient, container: Container, future: FreezableFuture<Unit>) {
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

    fun start(container: Container) {
        val f = FreezableFuture<Unit>()
        q.push(
            Message.StartContainer(
                container = container,
                future = f,
            )
        )
        f.joinAndGetOrThrow()
    }

    fun stop(container: Container) {
        val f = FreezableFuture<Unit>()
        q.push(
            Message.StopContainer(
                container = container,
                future = f,
            )
        )
        f.joinAndGetOrThrow()
    }

    fun registerForReuse(container: Container) {
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
        class StartContainer(val container: Container, val future: FreezableFuture<Unit>) : Message {

        }

        class StopContainer(val container: Container, val future: FreezableFuture<Unit>) : Message {

        }

        class RegisterForReuseContainer(val container: Container, val future: FreezableFuture<Unit>) : Message {

        }
    }
}