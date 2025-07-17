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

class InsertMessageScriptTest {
    companion object {
        @field:RegisterExtension
        private val REDIS_CLUSTER_EXTENSION = RedisClusterExtension()
    }

    @Test
    fun testInsertMessageScript() = runTest {
        val destination = Destination(UUID.randomUUID(), 0)

        val clusterClient = REDIS_CLUSTER_EXTENSION.clusterClient!!
        clusterClient.connect("test", ByteArrayCodec.INSTANCE).use { connection ->
            connection.withConnection {
                val insertScript = InsertMessageScript.load(it)
                val getScript = GetMessageScript.load(it)

                val message1 = message {
                    this.serverTimestamp = Instant.now().epochSecond
                    this.serverGuid = UUID.randomUUID().toString()
                    this.content = ByteString.copyFrom("Hello World!", Charsets.UTF_8)
                }
                insertScript.execute(destination, message1)

                val actual1 = getScript.execute(destination, 1024, 0)
                assertEquals(listOf(message1), actual1.first)

                val message2 = message {
                    this.serverTimestamp = Instant.now().epochSecond
                    this.serverGuid = UUID.randomUUID().toString()
                    this.content = ByteString.copyFrom("How are you?", Charsets.UTF_8)
                }
                insertScript.execute(destination, message2)

                val actual2 = getScript.execute(destination, 1024, 0)
                assertEquals(listOf(message1, message2), actual2.first)

                insertScript.execute(destination, message1)
                assertEquals(listOf(message1, message2), actual2.first)
            }
        }
    }
}
