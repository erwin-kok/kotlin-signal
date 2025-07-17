@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.messages

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines

class UnlockQueueScript private constructor(
    private val redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>,
    private val sha1: String,
) {
    suspend fun execute(destination: Destination) {
        val keys = listOf(
            destination.persistInProgressKey,
        )
        redisConnection.coroutines().evalsha<Unit>(
            sha1,
            ScriptOutputType.BOOLEAN,
            keys.toTypedArray(),
            MESSAGES_PERSISTED_EVENT_ARGS,
        )
    }

    companion object {
        private val MESSAGES_PERSISTED_EVENT_ARGS = clientEvent {
            messagesPersisted = MessagesPersistedEvent.getDefaultInstance()
        }.toByteArray()

        fun load(redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>): UnlockQueueScript {
            val classLoader = UnlockQueueScript::class.java.classLoader
            val script = String(requireNotNull(classLoader.getResourceAsStream("lua/insert_message.lua")).readAllBytes(), Charsets.UTF_8)
            requireNotNull(script)
            return UnlockQueueScript(
                redisConnection = redisConnection,
                sha1 = redisConnection.sync().scriptLoad(script),
            )
        }
    }
}
