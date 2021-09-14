package pw.binom.testContainer

import pw.binom.atomic.AtomicReference
import pw.binom.io.Closeable
import pw.binom.network.TcpServerConnection
import pw.binom.network.UdpConnection

class Container(
    val image: String,
    val environments: Map<String, String>,
    val ports: List<Port>,
    val reuse: Boolean,
) : Closeable {
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

    internal fun prepare() {
        TestContainerThread.start(this)
        TestContainerThread.registerForReuse(this)
    }

    override fun close() {
        TestContainerThread.stop(this)
    }
}

operator fun <T : Container, R> T.invoke(func: (T) -> R): R {
    if (id == null) {
        prepare()
    }
    return try {
        func(this)
    } finally {
        if (!reuse) {
            close()
        }
    }
}