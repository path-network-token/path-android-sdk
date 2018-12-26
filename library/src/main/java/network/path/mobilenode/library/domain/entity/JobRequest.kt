package network.path.mobilenode.library.domain.entity

import network.path.mobilenode.library.Constants

data class JobRequest(
        val type: String = "job-request",
        val protocol: String? = null,
        val method: String? = null,
        val headers: List<Map<String, String>>? = null,
        val payload: String? = null,
        val endpointAddress: String? = null,
        val endpointPort: Int? = null,
        val endpointAdditionalParams: String? = null,
        val degradedAfter: Long? = null,
        val criticalAfter: Long? = null,
        val criticalResponses: List<JobCriticalResponse> = emptyList(),
        val validResponses: List<JobValidResponse> = emptyList(),
        val jobUuid: String,
        var executionUuid: String
)

data class JobValidResponse(val headerStatus: String, val bodyContains: String)
data class JobCriticalResponse(val headerStatus: String, val bodyContains: String)

val JobRequest.endpointHost: String
    get() {
        endpointAddress ?: throw java.io.IOException("Missing endpoint address in $this")
        val regex = "^\\w+://".toRegex(RegexOption.IGNORE_CASE)
        return endpointAddress.replaceFirst(regex, "").replaceAfter('/', "")
    }

fun JobRequest.endpointPortOrDefault(default: Int): Int =
        (endpointPort ?: default).coerceIn(Constants.TCP_UDP_PORT_RANGE)
