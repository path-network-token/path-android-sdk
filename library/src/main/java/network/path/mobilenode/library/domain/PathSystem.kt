package network.path.mobilenode.library.domain

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.instacart.library.truetime.TrueTimeRx
import io.reactivex.schedulers.Schedulers
import network.path.mobilenode.library.BuildConfig
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.data.android.LastLocationProvider
import network.path.mobilenode.library.data.android.NetworkMonitor
import network.path.mobilenode.library.data.http.CustomDns
import network.path.mobilenode.library.data.http.PathHttpEngine
import network.path.mobilenode.library.data.runner.PathJobExecutorImpl
import network.path.mobilenode.library.data.storage.PathStorageImpl
import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.CheckTypeStatistics
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobList
import network.path.mobilenode.library.domain.entity.JobRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class PathSystem(
        private val engine: PathEngine,
        val storage: PathStorage,
        private val jobExecutor: PathJobExecutor,
        private val networkMonitor: NetworkMonitor
) : PathEngine.Listener {
    companion object {
        fun create(context: Context): PathSystem {
            val gson = createLenientGson()
            val okHttpClient = createOkHttpClient()
            val networkMonitor = NetworkMonitor(context)
            val locationProvider = LastLocationProvider(context)
            val storage = PathStorageImpl(context)
            val engine = PathHttpEngine(context, locationProvider, networkMonitor, okHttpClient, gson, storage)
            val jobExecutor = PathJobExecutorImpl(okHttpClient, storage, gson)
            return PathSystem(engine, storage, jobExecutor, networkMonitor)
        }

        private fun createLenientGson(): Gson = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()

        private fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
                .readTimeout(Constants.JOB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .writeTimeout(Constants.JOB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .connectTimeout(Constants.JOB_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .addInterceptor { chain ->
                    try {
                        chain.proceed(chain.request())
                    } catch (e: Throwable) {
                        throw if (e is IOException) e else IOException(e)
                    }
                }
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
//            level = HttpLoggingInterceptor.Level.BODY
                })
                .dns(CustomDns())
                .build()

    }

    interface Listener {
        fun onStatusChanged(status: ConnectionStatus)
        fun onNodeId(nodeId: String?)
        fun onJobListReceived(jobList: JobList?)
        fun onRunningChanged(isRunning: Boolean)
        fun onStatisticsChanged(statistics: List<CheckTypeStatistics>)
    }

    open class BaseListener : Listener {
        override fun onStatusChanged(status: ConnectionStatus) {}
        override fun onNodeId(nodeId: String?) {}
        override fun onJobListReceived(jobList: JobList?) {}
        override fun onRunningChanged(isRunning: Boolean) {}
        override fun onStatisticsChanged(statistics: List<CheckTypeStatistics>) {}
    }

    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<Listener, Boolean>(0))

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    val status: ConnectionStatus get() = engine.status
    val nodeId: String? get() = engine.nodeId
    val jobList: JobList? get() = engine.jobList
    val isRunning: Boolean get() = engine.isRunning

    var statistics: List<CheckTypeStatistics> = emptyList()
        private set(value) {
            field = value
            listeners.forEach { it.onStatisticsChanged(value) }
        }

    init {
        initTrueTime()
    }

    fun start() {
        jobExecutor.start()
        networkMonitor.start()

        engine.addListener(this)
        engine.start()

        // Initial statistics value
        updateStatistics()
    }

    fun toggle() {
        engine.toggle()
    }

    fun stop() {
        engine.stop()
        engine.removeListener(this)

        networkMonitor.stop()
        jobExecutor.stop()
    }

    // PathEngine listener
    override fun onStatusChanged(status: ConnectionStatus) {
        listeners.forEach { it.onStatusChanged(status) }
    }

    override fun onRequestReceived(request: JobRequest) {
        process(request)
    }

    override fun onNodeId(nodeId: String?) {
        if (nodeId != null) {
            storage.nodeId = nodeId
        }
        listeners.forEach { it.onNodeId(nodeId) }
    }

    override fun onJobListReceived(jobList: JobList?) {
        listeners.forEach { it.onJobListReceived(jobList) }
    }

    override fun onRunning(isRunning: Boolean) {
        listeners.forEach { it.onRunningChanged(isRunning) }
    }

    // Private methods
    private fun process(request: JobRequest) {
        Timber.d("SYSTEM: received [$request]")
        val result = jobExecutor.execute(request).get()
        storage.recordStatistics(result.checkType, result.responseTime)
        engine.processResult(result)
        updateStatistics()
        Timber.d("SYSTEM: request result [$result]")
    }

    private fun updateStatistics() {
        val allStats = CheckType.values()
                .map { storage.statisticsForType(it) }
                .sortedWith(compareByDescending(CheckTypeStatistics::count)
                        .then(compareByDescending(CheckTypeStatistics::averageLatency)))

        val otherStats = allStats.subList(2, allStats.size - 1)
                .fold(CheckTypeStatistics(null, 0, 0)) { total, stats ->
                    total.addOther(stats)
                }

        statistics = listOf(allStats[0], allStats[1], otherStats)
    }

    @SuppressLint("CheckResult")
    private fun initTrueTime() {
        TrueTimeRx.build()
                .initializeRx("time.google.com")
                .subscribeOn(Schedulers.io())
                .subscribe(
                        { date -> Timber.d("TRUE TIME: initialised [$date]") },
                        { throwable -> Timber.w("TRUE TIME: initialisation failed: $throwable") }
                )
    }
}
