package org.erwinkok.signal.server.redis

import io.lettuce.core.cluster.SlotHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedisClusterUtilTest {
    @Test
    fun testGetMinimalHashTag() {
        for (slot in 0 until SlotHash.SLOT_COUNT) {
            assertEquals(slot, SlotHash.getSlot(RedisClusterUtil.getMinimalHashTag(slot)))
        }
    }
}
