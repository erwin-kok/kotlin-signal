package org.erwinkok.signal.server.identity

import org.signal.libsignal.protocol.ServiceId
import org.signal.libsignal.protocol.ServiceId.Aci
import org.signal.libsignal.protocol.ServiceId.Pni
import java.util.UUID

enum class IdentityType(val bytePrefix: Byte, val stringPrefix: String) {
    ACI(0x00, "ACI:"),
    PNI(0x01, "PNI:"),
}

sealed interface ServiceIdentifier {
    val identityType: IdentityType
    val uuid: UUID
    val serviceIdentifierString: String
    val compactByteArray: ByteArray
    val fixedWidthByteArray: ByteArray
    fun toLibSignal(): ServiceId

    companion object {
        fun valueOf(string: String): ServiceIdentifier {
            return try {
                AciServiceIdentifier.valueOf(string)
            } catch (_: IllegalArgumentException) {
                PniServiceIdentifier.valueOf(string)
            }
        }

        fun fromBytes(bytes: ByteArray): ServiceIdentifier {
            return try {
                AciServiceIdentifier.fromBytes(bytes)
            } catch (_: IllegalArgumentException) {
                PniServiceIdentifier.fromBytes(bytes)
            }
        }

        fun fromLibsignal(libsignalServiceId: ServiceId): ServiceIdentifier {
            if (libsignalServiceId is Aci) {
                return AciServiceIdentifier(libsignalServiceId.rawUUID)
            }
            if (libsignalServiceId is Pni) {
                return PniServiceIdentifier(libsignalServiceId.rawUUID)
            }
            throw IllegalArgumentException("unknown libsignal ServiceId type")
        }
    }
}
