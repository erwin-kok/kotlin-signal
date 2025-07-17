@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.messages

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines

class RemoveMessageQueueScript private constructor(
    private val redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>,
    private val sha1: String,
) {
    suspend fun execute(destination: Destination, processedMessageGuids: List<String>): List<MessageProtos.Message> {
        val keys = listOf(
            destination.messageQueueKey,
            destination.messageQueueMetadataKey,
            destination.queueIndexKey,
        )
        val args = buildList {
            add(PAGE_SIZE.toString().toByteArray(Charsets.UTF_8)) // limit
            processedMessageGuids.map { it.toByteArray(Charsets.UTF_8) }
        }
        val result = redisConnection.coroutines().evalsha<List<ByteArray>>(
            sha1,
            ScriptOutputType.MULTI,
            keys.toTypedArray(),
            *args.toTypedArray(),
        ) ?: return emptyList()

        return result.map { MessageProtos.Message.parseFrom(it) }
    }

    companion object {
        private const val PAGE_SIZE = 100

        fun load(redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>): RemoveMessageQueueScript {
            val classLoader = RemoveMessageQueueScript::class.java.classLoader
            val script = String(requireNotNull(classLoader.getResourceAsStream("lua/remove_queue.lua")).readAllBytes(), Charsets.UTF_8)
            requireNotNull(script)
            return RemoveMessageQueueScript(
                redisConnection = redisConnection,
                sha1 = redisConnection.sync().scriptLoad(script),
            )
        }
    }
}
