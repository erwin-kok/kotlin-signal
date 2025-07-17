package org.erwinkok.signal.server.identity

import org.erwinkok.signal.server.util.toBytes
import org.erwinkok.signal.server.util.toUUID
import org.signal.libsignal.protocol.ServiceId
import java.nio.ByteBuffer
import java.util.UUID

class AciServiceIdentifier(override val uuid: UUID) : ServiceIdentifier {
    override val identityType = IdentityType.ACI
    override val serviceIdentifierString = uuid.toString()
    override val compactByteArray = uuid.toBytes()
    override val fixedWidthByteArray: ByteArray
        get() {
            val byteBuffer = ByteBuffer.allocate(17)
            byteBuffer.put(IdentityType.ACI.bytePrefix)
            byteBuffer.putLong(uuid.mostSignificantBits)
            byteBuffer.putLong(uuid.leastSignificantBits)
            byteBuffer.flip()
            return byteBuffer.array()
        }

    override fun toLibSignal(): ServiceId {
        return ServiceId.Aci(uuid)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AciServiceIdentifier) {
            return super.equals(other)
        }

        if (uuid != other.uuid) return false
        if (identityType != other.identityType) return false
        if (serviceIdentifierString != other.serviceIdentifierString) return false
        if (!compactByteArray.contentEquals(other.compactByteArray)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + identityType.hashCode()
        result = 31 * result + serviceIdentifierString.hashCode()
        result = 31 * result + compactByteArray.contentHashCode()
        return result
    }

    companion object {
        fun valueOf(string: String): AciServiceIdentifier {
            return AciServiceIdentifier(string.toUUID())
        }

        fun fromBytes(bytes: ByteArray): AciServiceIdentifier {
            val uuid = if (bytes.size == 17) {
                require(bytes[0] == IdentityType.ACI.bytePrefix) {
                    "Unexpected byte array prefix: ${bytes[0].toString(16)}"
                }
                bytes.copyOfRange(1, bytes.size).toUUID()
            } else {
                bytes.toUUID()
            }
            return AciServiceIdentifier(uuid)
        }
    }
}
