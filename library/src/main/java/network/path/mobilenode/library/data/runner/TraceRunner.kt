package network.path.mobilenode.library.data.runner

import android.content.Context
import com.google.gson.Gson
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.endpointHost
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class TraceResult(
    val target: String,
    val targetIp: String,
    val maxHops: Int,
    val packetSize: Int,
    val probesPerHop: Int,
    val hops: List<Hop>
)

data class Hop(val ip: String, val rtts: List<Double>, val lost: Int)

internal class TraceRunner(private val context: Context, private val gson: Gson) : Runner {
    override val jobType = JobType.TRACEROUTE

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) {
            runWithTimeout(Constants.TRACEROUTE_JOB_TIMEOUT_MILLIS) {
                runTraceJob(it)
            }
        }

    private fun runTraceJob(jobRequest: JobRequest): Pair<String, Long?> {
        val libs = context.applicationInfo.nativeLibraryDir
        val cmd = listOf(
            File(libs, "libtraceroute.so").absolutePath,
            "--icmp", "--wait=1,3,10", "-n", "--queries=10", jobRequest.endpointHost
        )

        val p = ProcessBuilder(cmd).start()
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(p.inputStream)).useLines {
            it.forEach { line -> sb.append(line) }
        }
        p.destroy()

        val result = gson.fromJson(sb.toString(), TraceResult::class.java)
        return sb.toString() to result.hops.lastOrNull()?.rtts?.average()?.toLong()
    }
}
