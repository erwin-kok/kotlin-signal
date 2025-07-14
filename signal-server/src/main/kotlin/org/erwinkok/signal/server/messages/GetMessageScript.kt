@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.messages

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines
import org.erwinkok.entities.MessageProtos

private val logger = KotlinLogging.logger {}

class GetMessageScript private constructor(
    private val redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>,
    private val sha1: String,
) {
    suspend fun execute(destination: Destination, limit: Int, afterMessageId: Long): Pair<List<MessageProtos.Message>, Long> {
        val keys = listOf(
            destination.messageQueueKey,
            destination.persistInProgressKey,
        )
        val args = listOf(
            limit.toString().toByteArray(Charsets.UTF_8),
            afterMessageId.toString().toByteArray(Charsets.UTF_8),
        )
        val result = redisConnection.coroutines().evalsha<List<ByteArray>>(
            sha1,
            ScriptOutputType.OBJECT,
            keys.toTypedArray(),
            *args.toTypedArray(),
        ) ?: return Pair(emptyList(), -1)

        if (result.size % 2 != 0) {
            logger.error { "'Get messages' should return a list with a even number of elements." }
            return Pair(emptyList(), -1)
        }

        val lastMessageId = String(result.last(), Charsets.UTF_8).toLong()
        val messages = result.mapIndexedNotNull { index, byteArray ->
            if (index % 2 == 0) {
                MessageProtos.Message.parseFrom(byteArray)
            } else {
                null
            }
        }
        return Pair(messages, lastMessageId)
    }

    companion object {
        fun load(redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>): GetMessageScript {
            val classLoader = GetMessageScript::class.java.classLoader
            val script = String(requireNotNull(classLoader.getResourceAsStream("lua/get_messages.lua")).readAllBytes(), Charsets.UTF_8)
            requireNotNull(script)
            return GetMessageScript(
                redisConnection = redisConnection,
                sha1 = redisConnection.sync().scriptLoad(script),
            )
        }
    }
}
