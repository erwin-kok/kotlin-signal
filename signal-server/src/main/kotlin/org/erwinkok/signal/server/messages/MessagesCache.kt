@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)

package org.erwinkok.signal.server.messages

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.ScoredValue
import io.lettuce.core.ZAddArgs
import io.lettuce.core.cluster.SlotHash
import io.lettuce.core.cluster.api.coroutines
import io.lettuce.core.codec.ByteArrayCodec
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.erwinkok.signal.server.metrics.MetricsUtil
import org.erwinkok.signal.server.metrics.monitorMetrics
import org.erwinkok.signal.server.metrics.withTimer
import org.erwinkok.signal.server.redis.ClusterClient
import org.erwinkok.signal.server.redis.RedisClusterUtil
import org.erwinkok.signal.server.util.toUUID
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class MessagesCache(
    clusterClient: ClusterClient,
    private val clock: Clock,
    private val messageDeletionDispatcher: CoroutineDispatcher,
) {
    private val clusterConnection = clusterClient.connect("messages-cache", ByteArrayCodec.INSTANCE)
    private val insertScript = clusterConnection.withSyncConnection {
        InsertMessageScript.load(it)
    }
    private val removeByGuidScript = clusterConnection.withSyncConnection {
        RemoveMessageByGuidScript.load(it)
    }
    private val removeQueueScript = clusterConnection.withSyncConnection {
        RemoveMessageQueueScript.load(it)
    }
    private val getItemsScript = clusterConnection.withSyncConnection {
        GetMessageScript.load(it)
    }
    private val getQueuesToPersistScript = clusterConnection.withSyncConnection {
        GetQueuesToPersistScriptScript.load(it)
    }
    private val unlockQueueScript = clusterConnection.withSyncConnection {
        UnlockQueueScript.load(it)
    }
    private val insertTimer = Metrics.timer(
        MetricsUtil.name<MessagesCache>("insert"),
    )
    private val getMessagesTimer = Metrics.timer(
        MetricsUtil.name<MessagesCache>("get"),
    )
    private val removeByGuidTimer = Metrics.timer(
        MetricsUtil.name<MessagesCache>("removeByGuid"),
    )
    private val clearQueueTimer = Metrics.timer(
        MetricsUtil.name<MessagesCache>("clear"),
    )
    private val getQueuesToPersistTimer = Metrics.timer(
        MetricsUtil.name<MessagesCache>("getQueuesToPersist"),
    )
    private val removeMessageCounter = Metrics.counter(
        MetricsUtil.name<MessagesCache>("remove"),
    )
    private val staleEphemeralMessagesCounter = Metrics.counter(
        MetricsUtil.name<MessagesCache>("staleEphemeralMessages"),
    )

    suspend fun insert(messageGuid: UUID, destination: Destination, message: MessageProtos.Message) {
        val messageWithGuid = message.toBuilder().setServerGuid(messageGuid.toString()).build()
        insertTimer.withTimer {
            insertScript.execute(destination, messageWithGuid)
        }
    }

    suspend fun remove(destination: Destination, messageGuid: UUID): RemovedMessage? {
        return remove(destination, listOf(messageGuid)).firstOrNull()
    }

    suspend fun remove(destination: Destination, messageGuids: List<UUID>): List<RemovedMessage> {
        return removeByGuidTimer.withTimer {
            withContext(messageDeletionDispatcher) {
                val messages = removeByGuidScript.execute(destination, messageGuids)
                val removedMessages = messages.map { RemovedMessage.fromMessage(it) }
                removeMessageCounter.increment(removedMessages.size.toDouble())
                removedMessages
            }
        }
    }

    suspend fun hasMessages(destination: Destination): Boolean {
        return clusterConnection.withConnection {
            (it.coroutines().zcard(destination.messageQueueKey) ?: 0) > 0
        }
    }

    suspend fun get(destination: Destination): Flow<MessageProtos.Message> {
        val earliestAllowableEphemeralTimestamp = clock.millis() - MAX_EPHEMERAL_MESSAGE_DELAY.inWholeMilliseconds
        val allMessages = getAllMessages(
            destination,
            PAGE_SIZE,
        )
        val messagesToPublish = allMessages.filter {
            !isStaleEphemeralMessage(it, earliestAllowableEphemeralTimestamp)
        }
        val staleEphemeralMessages = allMessages
            .filter {
                isStaleEphemeralMessage(it, earliestAllowableEphemeralTimestamp)
            }
        discardStaleMessages(destination, staleEphemeralMessages, staleEphemeralMessagesCounter, "ephemeral")

        return messagesToPublish.monitorMetrics("mySampleFlow", Metrics.globalRegistry)
    }

    suspend fun getEarliestUndeliveredTimestamp(destination: Destination): Long {
        return getAllMessages(destination, 1)
            .first()
            .serverTimestamp
    }

    suspend fun clear(destinationUuid: UUID) {
        Device.ALL_POSSIBLE_DEVICE_IDS.map {
            clear(Destination(destinationUuid, it))
        }
    }

    suspend fun clear(destination: Destination) {
        clearQueueTimer.withTimer {
            withContext(messageDeletionDispatcher) {
                val processedMessages = mutableListOf<String>()
                while (true) {
                    val messagesToProcess = removeQueueScript.execute(destination, processedMessages)
                    if (messagesToProcess.isEmpty()) {
                        break
                    }
                    processedMessages.clear()
                    processedMessages.addAll(messagesToProcess.map { it.serverGuid })
                }
            }
        }
    }

    suspend fun shardForSlot(slot: Int): String {
        return try {
            clusterConnection.withConnection {
                it.partitions.getPartitionBySlot(slot).uri.host
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    internal fun getAllMessages(
        destination: Destination,
        pageSize: Int,
    ): Flow<MessageProtos.Message> {
        return flow {
            var afterMessageId = -1L
            while (true) {
                val (messages, lastMessageId) = getItemsScript.execute(destination, pageSize, afterMessageId)
                if (messages.isEmpty()) {
                    return@flow
                }
                for (message in messages) {
                    emit(message)
                }
                afterMessageId = lastMessageId
            }
        }
    }

    internal suspend fun estimatePersistedQueueSizeBytes(destination: Destination): Long {
        val allSerializedMessages = flow {
            var timeStamp: Long? = null
            while (true) {
                val scoredValues = getNextPageForEstimate(destination, timeStamp).toList()
                if (scoredValues.isEmpty()) {
                    break
                }
                scoredValues.forEach { emit(it.value) }
                timeStamp = scoredValues.last().score.toLong()
            }
        }
        return parseAndFetchMessages(allSerializedMessages)
            .filter { !it.ephemeral }
            .fold(0L) { accumulator, message -> accumulator + message.serializedSize }
    }

    internal suspend fun getMessagesToPersist(destination: Destination, limit: Long): List<MessageProtos.Message> {
        return getMessagesTimer.withTimer {
            val messages = clusterConnection.withConnection {
                it.coroutines().zrange(destination.messageQueueKey, 0, limit)
            }
            val allMessages = parseAndFetchMessages(messages)
            val ephemeralMessages = allMessages.filter { it.ephemeral }
            discardStaleMessages(destination, ephemeralMessages, staleEphemeralMessagesCounter, "ephemeral")
            val messagesToPersist = allMessages.filter { !it.ephemeral }
            withTimeout(5.seconds) {
                messagesToPersist.toList()
            }
        }
    }

    internal suspend fun nextSlotToPersist(): Int {
        return clusterConnection.withConnection {
            (it.coroutines().incr(NEXT_SLOT_TO_PERSIST_KEY) ?: 0).mod(SlotHash.SLOT_COUNT)
        }
    }

    internal suspend fun queuesToPersist(slot: Int, maxTime: Instant, limit: Int): List<String> {
        return getQueuesToPersistTimer.withTimer {
            getQueuesToPersistScript.execute(slot, maxTime, limit)
        }
    }

    internal suspend fun addQueueToPersist(destination: Destination) {
        clusterConnection.withConnection {
            it.coroutines().zadd(
                destination.queueIndexKey,
                ZAddArgs.Builder.nx(),
                System.currentTimeMillis(),
                destination.messageQueueKey,
            )
        }
    }

    internal suspend fun lockQueueForPersistence(destination: Destination) {
        clusterConnection.withConnection {
            it.coroutines().setex(
                destination.persistInProgressKey,
                30,
                LOCK_VALUE,
            )
        }
    }

    internal suspend fun unlockQueueForPersistence(destination: Destination) {
        unlockQueueScript.execute(destination)
    }

    private suspend fun getNextPageForEstimate(destination: Destination, start: Long?): Flow<ScoredValue<ByteArray>> {
        val range = if (start != null) Range.Boundary.excluding(start) else Range.Boundary.unbounded()
        return clusterConnection.withConnection {
            it.coroutines().zrangebyscoreWithScores(
                destination.messageQueueKey,
                Range.from(range, Range.Boundary.unbounded()),
                Limit.from(PAGE_SIZE.toLong()),
            )
        }
    }

    private fun parseAndFetchMessages(serializedMessages: Flow<ByteArray>): Flow<MessageProtos.Message> {
        return flow {
            serializedMessages.collect {
                emit(MessageProtos.Message.parseFrom(it))
            }
        }
    }

    private fun isStaleEphemeralMessage(
        message: MessageProtos.Message,
        earliestAllowableTimestamp: Long,
    ): Boolean {
        return message.ephemeral && message.clientTimestamp < earliestAllowableTimestamp
    }

    private suspend fun discardStaleMessages(destination: Destination, staleMessages: Flow<MessageProtos.Message>, counter: Counter, context: String) {
        staleMessages
            .map { it.serverGuid.toUUID() }
            .chunked(PAGE_SIZE)
            .flowOn(messageDeletionDispatcher)
            .collect { messageGuids -> remove(destination, messageGuids) }
    }

    companion object {
        private const val PAGE_SIZE = 100
        private val LOCK_VALUE = "1".toByteArray(StandardCharsets.UTF_8)
        val MAX_EPHEMERAL_MESSAGE_DELAY = 10.seconds
        val NEXT_SLOT_TO_PERSIST_KEY = "user_queue_persist_slot".toByteArray(Charsets.UTF_8)

        fun queueIndexKey(slot: Int): ByteArray {
            return ("user_queue_index::{${RedisClusterUtil.getMinimalHashTag(slot)}}").toByteArray(StandardCharsets.UTF_8)
        }

        fun accountUuidFromQueueName(queueName: String): UUID {
            val startOfHashTag = queueName.indexOf('{')
            return UUID.fromString(
                queueName.substring(
                    startOfHashTag + 1,
                    queueName.indexOf("::", startOfHashTag),
                ),
            )
        }

        fun deviceIdFromQueueName(queueName: String): Byte {
            return queueName.substring(
                queueName.lastIndexOf("::") + 2,
                queueName.lastIndexOf('}'.code.toChar()),
            ).toByte()
        }
    }
}
