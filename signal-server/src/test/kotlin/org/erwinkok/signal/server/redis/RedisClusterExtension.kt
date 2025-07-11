package org.erwinkok.signal.server.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.SlotHash
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import redis.embedded.RedisServer
import java.io.File
import java.net.ServerSocket

class RedisClusterExtension(
    val clusterSize: Int = 3,
) : Extension, BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {
    private val nodes = mutableListOf<RedisServer>()

    override fun beforeAll(context: ExtensionContext) {
        repeat(clusterSize) {
            nodes.add(startWithRetries(3))
        }
        assembleCluster()
    }

    override fun afterAll(context: ExtensionContext) {
        nodes.forEach { it.stop() }
    }

    override fun afterEach(context: ExtensionContext) {
    }

    override fun beforeEach(context: ExtensionContext) {
    }

    fun redisURIs(): List<RedisURI> {
        return nodes.map { redisUri(it) }
    }

    private fun startWithRetries(attemptsLeft: Int): RedisServer {
        try {
            val redisServer = buildClusterNode(getNextRedisClusterPort())
            redisServer.start()
            return redisServer
        } catch (e: Exception) {
            if (attemptsLeft == 0) {
                throw IllegalStateException("Could not start Redis Cluster", e)
            }
            Thread.sleep(500)
            return startWithRetries(attemptsLeft - 1)
        }
    }

    private fun buildClusterNode(port: Int): RedisServer {
        val clusterConfigFile = File.createTempFile("redis", ".conf")
        clusterConfigFile.deleteOnExit()
        return RedisServer.builder()
            .setting("cluster-enabled yes")
            .setting("cluster-config-file " + clusterConfigFile.absolutePath)
            .setting("cluster-node-timeout 5000")
            .setting("appendonly no")
            .setting("save \"\"")
            .setting("dir " + System.getProperty("java.io.tmpdir"))
            .port(port)
            .build()
    }

    fun getNextRedisClusterPort(): Int {
        repeat(11000) {
            ServerSocket(0).use { socket ->
                val port = socket.getLocalPort()
                if (port < 55535) {
                    return port
                }
            }
        }
        throw IllegalStateException("Could not find a free port for Redis Cluster")
    }

    private fun assembleCluster() {
        meetNodes()
        assignSlots()
        waitClusterReady()
    }

    private fun meetNodes() {
        RedisClient.create(redisUri(nodes.first())).use { meetClient ->
            val connection = meetClient.connect()
            val commands = connection.sync()
            nodes.subList(1, nodes.size).forEach {
                commands.clusterMeet("127.0.0.1", firstPort(it))
            }
        }
    }

    private fun assignSlots() {
        val slotsPerNode = SlotHash.SLOT_COUNT / nodes.size
        for ((index, node) in nodes.withIndex()) {
            val startInclusive = index * slotsPerNode
            val endExclusive = if (index == nodes.size - 1) SlotHash.SLOT_COUNT else (index + 1) * slotsPerNode
            RedisClient.create(redisUri(node)).use { assignSlotClient ->
                assignSlotClient.connect().use { assignSlotConnection ->
                    val slots = IntArray(endExclusive - startInclusive)
                    for (s in startInclusive..<endExclusive) {
                        slots[s - startInclusive] = s
                    }
                    assignSlotConnection.sync().clusterAddSlots(*slots)
                }
            }
        }
    }

    private fun waitClusterReady() {
        RedisClient.create(redisUri(nodes.first())).use { waitClient ->
            waitClient.connect().use { connection ->
                repeat(20) {
                    val clusterInfo = connection.sync().clusterInfo()
                    if (clusterInfo.contains("cluster_state:ok")) {
                        return
                    }
                    Thread.sleep(500)
                }
                throw IllegalStateException("Timeout: Redis cluster not ready")
            }
        }
    }

    private fun redisUri(node: RedisServer): RedisURI {
        return RedisURI.create("127.0.0.1", firstPort(node))
    }

    private fun firstPort(node: RedisServer): Int {
        return node.ports().first()
    }
}
