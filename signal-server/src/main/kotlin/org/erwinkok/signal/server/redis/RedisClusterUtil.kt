package org.erwinkok.signal.server.redis

import io.lettuce.core.cluster.SlotHash

object RedisClusterUtil {
    private val HASHES_BY_SLOT = arrayOfNulls<String>(SlotHash.SLOT_COUNT)

    init {
        var slotsCovered = 0
        var i = 0
        while (slotsCovered < HASHES_BY_SLOT.size) {
            val hash = (i++).toString(36)
            val slot = SlotHash.getSlot(hash)
            if (HASHES_BY_SLOT[slot] == null) {
                HASHES_BY_SLOT[slot] = hash
                slotsCovered += 1
            }
        }
    }

    fun getMinimalHashTag(slot: Int): String? {
        return HASHES_BY_SLOT[slot]
    }
}
