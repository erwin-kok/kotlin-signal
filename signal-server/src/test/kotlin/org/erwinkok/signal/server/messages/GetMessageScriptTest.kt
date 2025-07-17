@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.messages

import com.google.protobuf.ByteString
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.codec.ByteArrayCodec
import kotlinx.coroutines.test.runTest
import org.erwinkok.signal.server.redis.RedisClusterExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class GetMessageScriptTest {
    companion object {
        @field:RegisterExtension
        private val REDIS_CLUSTER_EXTENSION = RedisClusterExtension()
    }

    @Test
    fun testGetMessageScript() = runTest {
        val destination = Destination(UUID.randomUUID(), 0)

        val clusterClient = REDIS_CLUSTER_EXTENSION.clusterClient!!
        clusterClient.connect("test", ByteArrayCodec.INSTANCE).use { connection ->
            connection.withConnection {
                val insertScript = InsertMessageScript.load(it)
                val getScript = GetMessageScript.load(it)

                val serverGuid = UUID.randomUUID().toString()
                val message1 = message {
                    this.serverTimestamp = Instant.now().epochSecond
                    this.serverGuid = serverGuid
                    this.content = ByteString.copyFrom("Hello World!", Charsets.UTF_8)
                }
                insertScript.execute(destination, message1)

                val result = getScript.execute(destination, 1024, 0)
                val list = result.first
                assertEquals(1, list.size)
                val actualMessage = list.first()
                assertEquals(serverGuid, actualMessage.serverGuid)
                assertEquals("Hello World!", actualMessage.content.toStringUtf8())
                assertEquals(message1, actualMessage)
            }
        }
    }
}
