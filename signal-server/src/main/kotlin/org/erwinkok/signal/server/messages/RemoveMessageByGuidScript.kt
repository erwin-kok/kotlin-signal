@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.messages

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines
import java.util.UUID

class RemoveMessageByGuidScript private constructor(
    private val redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>,
    private val sha1: String,
) {
    suspend fun execute(destination: Destination, messageGuids: List<UUID>): List<MessageProtos.Message> {
        val keys = listOf(
            destination.messageQueueKey,
            destination.messageQueueMetadataKey,
            destination.queueIndexKey,
        )
        val args = messageGuids.map { it.toString().toByteArray(Charsets.UTF_8) }
        val result = redisConnection.coroutines().evalsha<List<ByteArray>>(
            sha1,
            ScriptOutputType.OBJECT,
            keys.toTypedArray(),
            *args.toTypedArray(),
        ) ?: return emptyList()

        return result.map { MessageProtos.Message.parseFrom(it) }
    }

    companion object {
        fun load(redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>): RemoveMessageByGuidScript {
            val classLoader = RemoveMessageByGuidScript::class.java.classLoader
            val script = String(requireNotNull(classLoader.getResourceAsStream("lua/remove_message_by_guid.lua")).readAllBytes(), Charsets.UTF_8)
            requireNotNull(script)
            return RemoveMessageByGuidScript(
                redisConnection = redisConnection,
                sha1 = redisConnection.sync().scriptLoad(script),
            )
        }
    }
}
