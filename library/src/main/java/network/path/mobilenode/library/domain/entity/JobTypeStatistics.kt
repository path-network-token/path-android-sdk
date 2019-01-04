package network.path.mobilenode.library.domain.entity

/**
 * Aggregated job execution statistics holder.
 *
 * For each [JobType] stores number of executed jobs and their total latency (in ms).
 *
 * @param [type] Job type
 * @param [count] Total number of jobs of this type
 * @param [totalLatencyMillis] Total latency for all jobs of this type
 */
data class JobTypeStatistics(val type: JobType?, val count: Long, val totalLatencyMillis: Long) {
    /**
     * @return Average latency for a single job. 0 if no jobs of this type were executed, quotient of [totalLatencyMillis] and [count] otherwise.
     */
    val averageLatency get() = if (count > 0) totalLatencyMillis / count else 0L

    internal fun add(latency: Long): JobTypeStatistics =
            JobTypeStatistics(type, count + 1, totalLatencyMillis + latency)

    internal fun addOther(other: JobTypeStatistics) =
            JobTypeStatistics(type, count + other.count, totalLatencyMillis + other.totalLatencyMillis)
}
