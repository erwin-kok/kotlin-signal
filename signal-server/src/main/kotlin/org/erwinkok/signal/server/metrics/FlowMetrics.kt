package org.erwinkok.signal.server.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.time.Duration

class FlowMetricsBinder<T>(
    private val flowName: String,
) : MeterBinder {
    lateinit var completionTimer: Timer
        private set
    lateinit var emittedCounter: Counter
        private set
    lateinit var completionCounter: Counter
        private set
    lateinit var errorCounter: Counter
        private set

    override fun bindTo(registry: MeterRegistry) {
        completionTimer = Timer.builder("flow.$flowName.completion.duration")
            .description("Time taken for the flow to complete (including errors)")
            .register(registry)

        emittedCounter = Counter.builder("flow.$flowName.emitted.total")
            .description("Number of elements emitted by the flow")
            .register(registry)

        completionCounter = Counter.builder("flow.$flowName.completed.total")
            .description("Number of times the flow completed successfully")
            .register(registry)

        errorCounter = Counter.builder("flow.$flowName.errors.total")
            .description("Number of times the flow terminated with an error")
            .register(registry)
    }
}

fun <T> Flow<T>.monitorMetrics(flowName: String, registry: MeterRegistry = Metrics.globalRegistry): Flow<T> {
    val metricsBinder = FlowMetricsBinder<T>(flowName)
    metricsBinder.bindTo(registry)
    var startTime = System.nanoTime()
    return this
        .onStart {
            startTime = System.nanoTime()
        }
        .onEach {
            metricsBinder.emittedCounter.increment()
        }
        .onCompletion { cause ->
            val duration = Duration.ofNanos(System.nanoTime() - startTime)
            metricsBinder.completionTimer.record(duration)
            if (cause == null) {
                metricsBinder.completionCounter.increment()
            } else {
                metricsBinder.errorCounter.increment()
            }
        }
}
