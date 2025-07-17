package org.erwinkok.signal.server.messages

import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.erwinkok.signal.server.identity.AciServiceIdentifier
import org.erwinkok.signal.server.identity.ServiceIdentifier
import org.erwinkok.signal.server.redis.RedisClusterExtension
import org.erwinkok.signal.server.util.toUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.util.UUID
import java.util.stream.Stream
import kotlin.random.Random

class MessagesCacheTest {
    companion object {
        @field:RegisterExtension
        private val REDIS_CLUSTER_EXTENSION = RedisClusterExtension()
        private val destination = Destination(UUID.randomUUID(), 7)
        private var serialTimestamp = 0L
    }

    private lateinit var messagesCache: MessagesCache

    @BeforeEach
    fun setUp() {
        messagesCache = MessagesCache(
            REDIS_CLUSTER_EXTENSION.clusterClient!!,
            Clock.systemUTC(),
            Dispatchers.Default,
        )
    }

    @Test
    fun testInsert() = runTest {
        val messageGuid = UUID.randomUUID()
        assertDoesNotThrow {
            messagesCache.insert(messageGuid, destination, generateRandomMessage(messageGuid, false))
            messagesCache.insert(messageGuid, destination, generateRandomMessage(messageGuid, true))
        }
    }

    @Test
    fun testDoubleInsertGuid() = runTest {
        val messageGuid = UUID.randomUUID()
        val message = generateRandomMessage(messageGuid, false)

        messagesCache.insert(messageGuid, destination, message)
        messagesCache.insert(messageGuid, destination, message)

        val count = messagesCache.getAllMessages(destination, 10).count()
        assertEquals(1, count)
    }

    @TestFactory
    fun testRemoveByUUID(): Stream<DynamicTest> {
        return sealedUnsealed { sealedSender ->
            val messageGuid = UUID.randomUUID()
            assertNull(messagesCache.remove(destination, messageGuid))

            val message = generateRandomMessage(messageGuid, sealedSender)
            messagesCache.insert(messageGuid, destination, message)

            val maybeRemovedMessage = messagesCache.remove(destination, messageGuid)
            assertEquals(RemovedMessage.fromMessage(message), maybeRemovedMessage)
        }
    }

    @TestFactory
    fun testRemoveBatchByUUID(): Stream<DynamicTest> {
        return sealedUnsealed { sealedSender ->
            val messageCount = 10
            val messagesToRemove = mutableListOf<MessageProtos.Message>()
            val messagesToPreserve = mutableListOf<MessageProtos.Message>()
            repeat(messageCount) {
                messagesToRemove.add(generateRandomMessage(UUID.randomUUID(), sealedSender))
                messagesToPreserve.add(generateRandomMessage(UUID.randomUUID(), sealedSender))
            }
            assertEquals(
                emptyList<RemovedMessage>(),
                messagesCache.remove(
                    destination,
                    messagesToRemove.map { it.serverGuid.toUUID() },
                ),
            )
            for (message in messagesToRemove) {
                messagesCache.insert(message.serverGuid.toUUID(), destination, message)
            }
            for (message in messagesToPreserve) {
                messagesCache.insert(message.serverGuid.toUUID(), destination, message)
            }
            val removedMessages = messagesCache.remove(
                destination,
                messagesToRemove.map { it.serverGuid.toUUID() },
            )
            assertEquals(messagesToRemove.map { RemovedMessage.fromMessage(it) }, removedMessages)
            assertEquals(messagesToPreserve, get(destination, messageCount))
        }
    }

    @Test
    fun testHasMessages() = runTest {
        assertFalse(messagesCache.hasMessages(destination))

        val messageGuid = UUID.randomUUID()
        val message = generateRandomMessage(messageGuid, true)
        messagesCache.insert(messageGuid, destination, message)

        assertTrue(messagesCache.hasMessages(destination))
    }

    private fun sealedUnsealed(action: suspend (Boolean) -> Unit): Stream<DynamicTest> {
        return listOf(true, false).map { sealedSender: Boolean ->
            DynamicTest.dynamicTest("sealed=$sealedSender") {
                runTest {
                    REDIS_CLUSTER_EXTENSION.beforeEach()
                    action(sealedSender)
                    REDIS_CLUSTER_EXTENSION.afterEach()
                }
            }
        }.stream()
    }

    private fun generateRandomMessage(messageGuid: UUID, sealedSender: Boolean): MessageProtos.Message {
        return generateRandomMessage(
            messageGuid,
            AciServiceIdentifier(UUID.randomUUID()),
            sealedSender,
            serialTimestamp++,
        )
    }

    private fun generateRandomMessage(messageGuid: UUID, destinationServiceId: ServiceIdentifier, sealedSender: Boolean): MessageProtos.Message {
        return generateRandomMessage(messageGuid, destinationServiceId, sealedSender, serialTimestamp++)
    }

    private fun generateRandomMessage(
        messageGuid: UUID,
        destinationServiceId: ServiceIdentifier,
        sealedSender: Boolean,
        timestamp: Long,
    ): MessageProtos.Message {
        return message {
            this.clientTimestamp = timestamp
            this.serverTimestamp = timestamp
            this.content = ByteString.copyFromUtf8(randomString(256))
            this.type = MessageProtos.Message.Type.CIPHERTEXT
            this.serverGuid = messageGuid.toString()
            this.destinationServiceId = destinationServiceId.serviceIdentifierString

            if (!sealedSender) {
                this.sourceDevice = Random.nextInt(Device.MAXIMUM_DEVICE_ID.toInt()) + 1
                this.sourceServiceId = UUID.randomUUID().toString()
            }
        }
    }

    private suspend fun get(destination: Destination, messageCount: Int): List<MessageProtos.Message> {
        return messagesCache.get(destination)
//            .take(messageCount)
            .toList()
    }

    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    fun randomString(length: Int) = (1..length)
        .map { Random.nextInt(0, charPool.size).let { charPool[it] } }
        .joinToString("")
}
