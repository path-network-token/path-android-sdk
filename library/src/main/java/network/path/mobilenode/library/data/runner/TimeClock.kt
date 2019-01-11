package network.path.mobilenode.library.data.runner

import android.os.SystemClock

interface TimeSource {
    val currentTimeMillis: Long

    fun measure(block: () -> Unit): Long {
        val start = currentTimeMillis
        block()
        return currentTimeMillis - start
    }
}

object TimeClock : TimeSource {
    override val currentTimeMillis get() = SystemClock.elapsedRealtime()
}
