@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.messages

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines
import org.erwinkok.entities.MessageProtos

class InsertMessageScript private constructor(
    private val redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>,
    private val sha1: String,
) {
    suspend fun execute(destination: Destination, message: MessageProtos.Message): Boolean {
        val keys = listOf(
            destination.messageQueueKey,
            destination.messageQueueMetadataKey,
            destination.queueIndexKey,
        )
        val args = listOf(
            message.toByteArray(),
            message.serverTimestamp.toString().toByteArray(Charsets.UTF_8),
            message.serverGuid.toString().toByteArray(Charsets.UTF_8),
        )
        return redisConnection.coroutines().evalsha<Boolean>(
            sha1,
            ScriptOutputType.BOOLEAN,
            keys.toTypedArray(),
            *args.toTypedArray(),
        ) ?: false
    }

    companion object {
        fun load(redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>): InsertMessageScript {
            val classLoader = GetMessageScript::class.java.classLoader
            val script = String(requireNotNull(classLoader.getResourceAsStream("lua/insert_message.lua")).readAllBytes(), Charsets.UTF_8)
            requireNotNull(script)
            return InsertMessageScript(
                redisConnection = redisConnection,
                sha1 = redisConnection.sync().scriptLoad(script),
            )
        }
    }
}
