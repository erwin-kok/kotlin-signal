package org.erwinkok.signal.server.redis

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisURI
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import java.time.Duration

class ClusterClient(
    redisURIs: List<RedisURI>,
) {
    private val clusterClient = RedisClusterClient.create(redisURIs)

    init {
        val options = ClusterClientOptions
            .builder()
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .validateClusterNodeMembership(false)
            .topologyRefreshOptions(
                ClusterTopologyRefreshOptions.builder()
                    .enableAllAdaptiveRefreshTriggers()
                    .build(),
            )
            .timeoutOptions(
                TimeoutOptions.builder()
                    .fixedTimeout(Duration.ofSeconds(2))
                    .build(),
            )
            .publishOnScheduler(true)
            .build()
        clusterClient.setOptions(options)
    }

    fun connect(name: String): ClusterConnection<String, String> {
        return connect(name, StringCodec.UTF8)
    }

    fun <K, V> connect(name: String, codec: RedisCodec<K, V>): ClusterConnection<K, V> {
        return ClusterConnection(
            clusterClient.connect(codec),
            CircuitBreaker.ofDefaults("$name-circuit-breaker"),
            Retry.ofDefaults("$name-retry"),
        )
    }

    fun shutdown() {
        clusterClient.shutdown()
    }
}
