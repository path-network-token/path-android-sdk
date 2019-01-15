package network.path.mobilenode.library.utils

import timber.log.Timber
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


internal class CustomThreadPoolManager {
    companion object {
        private val NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors()
    }

    private val executorService = ScheduledThreadPoolExecutor(NUMBER_OF_CORES * 2, BackgroundThreadFactory())

    fun <T> run(name: CharSequence, delay: Long = 0L, callable: (() -> T?)): Future<T?>? {
        if (executorService.isShutdown) return null

        return if (delay != 0L) {
            executorService.schedule(callable, delay, TimeUnit.MILLISECONDS)
        } else {
            executorService.submit(callable)
        }
    }

    fun destroy(): List<Runnable> = executorService.shutdownNow()

    private class BackgroundThreadFactory : ThreadFactory {
        private var currentTag = AtomicInteger(1)

        override fun newThread(runnable: Runnable): Thread {
            val tag = currentTag.getAndIncrement().toString()

            val thread = Thread(runnable)
            thread.name = "CustomThread-$tag"
            thread.priority = android.os.Process.THREAD_PRIORITY_BACKGROUND

            // A exception handler is created to log the exception from threads
            thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { th, ex ->
                Timber.e("PATH: [${th.name}] encountered an error: ${ex.message}")
                Timber.e(ex)
            }
            return thread
        }
    }
}
