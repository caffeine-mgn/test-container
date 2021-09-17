package pw.binom.testContainer

import pw.binom.atomic.AtomicReference
import pw.binom.network.TcpServerConnection
import pw.binom.network.UdpConnection

open class TestContainer(
    val image: String,
    val environments: Map<String, String> = emptyMap(),
    val ports: List<Port> = emptyList(),
    val reuse: Boolean = false,
) {
    internal var idAtomic = AtomicReference<String?>(null)
    val id
        get() = idAtomic.value

    enum class PortType(val code: String) {
        TCP("tcp"), UDP("udp"),
    }

    class Port(
        val type: PortType = PortType.TCP,
        val internalPort: Int,
        val externalPort: Int = when (type) {
            PortType.TCP -> TcpServerConnection.randomPort()
            PortType.UDP -> UdpConnection.randomPort()
        }
    )

    fun start() {
        TestContainerThread.start(this)
    }

    fun stop() {
        TestContainerThread.stop(this)
    }

    fun markAutoRemove() {
        check(id != null) { "Container not started" }
        TestContainerThread.registerForReuse(this)
    }
}

operator fun <T : TestContainer, R> T.invoke(func: (T) -> R): R {
    if (id == null) {
        start()
        if (reuse) {
            TestContainerThread.registerForReuse(this)
        }
    }
    return try {
        func(this)
    } finally {
        if (!reuse) {
            stop()
        }
    }
}