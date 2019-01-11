package network.path.mobilenode.library.data.runner

import com.google.gson.Gson
import network.path.mobilenode.library.domain.PathJobExecutor
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import okhttp3.OkHttpClient
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class PathJobExecutorImpl(
    private val okHttpClient: OkHttpClient,
    private val storage: PathStorage,
    private val gson: Gson,
    private val timeSource: TimeSource
) : PathJobExecutor {
    private lateinit var executor: ExecutorService

    override fun start() {
        executor = Executors.newCachedThreadPool()
    }

    override fun execute(request: JobRequest): Future<JobResult> =
        executor.submit(Callable {
            request.findRunner().runJob(request, timeSource)
        })

    override fun stop() {
        executor.shutdown()
    }

    private fun JobRequest.findRunner(): Runner = when {
        protocol == null -> FallbackRunner
        protocol.startsWith(prefix = "http", ignoreCase = true) -> HttpRunner(okHttpClient, storage)
        protocol.startsWith(prefix = "tcp", ignoreCase = true) -> TcpRunner()
        protocol.startsWith(prefix = "udp", ignoreCase = true) -> UdpRunner()
        method.orEmpty().startsWith(prefix = "traceroute", ignoreCase = true) -> TraceRunner(gson)
        else -> FallbackRunner
    }
}
