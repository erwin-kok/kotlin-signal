package org.erwinkok.signal.server.messages

import com.google.protobuf.ByteString
import io.lettuce.core.codec.ByteArrayCodec
import kotlinx.coroutines.test.runTest
import org.erwinkok.signal.server.redis.RedisClusterExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class RemoveMessageByGuidScriptTest {
    companion object Companion {
        @field:RegisterExtension
        private val REDIS_CLUSTER_EXTENSION = RedisClusterExtension()
    }

    @Test
    fun testRemoveMessageScript() = runTest {
        val destination = Destination(UUID.randomUUID(), 0)

        val clusterClient = REDIS_CLUSTER_EXTENSION.clusterClient!!
        clusterClient.connect("test", ByteArrayCodec.INSTANCE).use { connection ->
            connection.withConnection {
                val insertScript = InsertMessageScript.load(it)
                val removeScript = RemoveMessageByGuidScript.load(it)
                val serverGuid = UUID.randomUUID()
                val message1 = message {
                    this.serverTimestamp = Instant.now().epochSecond
                    this.serverGuid = serverGuid.toString()
                    this.content = ByteString.copyFrom("Hello World!", Charsets.UTF_8)
                }
                insertScript.execute(destination, message1)

                val result = removeScript.execute(destination, listOf(serverGuid))
                Assertions.assertEquals(1, result.size)
                val actualMessage = result.first()
                Assertions.assertEquals(serverGuid.toString(), actualMessage.serverGuid)
                Assertions.assertEquals("Hello World!", actualMessage.content.toStringUtf8())
                Assertions.assertEquals(message1, actualMessage)
            }
        }
    }
}
