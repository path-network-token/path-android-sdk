package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import network.path.mobilenode.library.domain.entity.JobType
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun computeJobResult(
    jobType: JobType,
    jobRequest: JobRequest,
    timeSource: TimeSource,
    block: (JobRequest) -> String
): JobResult {
    var responseBody = ""
    var isResponseKnown = false

    val requestDurationMillis = timeSource.measure {
        try {
            responseBody = block(jobRequest)
            isResponseKnown = true
        } catch (e: IOException) {
            responseBody = e.toString()
        } catch (e: Exception) {
            responseBody = e.toString()
        }
    }

    val status = when (isResponseKnown) {
        true -> calculateJobStatus(requestDurationMillis, jobRequest)
        false -> Status.UNKNOWN
    }

    Timber.d("RUNNER: [$jobRequest] => $status")
    return JobResult(
        checkType = jobType,
        executionUuid = jobRequest.executionUuid,
        responseTime = requestDurationMillis,
        responseBody = responseBody,
        status = status
    )
}

internal inline fun <T> runWithTimeout(timeout: Long, crossinline block: () -> T): T {
    val executor = Executors.newSingleThreadExecutor()
    val f = executor.submit(Callable { block() })
    return f.get(timeout, TimeUnit.MILLISECONDS)
}

internal fun calculateJobStatus(requestDurationMillis: Long, jobRequest: JobRequest): String {
    val degradedAfterMillis = jobRequest.degradedAfter ?: Constants.DEFAULT_DEGRADED_TIMEOUT_MILLIS
    val criticalAfterMillis = jobRequest.criticalAfter ?: Constants.DEFAULT_CRITICAL_TIMEOUT_MILLIS

    return when {
        requestDurationMillis > degradedAfterMillis -> Status.DEGRADED
        requestDurationMillis > criticalAfterMillis -> Status.CRITICAL
        else -> Status.OK
    }
}
