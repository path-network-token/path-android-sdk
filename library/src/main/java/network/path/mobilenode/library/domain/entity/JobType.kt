package network.path.mobilenode.library.domain.entity

/**
 * Enum of possible job type values for [JobTypeStatistics]
 */
enum class JobType {
    /**
     * HTTP job
     */
    HTTP,
    /**
     * TCP job
     */
    TCP,
    /**
     * UDP job
     */
    UDP,
    /**
     * Tracerout job
     */
    TRACEROUTE,
    /**
     * DNS job
     */
    DNS,
    /**
     * Unknown job
     */
    UNKNOWN
}
