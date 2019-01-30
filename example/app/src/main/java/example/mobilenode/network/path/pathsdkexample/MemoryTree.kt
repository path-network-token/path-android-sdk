package example.mobilenode.network.path.pathsdkexample

import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class MemoryTree private constructor() : Timber.DebugTree() {
    data class Record(
        val time: Long,
        val priority: Int,
        val tag: String?,
        val message: String,
        val t: Throwable?
    )

    companion object {
        private var INSTANCE: MemoryTree? = null

        fun create(): MemoryTree {
            if (INSTANCE == null) {
                INSTANCE = MemoryTree()
            }
            return INSTANCE!!
        }
    }

    interface Listener {
        fun onLog(record: Record)
    }

    private val _records = LimitedQueue<Record>(1000)
    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<Listener, Boolean>(0))

    val records: List<Record> = _records

    /**
     * Adds new listener. This operation is thread-safe.
     */
    fun addListener(l: Listener) = listeners.add(l)

    /**
     * Removes specified listener. This operation is thread-safe.
     */
    fun removeListener(l: Listener) = listeners.remove(l)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        val record = Record(System.nanoTime(), priority, tag, message, t)
        _records.add(record)
        listeners.forEach { it.onLog(record) }
    }

    private class LimitedQueue<E>(private val limit: Int) : LinkedList<E>() {
        override fun add(element: E): Boolean {
            val added = super.add(element)
            if (added) {
                while (size > limit) {
                    super.remove()
                }
            }
            return added
        }
    }
}
