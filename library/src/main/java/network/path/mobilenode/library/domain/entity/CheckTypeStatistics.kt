package network.path.mobilenode.library.domain.entity

data class CheckTypeStatistics(val type: CheckType?, val count: Long, val totalLatencyMillis: Long) {
    val averageLatency get() = if (count > 0) totalLatencyMillis / count else 0L

    fun add(latency: Long): CheckTypeStatistics =
            CheckTypeStatistics(type, count + 1, totalLatencyMillis + latency)

    fun addOther(other: CheckTypeStatistics) =
            CheckTypeStatistics(type, count + other.count, totalLatencyMillis + other.totalLatencyMillis)
}
