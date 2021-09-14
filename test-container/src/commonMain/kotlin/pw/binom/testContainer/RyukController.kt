package pw.binom.testContainer

import pw.binom.docker.*
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher
import pw.binom.docker.Container as DockerContainer

class RyukController private constructor(val dockerClient: DockerClient, val container: DockerContainer) {
    companion object {
        suspend fun create(dockerClient: DockerClient): RyukController {
            val created1 = dockerClient.getContainers(names = listOf("testcontainers-ryuk-${RUN_INSTANCE}"))
                .firstOrNull()
            if (created1 != null) {
                return RyukController(dockerClient, created1)
            }

            val nd = NetworkDispatcher()
            val srv = nd.bindTcp(NetworkAddress.Immutable(host = "127.0.0.1", port = 0))
            val freePort = srv.port
            srv.close()
            nd.close()
            dockerClient.pullImage("testcontainers/ryuk:0.3.2")
            val created = dockerClient.createContainer(
                name = "testcontainers-ryuk-$RUN_INSTANCE",
                arguments = CreateContainerRequest(
                    image = "testcontainers/ryuk:0.3.2",
                    labels = mapOf("session" to RUN_INSTANCE.toString()),
                    exposedPorts = mapOf("8080/tcp" to mapOf()),
                    hostConfig = HostConfig(
                        binds = listOf("//var/run/docker.sock:/var/run/docker.sock"),
                        portBindings = mapOf("8080/tcp" to listOf(PortBind(freePort.toString()))),
                        privileged = true,
                        autoRemove = true
                    ),
                    volumes = mapOf("/var/run/docker.sock" to mapOf()),
                )
            )
            dockerClient.startContainer(id = created.id)
            return RyukController(
                dockerClient,
                dockerClient.getContainers(names = listOf("testcontainers-ryuk-${RUN_INSTANCE}")).first()
            )
        }
    }

    val port: Int
        get() = container.ports!!.first().PublicPort!!

    private suspend fun find() =
        dockerClient.getContainers(names = listOf("testcontainers-ryuk-${RUN_INSTANCE}"))
            .firstOrNull()

    suspend fun destory() {
        dockerClient.stopContainer(id = container.id)
        dockerClient.remove(id = container.id)
    }
}