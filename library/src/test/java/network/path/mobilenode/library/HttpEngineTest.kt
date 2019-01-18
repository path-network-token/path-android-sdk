package network.path.mobilenode.library

import android.location.Location
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.verify
import net.jodah.concurrentunit.Waiter
import network.path.mobilenode.library.data.android.LastLocationProvider
import network.path.mobilenode.library.data.android.NetworkMonitor
import network.path.mobilenode.library.data.http.PathHttpEngine
import network.path.mobilenode.library.domain.PathEngine
import network.path.mobilenode.library.domain.PathNativeProcesses
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobList
import network.path.mobilenode.library.utils.CustomThreadPoolManager
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mock.MockInterceptor
import org.junit.Ignore
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import timber.log.Timber
import java.util.concurrent.TimeUnit

// NOTE: This test does not work. There is too much going on behind the scenes in multi-threaded environment.
// Executions halts after HttpEngine tries to call checkIn()
// I will just leave it here for future.
class HttpEngineTest {
    companion object {
        private const val DUMMY_NODE_ID = "DUMMY_NODE_ID"
        private const val DUMMY_WALLET = "DUMMY_WALLET"

        @BeforeAll
        @JvmStatic
        fun staticInit() {
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    println("[$tag] [$priority] $message")
                    if (t != null) {
                        println(t.message)
                    }
                }
            })
        }
    }

    private lateinit var gson: Gson
    private lateinit var engine: PathHttpEngine
    private lateinit var interceptor: MockInterceptor
    private lateinit var locationProvider: LastLocationProvider
    private lateinit var threadManager: CustomThreadPoolManager
    private lateinit var listener: PathEngine.Listener

    @BeforeEach
    fun init() {
        gson = createLenientGson()
        threadManager = CustomThreadPoolManager()

        val nativeProcesses = Mockito.mock(PathNativeProcesses::class.java)
        val networkMonitor = Mockito.mock(NetworkMonitor::class.java)
        val storage = Mockito.mock(PathStorage::class.java)
        Mockito.`when`(storage.nodeId).thenReturn(DUMMY_NODE_ID)
        Mockito.`when`(storage.walletAddress).thenReturn(DUMMY_WALLET)
        locationProvider = Mockito.mock(LastLocationProvider::class.java)
        interceptor = MockInterceptor()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        engine = PathHttpEngine(
            nativeProcesses,
            locationProvider,
            networkMonitor,
            httpClient,
            gson,
            storage,
            threadManager,
            true
        )

        listener = Mockito.mock(PathEngine.Listener::class.java)
        engine.addListener(listener)
    }

    @Test
    @Ignore
    fun testStart() {
        val success = JobList(
            type = "job-list",
            nodeId = DUMMY_NODE_ID,
            asn = 12345,
            asOrganization = "Some organization",
            networkPrefix = "192.168.0.0/24",
            location = "Earth",
            jobs = emptyList()
        )
        val location = Mockito.mock(Location::class.java)
        Mockito.`when`(location.latitude).thenReturn(-30.0)
        Mockito.`when`(location.longitude).thenReturn(50.0)
        Mockito.`when`(locationProvider.location()).thenReturn(location)
        interceptor
            .addRule()
            .post()
            .url(Constants.HTTP_TEST_URL + "/checkin/$DUMMY_NODE_ID")
            .respond(ResponseBody.create(MediaType.get("application/json"), gson.toJson(success)))

        val waiter = Waiter()
        threadManager.run("test", 0) {
            argumentCaptor<ConnectionStatus>().apply {
                verify(listener).onStatusChanged(capture())
                waiter.assertEquals(firstValue, ConnectionStatus.CONNECTED)
            }
            waiter.resume()
        }

        engine.start()
        waiter.await(30, TimeUnit.SECONDS)
    }

    private fun createLenientGson(): Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
}
