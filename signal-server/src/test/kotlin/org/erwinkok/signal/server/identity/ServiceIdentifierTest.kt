package org.erwinkok.signal.server.identity

import org.erwinkok.signal.server.util.toBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import java.util.stream.Stream

class ServiceIdentifierTest {
    @Test
    fun testValueOfAci() {
        val uuid = UUID.randomUUID()
        val serviceIdentifier = ServiceIdentifier.valueOf(uuid.toString())
        assertEquals(IdentityType.ACI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @Test
    fun testValueOfPni() {
        val uuid = UUID.randomUUID()
        val serviceIdentifier = ServiceIdentifier.valueOf("PNI:$uuid")
        assertEquals(IdentityType.PNI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @TestFactory
    fun testValueOfIllegalArgument(): Stream<DynamicTest> {
        return listOf(
            "Not a valid UUID",
            "BAD:a9edc243-3e93-45d4-95c6-e3a84cd4a254",
            "ACI:a9edc243-3e93-45d4-95c6-e3a84cd4a254",
        ).map { value: String ->
            DynamicTest.dynamicTest("Test: $value") {
                assertThrows<IllegalArgumentException> {
                    ServiceIdentifier.valueOf(value)
                }
            }
        }.stream()
    }

    @Test
    fun testFromBytesAciNotPrefixed() {
        val uuid = UUID.randomUUID()
        val serviceIdentifier = ServiceIdentifier.fromBytes(uuid.toBytes())
        assertEquals(IdentityType.ACI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @Test
    fun testFromBytesAci() {
        val uuid = UUID.randomUUID()
        val aciPrefixedBytes = byteArrayOf(0x00) + uuid.toBytes()
        assertEquals(17, aciPrefixedBytes.size)
        val serviceIdentifier = ServiceIdentifier.fromBytes(aciPrefixedBytes)
        assertEquals(IdentityType.ACI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @Test
    fun testFromBytesPni() {
        val uuid = UUID.randomUUID()
        val pniPrefixedBytes = byteArrayOf(0x01) + uuid.toBytes()
        assertEquals(17, pniPrefixedBytes.size)
        val serviceIdentifier = ServiceIdentifier.fromBytes(pniPrefixedBytes)
        assertEquals(IdentityType.PNI, serviceIdentifier.identityType)
        assertEquals(uuid, serviceIdentifier.uuid)
    }

    @TestFactory
    fun testFromBytesIllegalArgument(): Stream<DynamicTest> {
        return listOf(
            ByteArray(0),
            ByteArray(15),
            ByteArray(18),
            byteArrayOf(0xff.toByte()),
        ).map { value: ByteArray ->
            DynamicTest.dynamicTest("Test: ${value.toHexString()}") {
                assertThrows<IllegalArgumentException> {
                    ServiceIdentifier.fromBytes(value)
                }
            }
        }.stream()
    }
}
