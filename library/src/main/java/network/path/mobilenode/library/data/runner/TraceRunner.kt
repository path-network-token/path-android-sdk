package network.path.mobilenode.library.data.runner

import com.google.gson.Gson
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.data.runner.mtr.Mtr
import network.path.mobilenode.library.data.runner.mtr.MtrResult
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.endpointHost
import timber.log.Timber

internal class TraceRunner(private val gson: Gson) : Runner {
    companion object {
        init {
            System.loadLibrary("traceroute")
        }
    }

    override val jobType = JobType.TRACEROUTE

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) {
            runWithTimeout(Constants.TRACEROUTE_JOB_TIMEOUT_MILLIS) {
                runTraceJob(it)
            }
        }

    private fun runTraceJob(jobRequest: JobRequest): Pair<String, Long?> {
        val port = jobRequest.endpointPort ?: 0
        val res = Mtr().trace(jobRequest.endpointHost, port, false)
        return if (res != null) {
            val grouped = res.filterNotNull().filter { it.ttl != 0 }.groupBy { "${it.ttl}${it.ip}" }
            val folded = grouped.map {
                val first = it.value.first()
                val filtered = it.value.filterNot { probe -> probe.timeout }
                if (filtered.isEmpty()) {
                    first
                } else {
                    val avg = filtered.map { probe -> probe.delay }.average()
                    val min = filtered.minBy { probe -> probe.delay }?.delay ?: avg
                    val max = filtered.maxBy { probe -> probe.delay }?.delay ?: avg
                    MtrResult(
                        first.ttl,
                        first.host,
                        first.ip,
                        false,
                        avg,
                        min,
                        max,
                        first.err
                    )
                }
            }
            val duration = folded.filter { !it.timeout }.maxBy { it.delay }?.delay
            Timber.d("TRACE: combined result [${folded.fold(StringBuilder()) { sb, r -> sb.append(r).append("\n") }}]")
            gson.toJson(folded) to duration?.toLong()
        } else "" to null
    }
}
