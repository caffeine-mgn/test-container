package pw.binom.testContainer

import pw.binom.Environment
import pw.binom.OS
import pw.binom.docker.DockerClient
import pw.binom.docker.dto.CreateContainerRequest
import pw.binom.docker.dto.HostConfig
import pw.binom.docker.dto.PortBind
import pw.binom.network.TcpServerConnection
import pw.binom.os
import pw.binom.docker.dto.Container as DockerContainer

private var defaultImage = "testcontainers/ryuk:0.3.2"

class RyukController private constructor(val dockerClient: DockerClient, val container: DockerContainer) {
    companion object {
        suspend fun create(dockerClient: DockerClient): RyukController {
            val created1 =
                dockerClient.getContainers(names = listOf("testcontainers-ryuk-$RUN_INSTANCE")).firstOrNull()
            if (created1 != null) {
                return RyukController(dockerClient, created1)
            }
            val freePort = TcpServerConnection.randomPort()
            dockerClient.pullImage(defaultImage)
            val created = dockerClient.createContainer(
                name = "testcontainers-ryuk-$RUN_INSTANCE",
                arguments = CreateContainerRequest(
                    image = defaultImage,
                    labels = mapOf("session" to RUN_INSTANCE.toString()),
                    exposedPorts = mapOf("8080/tcp" to mapOf()),
                    hostConfig = HostConfig(
                        binds = listOf("$UNIX_SOCKET_ADDRESS:/var/run/docker.sock"),
                        portBindings = mapOf("8080/tcp" to listOf(PortBind(freePort.toString()))),
                        privileged = true,
                        autoRemove = true
                    ),
                    volumes = mapOf("/var/run/docker.sock" to mapOf()),
                )
            )
            dockerClient.startContainer(id = created.id)
            return RyukController(
                dockerClient, dockerClient.getContainers(names = listOf("testcontainers-ryuk-$RUN_INSTANCE")).first()
            )
        }
    }

    val port: Int
        get() = container.ports!!.first().PublicPort!!

    private suspend fun find() =
        dockerClient.getContainers(names = listOf("testcontainers-ryuk-$RUN_INSTANCE")).firstOrNull()

    suspend fun destory() {
        dockerClient.stopContainer(id = container.id)
        dockerClient.removeContainer(id = container.id)
    }
}
