package org.erwinkok.signal.server.identity

import org.erwinkok.signal.server.util.toBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.signal.libsignal.protocol.ServiceId
import java.nio.ByteBuffer
import java.util.UUID

class PniServiceIdentifierTest {
    @Test
    fun identityType() {
        assertEquals(IdentityType.PNI, PniServiceIdentifier(UUID.randomUUID()).identityType)
    }

    @Test
    fun toServiceIdentifierString() {
        val uuid = UUID.randomUUID()
        assertEquals("PNI:$uuid", PniServiceIdentifier(uuid).serviceIdentifierString)
    }

    @Test
    fun toByteArray() {
        val uuid = UUID.randomUUID()
        val expectedBytesBuffer = ByteBuffer.allocate(17)
        expectedBytesBuffer.put(0x01.toByte())
        expectedBytesBuffer.putLong(uuid.mostSignificantBits)
        expectedBytesBuffer.putLong(uuid.leastSignificantBits)
        expectedBytesBuffer.flip()
        assertArrayEquals(expectedBytesBuffer.array(), PniServiceIdentifier(uuid).compactByteArray)
        assertArrayEquals(expectedBytesBuffer.array(), PniServiceIdentifier(uuid).fixedWidthByteArray)
    }

    @Test
    fun valueOf() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, PniServiceIdentifier.valueOf("PNI:$uuid").uuid)
        assertThrows<IllegalArgumentException> {
            PniServiceIdentifier.valueOf(uuid.toString())
        }
        assertThrows<IllegalArgumentException> {
            PniServiceIdentifier.valueOf("Not a valid UUID")
        }
        assertThrows<IllegalArgumentException> {
            PniServiceIdentifier.valueOf("ACI:$uuid")
        }
    }

    @Test
    fun fromBytes() {
        val uuid = UUID.randomUUID()
        assertThrows<IllegalArgumentException> {
            assertEquals(uuid, PniServiceIdentifier.fromBytes(uuid.toBytes()))
        }

        val wrongPrefixedBytes = byteArrayOf(0x00) + uuid.toBytes()
        assertThrows<IllegalArgumentException> {
            PniServiceIdentifier.fromBytes(wrongPrefixedBytes)
        }

        val prefixedBytes = byteArrayOf(0x01) + uuid.toBytes()
        assertEquals(uuid, PniServiceIdentifier.fromBytes(prefixedBytes).uuid)
    }

    @Test
    fun testFromLibSignal() {
        val uuid = UUID.randomUUID()
        val serviceId = ServiceId.Pni(uuid)
        val serviceIdentifier = ServiceIdentifier.fromLibsignal(serviceId)
        assertEquals(IdentityType.PNI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @Test
    fun testToLibSignal() {
        val uuid = UUID.randomUUID()
        val serviceIdentifier = PniServiceIdentifier.fromBytes(byteArrayOf(1) + uuid.toBytes())
        assertEquals(uuid, serviceIdentifier.uuid)
        val serviceId = serviceIdentifier.toLibSignal()
        assertEquals(ServiceId.Pni(uuid), serviceId)
    }
}
