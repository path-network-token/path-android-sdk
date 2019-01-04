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
import network.path.mobilenode.library.domain.PathSystem.Companion.create
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.JobTypeStatistics
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobList
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.NodeInfo
import network.path.mobilenode.library.domain.entity.WifiSetting
import network.path.mobilenode.library.utils.CustomThreadPoolManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Facade to the whole functionality of Path API.
 *
 * This is the only public class you need to perform Path jobs.
 *
 * To create an instance of this class use static [create] method, which takes [Context] parameter
 * as the only argument.
 */
class PathSystem
internal constructor(
        private val engine: PathEngine,
        private val storage: PathStorage,
        private val jobExecutor: PathJobExecutor,
        private val networkMonitor: NetworkMonitor,
        private val threadManager: CustomThreadPoolManager
) {
    companion object {
        private var INSTANCE: PathSystem? = null

        /**
         * Creates singleton instance of [PathSystem] class.
         *
         * @param [context] Android context which is necessary for some internal sub-systems to work. Can be application context.
         */
        fun create(context: Context): PathSystem {
            if (INSTANCE == null) {
                val gson = createLenientGson()
                val threadManager = CustomThreadPoolManager()
                val okHttpClient = createOkHttpClient()
                val networkMonitor = NetworkMonitor(context)
                val locationProvider = LastLocationProvider(context)
                val storage = PathStorageImpl(context)
                val engine = PathHttpEngine(context, locationProvider, networkMonitor, okHttpClient, gson, storage, threadManager)
                val jobExecutor = PathJobExecutorImpl(okHttpClient, storage, gson)
                INSTANCE = PathSystem(engine, storage, jobExecutor, networkMonitor, threadManager)
            }
            return INSTANCE!!
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

    /**
     * Listener interface which can be used to get informed about changes inside [PathSystem] class
     */
    interface Listener {
        /**
         * This callback is invoked when connection status to Path API changes
         *
         * @param [status] New status value
         */
        fun onStatusChanged(status: ConnectionStatus)

        /**
         * This callback is invoked when ID of the node is updated by the backend.
         *
         * Initially node ID is null and gets updated after the first successful check-in to the backend.
         * Since then received value of node ID is retained locally and never gets changed afterwards.
         */
        fun onNodeId(nodeId: String?)

        /**
         * This callback is invoked after each successful check-in with updated node information.
         *
         * @param [nodeInfo] Optional instance of [NodeInfo] with new information.
         */
        // TODO: Nullable?
        fun onNodeInfoReceived(nodeInfo: NodeInfo?)

        /**
         * This callback is invoked when job execution status is changed.
         *
         * Job execution can be either active or paused. In either case check-ins are still happening on a regular basis.
         * The only difference is that new jobs are not requested in paused state during check-in.
         *
         * @param [isRunning] New value of job execution. true if job execution is active, false if job execution is paused.
         */
        fun onRunningChanged(isRunning: Boolean)

        /**
         * This callback is invoked when executed jobs statistics is changed.
         *
         * Statistics is stored locally and gets wiped after uninstall. Each time job is executed statistics gets updated and
         * this callback is called with updated value.
         *
         * @param [statistics] Updated statistics.
         */
        fun onStatisticsChanged(statistics: List<JobTypeStatistics>)
    }

    /**
     * Helper class with empty implementation of [Listener]. Use it if you only need to override some of the callbacks.
     */
    open class BaseListener : Listener {
        override fun onStatusChanged(status: ConnectionStatus) {}
        override fun onNodeId(nodeId: String?) {}
        override fun onNodeInfoReceived(nodeInfo: NodeInfo?) {}
        override fun onRunningChanged(isRunning: Boolean) {}
        override fun onStatisticsChanged(statistics: List<JobTypeStatistics>) {}
    }

    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<Listener, Boolean>(0))

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    /**
     * Current value of [ConnectionStatus].
     *
     * Initial value is [ConnectionStatus.LOOKING]. If latest check-in was successful this value is [ConnectionStatus.CONNECTED] or [ConnectionStatus.PROXY]
     * based on the fact whether shadowsocks proxy was used or not.
     * This value is [ConnectionStatus.DISCONNECTED] if there was unsuccessful check-in after successful.
     * (This value will stay [ConnectionStatus.LOOKING] until successful check-in)
     */
    val status: ConnectionStatus get() = engine.status
    /**
     * Current value of node ID.
     *
     * @returns null before any successful check-in, non-null afterwards.
     */
    val nodeId: String? get() = engine.nodeId
    /**
     * Latest node information.
     */
    val nodeInfo: NodeInfo? get() = engine.jobList?.nodeInfo
    /**
     * Current status of job execution status.
     *
     * @returns true if job execution is active, false if paused.
     */
    val isRunning: Boolean get() = engine.isRunning

    // Activation
    /**
     * Helper value which can be used to automatically start [PathSystem].
     *
     * Imagine, that you don't want to auto-start [PathSystem] before user accepts some kind of license agreement or in any other conditions.
     * [isActivated] can be used to store user acceptance to later auto-start [PathSystem] on app launch (or device boot).
     * [isActivated] value is not used by internal classes in any way. It is just stored locally on the device.
     *
     * @returns true if user activated [PathSystem] before, false otherwise. Default value is false
     */
    val isActivated: Boolean get() = storage.isActivated

    /**
     * Changes [isActivated] value to true.
     */
    fun activate() {
        storage.isActivated = true
    }

    // Wi-Fi setting
    /**
     * Current value of [WifiSetting].
     *
     * It is used by [PathSystem] to stop jobs execution when device is not connected to Wi-Fi if this value is set to [WifiSetting.WIFI_ONLY].
     * Default value is [WifiSetting.WIFI_AND_CELLULAR].
     */
    var wifiSetting: WifiSetting
        get() = storage.wifiSetting
        set(value) {
            storage.wifiSetting = value
        }

    // Wallet address
    /**
     * Current value of ETH wallet address to receive payment for job execution.
     *
     * Default value is 0x0000000000000000000000000000000000000000.
     * Make sure you provide a valid wallet address otherwise payment will go to nowhere.
     */
    var walletAddress: String
        get() = storage.walletAddress
        set(value) {
            storage.walletAddress = value
        }
    /**
     * @return true is default wallet address is currently in use, false otherwise.
     */
    val hasAddress: Boolean get() = storage.walletAddress != Constants.PATH_DEFAULT_WALLET_ADDRESS

    // Stats
    /**
     * @return Latest jobs execution statistics.
     */
    var statistics: List<JobTypeStatistics> = emptyList()
        private set(value) {
            field = value
            listeners.forEach { it.onStatisticsChanged(value) }
        }

    private val engineListener = object : PathEngine.Listener {
        override fun onStatusChanged(status: ConnectionStatus) {
            listeners.forEach { it.onStatusChanged(status) }
        }

        override fun onRequestReceived(request: JobRequest) {
            threadManager.run {
                Timber.d("SYSTEM: received [$request]")
                val result = jobExecutor.execute(request).get()
                storage.recordStatistics(result.checkType, result.responseTime)
                engine.processResult(result)
                updateStatistics()
                Timber.d("SYSTEM: request result [$result]")
            }
        }

        override fun onNodeId(nodeId: String?) {
            if (nodeId != null) {
                storage.nodeId = nodeId
            }
            listeners.forEach { it.onNodeId(nodeId) }
        }

        override fun onJobListReceived(jobList: JobList?) {
            listeners.forEach { it.onNodeInfoReceived(jobList?.nodeInfo) }
        }

        override fun onRunning(isRunning: Boolean) {
            listeners.forEach { it.onRunningChanged(isRunning) }
        }
    }

    init {
        initTrueTime()
    }

    /**
     * This method initiates connection to Path API and starts performing jobs execution.
     *
     * None of the listeners' callbacks will be called until this method is invoked.
     */
    fun start() {
        jobExecutor.start()
        networkMonitor.start()

        engine.addListener(engineListener)
        engine.start()

        // Initial statistics value
        updateStatistics()
    }

    /**
     * Toggles job execution status.
     *
     * If current value is active, changes it to paused. If current value is paused, changes it to active.
     */
    fun toggle() {
        engine.toggle()
    }

    /**
     * Stops any jobs executions and disconnects from the Path API.
     */
    fun stop() {
        engine.stop()
        engine.removeListener(engineListener)

        networkMonitor.stop()
        jobExecutor.stop()
    }

    // Private methods
    private fun updateStatistics() {
        val allStats = JobType.values()
                .map { storage.statisticsForType(it) }
                .sortedWith(compareByDescending(JobTypeStatistics::count)
                        .then(compareByDescending(JobTypeStatistics::averageLatency)))

        val otherStats = allStats.subList(2, allStats.size - 1)
                .fold(JobTypeStatistics(null, 0, 0)) { total, stats ->
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
