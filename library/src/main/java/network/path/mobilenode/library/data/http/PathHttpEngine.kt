package network.path.mobilenode.library.data.http

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.instacart.library.truetime.TrueTime
import network.path.mobilenode.library.BuildConfig
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.data.android.LastLocationProvider
import network.path.mobilenode.library.data.android.NetworkMonitor
import network.path.mobilenode.library.domain.PathEngine
import network.path.mobilenode.library.domain.PathNativeProcesses
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.*
import network.path.mobilenode.library.utils.CustomThreadPoolManager
import network.path.mobilenode.library.utils.isPortInUse
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.HttpException
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.math.max

internal class PathHttpEngine(
    private val nativeProcesses: PathNativeProcesses,
    private val lastLocationProvider: LastLocationProvider,
    private val networkMonitor: NetworkMonitor,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val storage: PathStorage,
    private val threadManager: CustomThreadPoolManager,
    private var isTest: Boolean
) : PathEngine, NetworkMonitor.Listener {
    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val HEARTBEAT_INTERVAL_ERROR_MS = 5_000L

        private const val MAX_JOBS = 10
        private const val MAX_RETRIES = 5

        private const val PROXY_RESTART_TIMEOUT = 3_600_000L // 1 hour

        fun create(
            context: Context,
            threadManager: CustomThreadPoolManager,
            okHttpClient: OkHttpClient,
            storage: PathStorage,
            gson: Gson,
            isTest: Boolean
        ): PathEngine {
            val networkMonitor = NetworkMonitor(context)
            val locationProvider = LastLocationProvider(context)
            val nativeProcesses = PathNativeProcessesImpl(context, storage)
            return PathHttpEngine(
                nativeProcesses,
                locationProvider,
                networkMonitor,
                okHttpClient,
                gson,
                storage,
                threadManager,
                isTest
            )
        }
    }

    init {
        initTrueTime(0L)
    }

    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<PathEngine.Listener, Boolean>(0))

    private val currentExecutionUuids = ConcurrentHashMap<String, Boolean>()

    private var retryCounter = 0
    private var useProxy = false
    private var httpService: PathService? = null

    private var checkInTask: Future<*>? = null
    private var nativeTask: Future<*>? = null
    private var pollTask: Future<*>? = null

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

    override var isJobExecutionRunning = true
        private set(value) {
            field = value
            listeners.forEach { it.onRunning(value) }
        }

    override fun start() {
        networkMonitor.start()
        networkMonitor.addListener(this)
        lastLocationProvider.start()

        httpService = getHttpService(false)
        performCheckIn(0L)

//        kotlin.concurrent.fixedRateTimer("TEST", false, java.util.Date(), 5_000) {
//            launch {
//                status.send(if (status.valueOrNull == ConnectionStatus.CONNECTED) ConnectionStatus.DISCONNECTED else ConnectionStatus.CONNECTED)
//            }
//        }
    }

    override fun processResult(result: JobResult) {
        if (result.executionUuid == "DUMMY_UUID") return

        val nodeId = storage.nodeId ?: return

        threadManager.run("processResult") {
            executeServiceCall {
                httpService?.postResult(nodeId, result.executionUuid, result)
            }
            currentExecutionUuids.remove(result.executionUuid)
        }
    }

    override fun stop() {
        stopNativeProcesses()

        networkMonitor.removeListener(this)
        networkMonitor.stop()
        lastLocationProvider.stop()

        // Reset values to defaults
        status = ConnectionStatus.LOOKING
        jobList = null
        isJobExecutionRunning = true
    }

    override fun toggleJobExecution(): Boolean {
        isJobExecutionRunning = !isJobExecutionRunning
        Timber.d("HTTP: changed status to [$isJobExecutionRunning]")
        return isJobExecutionRunning
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
            Timber.d("HTTP: Checking in...")
            val result = executeServiceCall {
                val checkIn = createCheckInMessage()
                if (checkIn != null) {
                    httpService?.checkIn(storage.nodeId ?: "", checkIn)
                } else {
                    // Failed to create check-in message: location is missing
                    retryCounter++
                    null
                }
            }
            if (result != null) {
                processJobs(result)
            } else {
                // No result from check in, we must have disconnected
                if (status != ConnectionStatus.LOOKING) {
                    status = ConnectionStatus.DISCONNECTED
                }
            }

            val isConnected = status == ConnectionStatus.CONNECTED || status == ConnectionStatus.PROXY
            val nextDelay = if (retryCounter > 0 || !isConnected) HEARTBEAT_INTERVAL_ERROR_MS else HEARTBEAT_INTERVAL_MS
            Timber.d("HTTP: Scheduling check-in in [$nextDelay ms]")
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
            pollJobs(0L)
        }

