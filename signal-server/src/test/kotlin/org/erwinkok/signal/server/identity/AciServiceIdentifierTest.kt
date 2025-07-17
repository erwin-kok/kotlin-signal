package org.erwinkok.signal.server.identity

import org.erwinkok.signal.server.util.toBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.signal.libsignal.protocol.ServiceId
import java.nio.ByteBuffer
import java.util.UUID

class AciServiceIdentifierTest {
    @Test
    fun identityType() {
        assertEquals(IdentityType.ACI, AciServiceIdentifier(UUID.randomUUID()).identityType)
    }

    @Test
    fun toServiceIdentifierString() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid.toString(), AciServiceIdentifier(uuid).serviceIdentifierString)
    }

    @Test
    fun toCompactByteArray() {
        val uuid = UUID.randomUUID()
        assertArrayEquals(uuid.toBytes(), AciServiceIdentifier(uuid).compactByteArray)
    }

    @Test
    fun toFixedWidthByteArray() {
        val uuid = UUID.randomUUID()
        val expectedBytesBuffer = ByteBuffer.allocate(17)
        expectedBytesBuffer.put(0x00.toByte())
        expectedBytesBuffer.putLong(uuid.mostSignificantBits)
        expectedBytesBuffer.putLong(uuid.leastSignificantBits)
        expectedBytesBuffer.flip()
        assertArrayEquals(expectedBytesBuffer.array(), AciServiceIdentifier(uuid).fixedWidthByteArray)
    }

    @Test
    fun valueOf() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, AciServiceIdentifier.valueOf(uuid.toString()).uuid)
        assertThrows<IllegalArgumentException> {
            AciServiceIdentifier.valueOf("Not a valid UUID")
        }
        assertThrows<IllegalArgumentException> {
            AciServiceIdentifier.valueOf("PNI:$uuid")
        }
        assertThrows<IllegalArgumentException> {
            AciServiceIdentifier.valueOf("ACI:$uuid")
        }
    }

    @Test
    fun fromBytes() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, AciServiceIdentifier.fromBytes(uuid.toBytes()).uuid)

        val prefixedBytes = byteArrayOf(0x00) + uuid.toBytes()
        assertEquals(uuid, AciServiceIdentifier.fromBytes(prefixedBytes).uuid)

        val wrongPrefixedBytes = byteArrayOf(0x01) + uuid.toBytes()
        assertThrows<IllegalArgumentException> {
            AciServiceIdentifier.fromBytes(wrongPrefixedBytes)
        }
    }

    @Test
    fun testFromLibSignal() {
        val uuid = UUID.randomUUID()
        val serviceId = ServiceId.Aci(uuid)
        val serviceIdentifier = ServiceIdentifier.fromLibsignal(serviceId)
        assertEquals(IdentityType.ACI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @Test
    fun testToLibSignal() {
        val uuid = UUID.randomUUID()
        val serviceIdentifier = AciServiceIdentifier.fromBytes(uuid.toBytes())
        assertEquals(uuid, serviceIdentifier.uuid)
        val serviceId = serviceIdentifier.toLibSignal()
        assertEquals(ServiceId.Aci(uuid), serviceId)
    }
}
