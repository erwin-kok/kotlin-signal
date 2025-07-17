package org.erwinkok.signal.server.messages

import org.erwinkok.signal.server.identity.ServiceIdentifier
import org.erwinkok.signal.server.util.toUUID
import java.util.UUID

data class RemovedMessage(
    val sourceServiceId: ServiceIdentifier?,
    val destinationServiceId: ServiceIdentifier,
    val serverGuid: UUID,
    val serverTimestamp: Long,
    val clientTimestamp: Long,
    val type: MessageProtos.Message.Type,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is RemovedMessage) {
            return super.equals(other)
        }

        if (serverTimestamp != other.serverTimestamp) return false
        if (clientTimestamp != other.clientTimestamp) return false
        if (sourceServiceId != other.sourceServiceId) return false
        if (destinationServiceId != other.destinationServiceId) return false
        if (serverGuid != other.serverGuid) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serverTimestamp.hashCode()
        result = 31 * result + clientTimestamp.hashCode()
        result = 31 * result + (sourceServiceId?.hashCode() ?: 0)
        result = 31 * result + destinationServiceId.hashCode()
        result = 31 * result + serverGuid.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    companion object {
        fun fromMessage(message: MessageProtos.Message): RemovedMessage {
            val sourceServiceId = if (message.hasSourceServiceId()) {
                ServiceIdentifier.valueOf(message.sourceServiceId)
            } else {
                null
            }
            val destinationServiceId = ServiceIdentifier.valueOf(message.destinationServiceId)
            return RemovedMessage(
                sourceServiceId,
                destinationServiceId,
                message.serverGuid.toUUID(),
                message.serverTimestamp,
                message.clientTimestamp,
                message.type,
            )
        }
    }
}
