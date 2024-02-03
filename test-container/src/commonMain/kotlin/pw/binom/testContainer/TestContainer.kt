package pw.binom.testContainer

import pw.binom.atomic.AtomicReference
import pw.binom.docker.dto.CreateContainerRequest
import pw.binom.docker.dto.Env
import pw.binom.docker.dto.HostConfig
import pw.binom.docker.dto.PortBind
import pw.binom.network.TcpServerConnection
import pw.binom.network.UdpConnection

open class TestContainer(
    val image: String,
    val environments: Map<String, String> = emptyMap(),
    val ports: List<Port> = emptyList(),
    val reuse: Boolean = false,
    val cmd: List<String> = emptyList(),
    val entryPoint: String? = null,
) {
    internal var idAtomic = AtomicReference<String?>(null)
    val id
        get() = idAtomic.getValue()

    enum class PortType(val code: String) {
        TCP("tcp"),
        UDP("udp"),
    }

    class Port(
        val type: PortType = PortType.TCP,
        val internalPort: Int,
        val externalPort: Int = when (type) {
            PortType.TCP -> TcpServerConnection.randomPort()
            PortType.UDP -> UdpConnection.randomPort()
        }
    )

    open suspend fun start() {
        if (id != null) {
            throw IllegalStateException("Container already started")
        }
//        TestContainerThread.start(this)
        Globals.dockerClient.pullImage(image = image)
        val createdContainer = Globals.dockerClient.createContainer(
            CreateContainerRequest(
                image = image,
                exposedPorts = ports.associate { "${it.internalPort}/${it.type.code}" to mapOf() },
                env = environments.map { Env(name = it.key, value = it.value) },
                hostConfig = HostConfig(
                    portBindings = ports.associate {
                        "${it.internalPort}/${it.type.code}" to listOf(
                            PortBind(it.externalPort.toString())
                        )
                    },
                    autoRemove = false,
                ),
                cmd = cmd,
                entrypoint = entryPoint,
            )
        )
        val ryuk = Globals.getRyukClient()
        Globals.dockerClient.startContainer(createdContainer.id)
        idAtomic.setValue(createdContainer.id)
        ryuk.register(images = listOf(mapOf("id" to createdContainer.id)))
    }

    open suspend fun stop() {
        if (id == null) {
            throw IllegalStateException("Container already stopped")
        }
        val containerId = id ?: throw IllegalStateException("Container \"${image}\" not started")
        Globals.dockerClient.stopContainer(containerId)
        idAtomic.setValue(null)
    }
}

suspend operator fun <T : TestContainer, R> T.invoke(func: suspend (T) -> R): R {
    if (id == null) {
        start()
    }
    return try {
        func(this)
    } finally {
        if (!reuse) {
            stop()
        }
    }
}

suspend fun <T> use(vararg testContainers: TestContainer, func: suspend () -> T): T {
    testContainers.forEach {
        if (it.id == null) {
            it.start()
        }
    }
    return try {
        func()
    } finally {
        testContainers.forEach {
            if (!it.reuse) {
                it.stop()
            }
        }
    }
}
