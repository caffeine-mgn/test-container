package pw.binom.testContainer

import pw.binom.FreezableFuture
import pw.binom.concurrency.Worker
import pw.binom.concurrency.joinAndGetOrThrow
import pw.binom.coroutine.AsyncQueue
import pw.binom.doFreeze
import pw.binom.docker.*
import pw.binom.docker.dto.CreateContainerRequest
import pw.binom.docker.dto.Env
import pw.binom.docker.dto.HostConfig
import pw.binom.docker.dto.PortBind
import pw.binom.getOrException
import pw.binom.io.ClosedException
import pw.binom.io.httpClient.HttpClient
import pw.binom.network.NetworkAddress

//internal object TestContainerThread {
//    private val w = Worker.create()
//    private val q = AsyncQueue<Message>()
//
//    init {
//        doFreeze()
//        w.execute(this) { self ->
//            val nd = NetworkDispatcher()
//            val httpClient = HttpClient.create(nd)
//            val dockerClient = DockerClient(httpClient)
//            val networkLoop = nd.startCoroutine {
//                try {
//                    val ryukController = RyukController.create(dockerClient)
//                    val ryukClient = RyukClient.connect(
//                        networkDispatcher = nd,
//                        addr = NetworkAddress.Immutable("127.0.0.1", ryukController.port)
//                    )
//                    try {
//                        while (true) {
//                            val msg = q.pop()
//                            when (msg) {
//                                is Message.StartContainer -> startContainer(
//                                    ryukClient = ryukClient,
//                                    dockerClient = dockerClient,
//                                    container = msg.container,
//                                    future = msg.future
//                                )
//                                is Message.StopContainer -> stopContainer(
//                                    dockerClient = dockerClient,
//                                    container = msg.container,
//                                    future = msg.future
//                                )
//                            }
//                        }
//                    } finally {
//                        ryukClient.asyncClose()
//                    }
//                } finally {
//                    while (q.isNotEmpty) {
//                        q.pop().future.resume(Result.failure(ClosedException()))
//                    }
//                }
//            }
//            try {
//                while (!networkLoop.isDone) {
//                    nd.select()
//                }
//                networkLoop.getOrException()
//            } catch (e: Throwable) {
//                e.printStackTrace()
//            } finally {
//                httpClient.close()
//                nd.close()
//                q.close()
//            }
//        }
//    }
//
//    private suspend fun registerForReuse(
//        ryukClient: RyukClient,
//        container: TestContainer,
//        future: FreezableFuture<Unit>
//    ) {
//        val containerId = container.id
//        if (containerId == null) {
//            future.resume(Result.failure(IllegalStateException("Container \"${container.image}\" not started")))
//            return
//        }
//        try {
//            ryukClient.register(images = listOf(mapOf("id" to containerId)))
//            future.resume(Result.success(Unit))
//        } catch (e: Throwable) {
//            future.resume(Result.failure(e))
//        }
//    }
//
//    private suspend fun startContainer(
//        dockerClient: DockerClient,
//        ryukClient: RyukClient,
//        container: TestContainer,
//        future: FreezableFuture<Unit>
//    ) {
//        try {
//            dockerClient.pullImage(image = container.image)
//            val createdContainer = dockerClient.createContainer(
//                CreateContainerRequest(
//                    image = container.image,
//                    exposedPorts = container.ports.associate { "${it.internalPort}/${it.type.code}" to mapOf() },
//                    env = container.environments.map { Env(name = it.key, value = it.value) },
//                    hostConfig = HostConfig(
//                        portBindings = container.ports.associate {
//                            "${it.internalPort}/${it.type.code}" to listOf(
//                                PortBind(it.externalPort.toString())
//                            )
//                        },
//                        autoRemove = false,
//                    ),
//                    cmd = container.cmd,
//                    entrypoint = container.entryPoint,
//                )
//            )
//            dockerClient.startContainer(createdContainer.id)
//            container.idAtomic.value = createdContainer.id
//            ryukClient.register(images = listOf(mapOf("id" to createdContainer.id)))
//            future.resume(Result.success(Unit))
//        } catch (e: Throwable) {
//            future.resume(Result.failure(e))
//        }
//    }
//
//    private suspend fun stopContainer(
//        dockerClient: DockerClient,
//        container: TestContainer,
//        future: FreezableFuture<Unit>
//    ) {
//        try {
//            val containerId = container.id
//            if (containerId == null) {
//                future.resume(Result.failure(IllegalStateException("Container \"${container.image}\" not started")))
//                return
//            }
//            dockerClient.stopContainer(containerId)
//            future.resume(Result.success(Unit))
//            container.idAtomic.value = null
//        } catch (e: Throwable) {
//            future.resume(Result.failure(e))
//        }
//    }
//
//    private fun checkClosed() {
//        if (q.isClosed) {
//            throw IllegalStateException("TestContainer Thread already closed")
//        }
//    }
//
//    fun start(container: TestContainer) {
//        checkClosed()
//        val f = FreezableFuture<Unit>()
//        q.push(
//            Message.StartContainer(
//                container = container,
//                future = f,
//            )
//        )
//        f.joinAndGetOrThrow()
//    }
//
//    fun stop(container: TestContainer) {
//        checkClosed()
//        val f = FreezableFuture<Unit>()
//        q.push(
//            Message.StopContainer(
//                container = container,
//                future = f,
//            )
//        )
//        f.joinAndGetOrThrow()
//    }
//
//    private sealed interface Message {
//        val future: FreezableFuture<Unit>
//
//        data class StartContainer(val container: TestContainer, override val future: FreezableFuture<Unit>) : Message
//
//        data class StopContainer(val container: TestContainer, override val future: FreezableFuture<Unit>) : Message
//    }
//}