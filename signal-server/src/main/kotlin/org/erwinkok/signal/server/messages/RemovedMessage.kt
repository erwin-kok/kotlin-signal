package org.erwinkok.signal.server.messages

import org.erwinkok.entities.MessageProtos
import org.erwinkok.signal.server.util.toUUID
import java.util.UUID

data class RemovedMessage(
    val serverGuid: UUID,
    val serverTimestamp: Long,
    val clientTimestamp: Long,
    val type: MessageProtos.Message.Type,
) {
    companion object {
        fun fromMessage(message: MessageProtos.Message): RemovedMessage {
            return RemovedMessage(
                message.serverGuid.toUUID(),
                message.serverTimestamp,
                message.clientTimestamp,
                message.type,
            )
        }
    }
}