//        test()
    }

    private fun pollJobs(delay: Long) {
        pollTask?.cancel(true)
        pollTask = threadManager.run("pollJobs", delay) {
            while (true) {
                // Process only jobs which were not marked as active.
                val ids = currentExecutionUuids.filterValues { !it }.keys.toList()
                if (ids.isEmpty()) break

                // Process inactive jobs
                Timber.d("HTTP: ${ids.size} jobs to be processed")
                ids.forEach { uuid ->
                    // Mark job as active
                    currentExecutionUuids[uuid] = true
                    processJob(uuid)
                }
            }
        }
    }

    private fun processJob(executionUuid: String) {
        threadManager.run("processJob") {
            val details = executeServiceCall {
                httpService?.requestDetails(executionUuid)
            }
            if (details != null) {
                details.executionUuid = executionUuid
                notifyRequest(details)
            } else {
                currentExecutionUuids.remove(executionUuid)
            }
        }
    }

    private fun notifyRequest(request: JobRequest) {
        listeners.forEach { it.onRequestReceived(request) }
    }

    private fun createCheckInMessage(): CheckIn? {
        val location = try {
            lastLocationProvider.location()
        } catch (e: Exception) {
            Timber.w(e, "HTTP: could not get location: $e")
            null
        } ?: return null

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
        val jobsToRequest =
            if (isJobExecutionRunning && requestJobs) max(MAX_JOBS - currentExecutionUuids.size, 0) else 0
        return CheckIn(
            nodeId = storage.nodeId,
            wallet = storage.walletAddress,
            lat = location.latitude.toString(),
            lon = location.longitude.toString(),
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
        Timber.w(e, "HTTP: Service call exception [$e]")
        var fallback = true
        when (e) {
            is UnknownHostException -> fallback = false
            is HttpException -> if (e.code() == 422) {
                val body = e.response().body()
                Timber.w("HTTP: exception response [$body]")
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
        return PathServiceImpl(client, gson, isTest)
    }

    private fun stopNativeProcesses() {
        nativeTask?.cancel(true)
        nativeTask = null

        nativeProcesses.stop()
    }

    private fun startNativeProcesses() {
        nativeProcesses.start()

        nativeTask?.cancel(true)
        nativeTask = threadManager.run("nativeProcesses", PROXY_RESTART_TIMEOUT) {
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

    private fun initTrueTime(delay: Long) {
        threadManager.run("TrueTime", delay) {
            try {
                TrueTime.build()
                    .withLoggingEnabled(BuildConfig.DEBUG)
                    .withNtpHost("time.google.com")
                    .initialize()
                Timber.d("TRUE TIME: initialised")
            } catch (e: Exception) {
                Timber.w(e, "TRUE TIME: failed to initialise: $e")
                // Retry in 1 second
                initTrueTime(1000L)
            }
        }
    }

    private fun test() {
//        val dummyRequest = JobRequest(protocol = "tcp", payload = "HELO\n", endpointAddress = "smtp.gmail.com", endpointPort = 587, jobUuid = "DUMMY_UUID", executionUuid = "DUMMY_UUID")
//        val dummyRequest = JobRequest(protocol = "tcp", payload = "GET /\n\n", endpointAddress = "www.google.com", endpointPort = 80, jobUuid = "DUMMY_UUID", executionUuid = "DUMMY_UUID")
        val dummyRequest = JobRequest(
            protocol = "",
            method = "traceroute",
            payload = "HELLO",
            endpointAddress = "www.google.com",
            jobUuid = "DUMMY_UUID",
            executionUuid = "DUMMY_UUID"
        )
        notifyRequest(dummyRequest)
    }
}
