package pw.binom.testContainer

import kotlinx.coroutines.Dispatchers
import pw.binom.io.AsyncCloseable
import pw.binom.io.bufferedReader
import pw.binom.io.bufferedWriter
import pw.binom.network.*

class RyukClient(val connection: TcpConnection) : AsyncCloseable {
    private val writer = connection.bufferedWriter(closeParent = false)
    private val reader = connection.bufferedReader(closeParent = false)

    companion object {
        suspend fun connect(networkDispatcher: NetworkCoroutineDispatcher = Dispatchers.Network, addr: NetworkAddress) =
            RyukClient(networkDispatcher.tcpConnect(addr))
    }

    override suspend fun asyncClose() {
        reader.asyncClose()
        writer.asyncClose()
        connection.asyncClose()
    }

    suspend fun register(images: List<Map<String, String>>) {
        images.forEach {
            val cmd = it.entries.map { "${it.key}=${it.value}" }.joinToString("&")
            writer.append(cmd).append("\n\r")
            writer.flush()
            reader.readln()
        }
    }
}
