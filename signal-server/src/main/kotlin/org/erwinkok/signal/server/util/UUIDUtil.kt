package org.erwinkok.signal.server.util

import com.google.protobuf.ByteString
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.UUID

fun ByteArray.toUUID(): UUID {
    return ByteBuffer.wrap(this).toUUID()
}

fun ByteBuffer.toUUID(): UUID {
    try {
        val mostSigBits = this.getLong()
        val leastSigBits = this.getLong()
        require(!this.hasRemaining()) { "unexpected byte array length; was greater than 16" }
        return UUID(mostSigBits, leastSigBits)
    } catch (_: BufferUnderflowException) {
        throw IllegalArgumentException("unexpected byte array length; was less than 16")
    }
}

fun ByteString.toUUID(): UUID {
    return this.toByteArray().toUUID()
}

fun String.toUUID(): UUID {
    return UUID.fromString(this)
}

fun UUID.toBytes(): ByteArray {
    return toByteBuffer().array()
}

fun UUID.toByteBuffer(): ByteBuffer {
    val byteBuffer = ByteBuffer.wrap(ByteArray(16))
    byteBuffer.putLong(this.mostSignificantBits)
    byteBuffer.putLong(this.leastSignificantBits)
    return byteBuffer.flip()
}

fun UUID.toByteString(): ByteString {
    return ByteString.copyFrom(toByteBuffer())
}
