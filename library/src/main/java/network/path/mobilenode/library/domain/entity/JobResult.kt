package network.path.mobilenode.library.domain.entity

internal data class JobResult(
    val type: String = "job-result",
    val checkType: JobType,
    val executionUuid: String,
    val status: String,
    val responseTime: Long,
    val responseBody: String,
    val contentLength: Int = responseBody.length
)
