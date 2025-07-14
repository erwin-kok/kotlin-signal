package org.erwinkok.signal.server.messages

import io.lettuce.core.codec.ByteArrayCodec
import io.micrometer.core.instrument.Metrics
import org.erwinkok.entities.MessageProtos
import org.erwinkok.signal.server.metrics.MetricsUtil
import org.erwinkok.signal.server.metrics.withTimer
import org.erwinkok.signal.server.redis.ClusterClient
import java.util.UUID

class MessagesCache(
    clusterClient: ClusterClient,
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
    private val insertTimer = Metrics.timer(MetricsUtil.name<MessagesCache>("insert"))
    private val removeByGuidTimer = Metrics.timer(MetricsUtil.name<MessagesCache>("removeByGuid"))

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
            val messages = removeByGuidScript.execute(destination, messageGuids)

            val removedMessages = messages.map { message -> RemovedMessage.fromMessage(message) }

            removedMessages
        }
    }
}
