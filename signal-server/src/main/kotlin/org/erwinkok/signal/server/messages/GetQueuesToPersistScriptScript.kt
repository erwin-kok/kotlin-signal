@file:OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)

package org.erwinkok.signal.server.messages

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.coroutines
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class GetQueuesToPersistScriptScript private constructor(
    private val redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>,
    private val sha1: String,
) {
    suspend fun execute(slot: Int, maxTime: Instant, limit: Int): List<String> {
        val keys = listOf(
            MessagesCache.queueIndexKey(slot),
        )
        val args = listOf(
            maxTime.toEpochMilliseconds().toString().toByteArray(Charsets.UTF_8),
            limit.toString().toByteArray(Charsets.UTF_8),
        )
        return redisConnection.coroutines().evalsha<List<String>>(
            sha1,
            ScriptOutputType.MULTI,
            keys.toTypedArray(),
            *args.toTypedArray(),
        ) ?: return emptyList()
    }

    companion object Companion {
        fun load(redisConnection: StatefulRedisClusterConnection<ByteArray, ByteArray>): GetQueuesToPersistScriptScript {
            val classLoader = GetQueuesToPersistScriptScript::class.java.classLoader
            val script = String(requireNotNull(classLoader.getResourceAsStream("lua/get_queues_to_persist.lua")).readAllBytes(), Charsets.UTF_8)
            requireNotNull(script)
            return GetQueuesToPersistScriptScript(
                redisConnection = redisConnection,
                sha1 = redisConnection.sync().scriptLoad(script),
            )
        }
    }
}
