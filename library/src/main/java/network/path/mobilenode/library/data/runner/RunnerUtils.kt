package network.path.mobilenode.library.data.runner

import android.os.SystemClock
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import network.path.mobilenode.library.domain.entity.Status
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun computeJobResult(checkType: CheckType, jobRequest: JobRequest, block: (JobRequest) -> String): JobResult {
    var responseBody = ""
    var isResponseKnown = false

    val requestDurationMillis = measureRealtimeMillis {
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
            checkType = checkType,
            executionUuid = jobRequest.executionUuid,
            responseTime = requestDurationMillis,
            responseBody = responseBody,
            status = status
    )
}

inline fun <T>runWithTimeout(timeout: Long, crossinline block: () -> T): T {
    val executor = Executors.newSingleThreadExecutor()
    val f = executor.submit(Callable { block() })
    return f.get(timeout, TimeUnit.MILLISECONDS)
}

inline fun measureRealtimeMillis(block: () -> Unit): Long {
    val start = SystemClock.elapsedRealtime()
    block()
    return SystemClock.elapsedRealtime() - start
}

fun calculateJobStatus(requestDurationMillis: Long, jobRequest: JobRequest): String {
    val degradedAfterMillis = jobRequest.degradedAfter ?: Constants.DEFAULT_DEGRADED_TIMEOUT_MILLIS
    val criticalAfterMillis = jobRequest.criticalAfter ?: Constants.DEFAULT_CRITICAL_TIMEOUT_MILLIS

    return when {
        requestDurationMillis > degradedAfterMillis -> Status.DEGRADED
        requestDurationMillis > criticalAfterMillis -> Status.CRITICAL
        else -> Status.OK
    }
}
