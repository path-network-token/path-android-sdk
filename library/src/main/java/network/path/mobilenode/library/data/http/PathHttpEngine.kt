package network.path.mobilenode.library.data.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import network.path.mobilenode.library.BuildConfig
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.data.android.LastLocationProvider
import network.path.mobilenode.library.data.android.NetworkMonitor
import network.path.mobilenode.library.domain.DomainGenerator
import network.path.mobilenode.library.domain.PathEngine
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.WifiSetting
import network.path.mobilenode.library.domain.entity.CheckIn
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobList
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import network.path.mobilenode.library.utils.CustomThreadPoolManager
import network.path.mobilenode.library.utils.Executable
import network.path.mobilenode.library.utils.GuardedProcessPool
import network.path.mobilenode.library.utils.isPortInUse
import network.path.mobilenode.library.utils.printThread
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.HttpException
import timber.log.Timber
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.math.max

class PathHttpEngine(
        private val context: Context,
        private val lastLocationProvider: LastLocationProvider,
        private val networkMonitor: NetworkMonitor,
        private val okHttpClient: OkHttpClient,
        private val gson: Gson,
        private val storage: PathStorage
) : PathEngine, NetworkMonitor.Listener {
    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_ERROR_MS = 5_000L
        private const val POLL_INTERVAL_MS = 9_000L

        private const val MAX_JOBS = 10
        private const val MAX_RETRIES = 5

        private const val TIMEOUT = 600
        private const val PROXY_RESTART_TIMEOUT = 3_600_000L // 1 hour

        private const val PROXY_PORT = 443
        private const val PROXY_PASSWORD = "PathNetwork"
        private const val PROXY_ENCRYPTION_METHOD = "aes-256-cfb"
    }

    private val threadManager = CustomThreadPoolManager()

    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<PathEngine.Listener, Boolean>(0))

    private val currentExecutionUuids = ConcurrentHashMap<String, Boolean>()

    private var retryCounter = 0
    private var useProxy = false
    private var httpService: PathService? = null

    private var checkInTask: Future<*>? = null

    private val ssLocal = GuardedProcessPool()
    private val simpleObfs = GuardedProcessPool()

    override var status = ConnectionStatus.LOOKING
        private set(value) {
            field = value
            listeners.forEach { it.onStatusChanged(value) }
        }

    override var nodeId = storage.nodeId
        private set(value) {
            field = value
            listeners.forEach { it.onNodeId(value) }
        }

    override var jobList: JobList? = null
        private set(value) {
            field = value
            listeners.forEach { it.onJobListReceived(value) }
        }

    override var isRunning = true
        private set(value) {
            field = value
            listeners.forEach { it.onRunning(value) }
        }

    override fun start() {
        networkMonitor.addListener(this)

        httpService = getHttpService(false)
        performCheckIn(0L)
        pollJobs(0L)

//        kotlin.concurrent.fixedRateTimer("TEST", false, java.util.Date(), 5_000) {
//            launch {
//                status.send(if (status.valueOrNull == ConnectionStatus.CONNECTED) ConnectionStatus.DISCONNECTED else ConnectionStatus.CONNECTED)
//            }
//        }
    }

    override fun processResult(result: JobResult) {
        if (result.executionUuid == "DUMMY_UUID") return

        val nodeId = storage.nodeId ?: return

        threadManager.run {
            executeServiceCall {
                httpService?.postResult(nodeId, result.executionUuid, result)
            }

            currentExecutionUuids.remove(result.executionUuid)

            val inactiveUuids = currentExecutionUuids.filterValues { !it }
            Timber.d("HTTP: ${currentExecutionUuids.size} jobs in the pool, ${inactiveUuids.size} jobs not yet active...")
//        if (inactiveUuids.isEmpty()) {
//            checkIn()
//        }
        }
    }

    override fun stop() {
        threadManager.stop()

        // Kill them all
        Executable.killAll(context)

        networkMonitor.removeListener(this)
    }

    override fun toggle() {
        isRunning = !isRunning
        Timber.d("HTTP: changed status to [$isRunning]")
    }

    override fun addListener(l: PathEngine.Listener) = listeners.add(l)
    override fun removeListener(l: PathEngine.Listener) = listeners.remove(l)

    override fun onStatusChanged(connected: Boolean) {
        if (connected) {
            performCheckIn(500L)
        }
    }

    private fun performCheckIn(delay: Long) {
        checkInTask?.cancel(true)
        checkInTask = threadManager.run("checkIn", delay) {
            printThread("performCheckIn")
            Timber.d("HTTP: Checking in...")
            val result = executeServiceCall {
                httpService?.checkIn(storage.nodeId ?: "", createCheckInMessage())
            }
            if (result != null) {
                processJobs(result)
            } else {
                // No result from check in, we must have disconnected
                if (status != ConnectionStatus.LOOKING) {
                    status = ConnectionStatus.DISCONNECTED
                }
            }

            Timber.d("HTTP: Scheduling check in...")
            val nextDelay = if (retryCounter > 0) HEARTBEAT_INTERVAL_ERROR_MS else HEARTBEAT_INTERVAL_MS
            performCheckIn(nextDelay)
        }
    }

    private fun processJobs(list: JobList) {
        Timber.d("HTTP: received job list [$list]")
        if (list.nodeId != null) {
            nodeId = list.nodeId
        }
        jobList = list
        status = if (useProxy) ConnectionStatus.PROXY else ConnectionStatus.CONNECTED

        if (list.jobs.isNotEmpty()) {
            val ids = list.jobs.map { it.executionUuid to false }
            // Add new jobs to the pool
            currentExecutionUuids.putAll(ids)
        }

//        test()
    }

    private fun pollJobs(delay: Long) {
        threadManager.run("pollJobs", delay) {
            printThread("pollJobs")
            Timber.d("HTTP: Start processing jobs...")
            if (currentExecutionUuids.isNotEmpty()) {
                // Process only jobs which were not marked as active.
                val ids = currentExecutionUuids.filterValues { !it }.keys.toList()
                Timber.d("HTTP: ${ids.size} jobs to be processed")
                ids.forEach { processJob(it) }
            }

            Timber.d("HTTP: Scheduling next jobs processing cycle...")
            pollJobs(POLL_INTERVAL_MS)
        }
    }

    private fun processJob(executionUuid: String) {
        threadManager.run {
            printThread("processJob")
            val details = executeServiceCall {
                httpService?.requestDetails(executionUuid)
            }
            if (details != null) {
                details.executionUuid = executionUuid
                // Mark job as active
                currentExecutionUuids[executionUuid] = true
                notifyRequest(details)
            } else {
                currentExecutionUuids.remove(executionUuid)
            }
        }
    }

    private fun notifyRequest(request: JobRequest) {
        listeners.forEach { it.onRequestReceived(request) }
    }

    private fun createCheckInMessage(): CheckIn {
        val location = try {
            lastLocationProvider.location()
        } catch (e: Exception) {
            null
        }
        var requestJobs = true
        if (storage.wifiSetting == WifiSetting.WIFI_ONLY) {
            // Make sure we are on WiFi if wifiOnly setting is used
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = networkMonitor.connectivityManager.activeNetwork
                val capabilities = networkMonitor.connectivityManager.getNetworkCapabilities(network)
                requestJobs = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                Timber.d("HTTP: network [$network], setting [${storage.wifiSetting}, requestJobs = $requestJobs")
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = networkMonitor.connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                requestJobs = networkInfo?.isConnected == true
                Timber.d("HTTP: network info [$networkInfo], setting [${storage.wifiSetting}], requestJobs = $requestJobs")
            }
        }
        val jobsToRequest = if (isRunning && requestJobs) max(MAX_JOBS - currentExecutionUuids.size, 0) else 0
        return CheckIn(
                nodeId = storage.nodeId,
                wallet = storage.walletAddress,
                lat = location?.latitude?.toString() ?: "0.0",
                lon = location?.longitude?.toString() ?: "0.0",
                returnJobsMax = jobsToRequest
        )
    }

    private fun <T> executeServiceCall(call: () -> Call<T>?): T? = try {
        val result = call()?.execute()?.body()
        if (result != null) {
            retryCounter = 0
        }
        result
    } catch (e: Exception) {
        var fallback = true
        when (e) {
            is UnknownHostException -> fallback = false
            is HttpException -> if (e.code() == 422) {
                val body = e.response().body()
                Timber.w("HTTP exception: $body")
                // TODO: Parse
                fallback = false
            }
        }
        if (fallback) {
            if (++retryCounter >= MAX_RETRIES) {
                Timber.w("HTTP: switching proxy mode to [${!useProxy}]")
                retryCounter = 0
                httpService = getHttpService(!useProxy)
            }
        }
        Timber.w("HTTP: Service call exception: $e")
        null
    }

    private fun getHttpService(useProxy: Boolean): PathService {
        val host = Constants.LOCALHOST
        val port = Constants.SS_LOCAL_PORT

        Timber.d("HTTP: creating new service [$useProxy]...")

        if (useProxy) {
            // Start the processes
            startNativeProcesses()
        }

        // Verify that ss-local is actually running before using it as a proxy
        val client = if (useProxy && isPortInUse(port)) {
            Timber.d("HTTP: proxy port [$port] is in use, connecting")
            this.useProxy = true
            okHttpClient.newBuilder().addProxy(host, port).build()
        } else {
            if (useProxy) {
                Timber.d("HTTP: proxy port [$port] is not in use, proxy is not running")
            } else {
                Timber.d("HTTP: proxy is not required")
            }
            this.useProxy = false
            okHttpClient
        }
        return PathServiceImpl(client, gson)
    }

    private fun startNativeProcesses() {
        val host = DomainGenerator.findDomain(storage)
        if (host != null) {
            Timber.d("HTTP: found proxy domain [$host]")
            Executable.killAll(context)

            val libs = context.applicationInfo.nativeLibraryDir
            val obfsCmd = mutableListOf(
                    File(libs, Executable.SIMPLE_OBFS).absolutePath,
                    "-s", host,
                    "-p", PROXY_PORT.toString(),
                    "-l", Constants.SIMPLE_OBFS_PORT.toString(),
                    "-t", TIMEOUT.toString(),
                    "--obfs", "http"
            )
            if (BuildConfig.DEBUG) {
                obfsCmd.add("-v")
            }
            simpleObfs.start(obfsCmd)

            val cmd = mutableListOf(
                    File(libs, Executable.SS_LOCAL).absolutePath,
                    "-u",
                    "-s", Constants.LOCALHOST,
                    "-p", Constants.SIMPLE_OBFS_PORT.toString(),
                    "-k", PROXY_PASSWORD,
                    "-m", PROXY_ENCRYPTION_METHOD,
                    "-b", Constants.LOCALHOST,
                    "-l", Constants.SS_LOCAL_PORT.toString(),
                    "-t", TIMEOUT.toString()
            )
            if (BuildConfig.DEBUG) {
                cmd.add("-v")
            }

            ssLocal.start(cmd)
        } else {
            Timber.w("HTTP: proxy domain not found")
        }

        threadManager.run("nativeProcesses", PROXY_RESTART_TIMEOUT) {
            startNativeProcesses()
        }
    }

    private fun OkHttpClient.Builder.addProxy(host: String, port: Int): OkHttpClient.Builder =
            proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port)))
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val url = request.url().newBuilder().scheme("http").build()
                        chain.proceed(request.newBuilder().url(url).build())
                    }

    private fun test() {
//        val dummyRequest = JobRequest(protocol = "tcp", payload = "HELO\n", endpointAddress = "smtp.gmail.com", endpointPort = 587, jobUuid = "DUMMY_UUID", executionUuid = "DUMMY_UUID")
//        val dummyRequest = JobRequest(protocol = "tcp", payload = "GET /\n\n", endpointAddress = "www.google.com", endpointPort = 80, jobUuid = "DUMMY_UUID", executionUuid = "DUMMY_UUID")
        val dummyRequest = JobRequest(protocol = "", method = "traceroute", payload = "HELLO", endpointAddress = "www.google.com", jobUuid = "DUMMY_UUID", executionUuid = "DUMMY_UUID")
        notifyRequest(dummyRequest)
    }
}
