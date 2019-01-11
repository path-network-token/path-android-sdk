package network.path.mobilenode.library

import android.content.Context
import network.path.mobilenode.library.data.storage.PathStorageImpl
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.WifiSetting
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class PathStorageTest {
    companion object {
        private const val WALLET_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        private const val DUMMY_VALUE = "DUMMY_VALUE"
    }

    private lateinit var storage: PathStorage

    @BeforeEach
    fun init() {
        val context = Mockito.mock(Context::class.java)
        val sharedPrefs = MockSharedPreferences()
        Mockito.`when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPrefs)
        storage = PathStorageImpl(context, true)
    }

    @Test
    fun testDefaultValues() {
        Assertions.assertEquals(storage.walletAddress, Constants.PATH_DEFAULT_WALLET_ADDRESS)
        Assertions.assertNull(storage.nodeId)
        Assertions.assertFalse(storage.autoStart)
        Assertions.assertEquals(storage.wifiSetting, WifiSetting.WIFI_AND_CELLULAR)
        Assertions.assertNull(storage.proxyDomain)
        for (type in JobType.values()) {
            val stat = storage.statisticsForType(type)
            Assertions.assertEquals(stat.type, type)
            Assertions.assertEquals(stat.count, 0)
            Assertions.assertEquals(stat.totalLatencyMillis, 0)
        }
    }

    @Test
    fun testStats() {
        val type = JobType.HTTP
        val stats = storage.recordStatistics(type, 1000)
        val otherStats = storage.statisticsForType(type)
        Assertions.assertEquals(stats, otherStats)

        val doubleStats = storage.recordStatistics(type, 1000)
        Assertions.assertEquals(doubleStats.type, type)
        Assertions.assertEquals(doubleStats.count, 2)
        Assertions.assertEquals(doubleStats.averageLatency, 1000)

        storage.recordStatistics(type, 1000)
        val tripleStats = storage.statisticsForType(type)
        Assertions.assertEquals(tripleStats.type, type)
        Assertions.assertEquals(tripleStats.count, 3)
        Assertions.assertEquals(tripleStats.averageLatency, 1000)
    }

    // Test below are of low value. All they do is test implementation of MockSharedPreferences
    @Test
    fun testWalletAddress() {
        storage.walletAddress = WALLET_ADDRESS
        Assertions.assertEquals(storage.walletAddress, WALLET_ADDRESS)
        storage.walletAddress = Constants.PATH_DEFAULT_WALLET_ADDRESS
        Assertions.assertEquals(storage.walletAddress, Constants.PATH_DEFAULT_WALLET_ADDRESS)
    }

    @Test
    fun testNodeId() {
        storage.nodeId = DUMMY_VALUE
        Assertions.assertEquals(storage.nodeId, DUMMY_VALUE)
        storage.nodeId = null
        Assertions.assertNull(storage.nodeId)
    }

    @Test
    fun testAutoStart() {
        storage.autoStart = true
        Assertions.assertTrue(storage.autoStart)
        storage.autoStart = false
        Assertions.assertFalse(storage.autoStart)
    }

    @Test
    fun testWifiSetting() {
        storage.wifiSetting = WifiSetting.WIFI_ONLY
        Assertions.assertEquals(storage.wifiSetting, WifiSetting.WIFI_ONLY)
        storage.wifiSetting = WifiSetting.WIFI_AND_CELLULAR
        Assertions.assertEquals(storage.wifiSetting, WifiSetting.WIFI_AND_CELLULAR)
    }

    @Test
    fun testProxyDomain() {
        storage.proxyDomain = DUMMY_VALUE
        Assertions.assertEquals(storage.proxyDomain, DUMMY_VALUE)
        storage.proxyDomain = null
        Assertions.assertNull(storage.proxyDomain)
    }
}
