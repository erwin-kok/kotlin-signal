@file:OptIn(ExperimentalLettuceCoroutinesApi::class)

package org.erwinkok.signal.server.redis

import com.google.protobuf.ByteString
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.ByteArrayCodec
import kotlinx.coroutines.test.runTest
import org.erwinkok.entities.message
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
    fun testScript() = runTest {
        val destination = Destination(UUID.randomUUID(), 0)

        RedisClusterClient.create(REDIS_CLUSTER_EXTENSION.redisURIs()).use {
            val connection = it.connect(ByteArrayCodec.INSTANCE)
            val script = InsertMessageScript(connection)

            val message1 = message {
                this.serverTimestamp = Instant.now().epochSecond
                this.serverGuid = UUID.randomUUID().toString()
                this.content = ByteString.copyFrom("Hello World!", Charsets.UTF_8)
            }
            script.execute(destination, message1)
            repeat(5) {
                script.execute(
                    destination,
                    message {
                        this.serverTimestamp = Instant.now().epochSecond
                        this.serverGuid = UUID.randomUUID().toString()
                        this.content = ByteString.copyFrom("Hello World!", Charsets.UTF_8)
                    },
                )
            }

            val getScript = GetMessagesScript(connection)
            val x = getScript.execute(destination, 1024, 0)
            println(x)
            val y = getScript.execute(destination, 1024, 0)
            println(y)
        }
    }
}
