package io.github.androiddesktop

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded shared dispatcher for privileged-core socket I/O.
 * Avoids creating one Java Thread for every tap/session request.
 */
object CoreIoDispatcher {
    private val sequence = AtomicInteger(1)
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        2,
        ThreadFactory { runnable ->
            Thread(runnable, "Androiddesktop-core-io-${sequence.getAndIncrement()}").apply {
                priority = Thread.NORM_PRIORITY
                isDaemon = true
            }
        }
    )

    fun execute(block: () -> Unit) {
        executor.execute(block)
    }
}
