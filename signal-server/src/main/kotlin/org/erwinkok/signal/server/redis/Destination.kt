package org.erwinkok.signal.server.redis

import io.lettuce.core.cluster.SlotHash
import java.util.UUID

data class Destination(
    val uuid: UUID,
    val deviceId: Byte,
) {
    val messageQueueKey = "message_queue::{$uuid::$deviceId}".toByteArray(Charsets.UTF_8)
    val messageQueueMetadataKey = "message_queue_metadata::{$uuid::$deviceId}".toByteArray(Charsets.UTF_8)
    val queueIndexKey: ByteArray
        get() = queueIndexKey(SlotHash.getSlot("$uuid::$deviceId"))

    fun queueIndexKey(slot: Int): ByteArray {
        return "message_queue_index::{${RedisClusterUtil.getMinimalHashTag(slot)}}".toByteArray(Charsets.UTF_8)
    }
}
