package org.erwinkok.signal.server.metrics

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.Timer.start

object MetricsUtil {
    inline fun <reified T> name(vararg parts: String): String {
        return "chat.${T::class.simpleName}.${parts.joinToString(".")}"
    }
}

suspend inline fun <reified T> Timer.withTimer(action: suspend () -> T): T {
    val timer = start()
    val result = try {
        action()
    } finally {
        timer.stop(this)
    }
    return result
}
