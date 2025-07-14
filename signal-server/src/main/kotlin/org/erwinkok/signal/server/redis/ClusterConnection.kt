package org.erwinkok.signal.server.redis

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import java.io.Closeable

class ClusterConnection<K, V>(
    private val connection: StatefulRedisClusterConnection<K, V>,
    private val circuitBreaker: CircuitBreaker,
    private val retry: Retry,
) : Closeable {
    suspend fun <T> execute(action: suspend (StatefulRedisClusterConnection<K, V>) -> T): T {
        return circuitBreaker.executeSuspendFunction {
            retry.executeSuspendFunction {
                action(connection)
            }
        }
    }

    fun <T> executeSync(action: (StatefulRedisClusterConnection<K, V>) -> T): T {
        return circuitBreaker.executeCallable {
            retry.executeCallable {
                action(connection)
            }
        }
    }

    override fun close() {
        connection.close()
    }
}
