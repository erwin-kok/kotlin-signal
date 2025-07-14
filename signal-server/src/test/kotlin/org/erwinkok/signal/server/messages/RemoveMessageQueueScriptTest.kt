package org.erwinkok.signal.server.messages

import com.google.protobuf.ByteString
import io.lettuce.core.codec.ByteArrayCodec
import kotlinx.coroutines.test.runTest
import org.erwinkok.entities.message
import org.erwinkok.signal.server.redis.RedisClusterExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.UUID

class RemoveMessageQueueScriptTest {
    companion object Companion {
        @field:RegisterExtension
        private val REDIS_CLUSTER_EXTENSION = RedisClusterExtension()
    }

    @Test
    fun testRemoveMessageScript() = runTest {
        val destination = Destination(UUID.randomUUID(), 0)

        val clusterClient = REDIS_CLUSTER_EXTENSION.clusterClient!!
        clusterClient.connect("test", ByteArrayCodec.INSTANCE).use { connection ->
            connection.execute {
                val insertScript = InsertMessageScript(it)
                val removeScript = RemoveMessageQueueScript(it)
                val message1 = message {
                    this.serverTimestamp = Instant.now().epochSecond
                    this.serverGuid = UUID.randomUUID().toString()
                    this.content = ByteString.copyFrom("Hello World!", Charsets.UTF_8)
                }
                insertScript.execute(destination, message1)

                val message2 = message {
                    this.serverTimestamp = Instant.now().epochSecond
                    this.serverGuid = UUID.randomUUID().toString()
                    this.content = ByteString.copyFrom("How are you?", Charsets.UTF_8)
                }
                insertScript.execute(destination, message2)

                val result = removeScript.execute(destination, emptyList())
                assertEquals(2, result.size)
                assertEquals(listOf(message1, message2), result)
            }
        }
    }
}
