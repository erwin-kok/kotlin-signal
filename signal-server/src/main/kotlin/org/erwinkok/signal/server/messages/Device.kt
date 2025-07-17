package org.erwinkok.signal.server.messages

class Device {
    companion object {
        const val PRIMARY_ID: Byte = 1
        const val MAXIMUM_DEVICE_ID = Byte.Companion.MAX_VALUE
        const val MAX_REGISTRATION_ID = 0x3FFF
        val ALL_POSSIBLE_DEVICE_IDS = (PRIMARY_ID until MAXIMUM_DEVICE_ID).map { it.toByte() }
    }
}
