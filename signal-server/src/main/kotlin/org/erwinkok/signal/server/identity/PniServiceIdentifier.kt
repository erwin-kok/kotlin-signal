package org.erwinkok.signal.server.identity

import org.erwinkok.signal.server.util.toUUID
import org.signal.libsignal.protocol.ServiceId
import java.nio.ByteBuffer
import java.util.UUID

class PniServiceIdentifier(override val uuid: UUID) : ServiceIdentifier {
    override val identityType = IdentityType.PNI
    override val serviceIdentifierString = IdentityType.PNI.stringPrefix + uuid.toString()
    override val compactByteArray = fixedWidthByteArray
    override val fixedWidthByteArray: ByteArray
        get() {
            val byteBuffer = ByteBuffer.allocate(17)
            byteBuffer.put(IdentityType.PNI.bytePrefix)
            byteBuffer.putLong(uuid.mostSignificantBits)
            byteBuffer.putLong(uuid.leastSignificantBits)
            byteBuffer.flip()
            return byteBuffer.array()
        }

    override fun toLibSignal(): ServiceId {
        return ServiceId.Pni(uuid)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is PniServiceIdentifier) {
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
        fun valueOf(string: String): PniServiceIdentifier {
            require(string.startsWith(IdentityType.PNI.stringPrefix)) {
                "PNI account identifier did not start with \"PNI:\" prefix"
            }
            return PniServiceIdentifier(string.substring(IdentityType.PNI.stringPrefix.length).toUUID())
        }

        fun fromBytes(bytes: ByteArray): PniServiceIdentifier {
            require(bytes.size == 17) {
                "Unexpected byte array length: ${bytes.size}"
            }
            require(bytes[0] == IdentityType.PNI.bytePrefix) {
                "Unexpected byte array prefix: ${bytes[0].toString(16)}"
            }
            return PniServiceIdentifier(bytes.copyOfRange(1, bytes.size).toUUID())
        }
    }
}
