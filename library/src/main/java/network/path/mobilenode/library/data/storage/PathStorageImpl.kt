package network.path.mobilenode.library.data.storage

import android.content.Context
import android.preference.PreferenceManager
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.WifiSetting
import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.CheckTypeStatistics
import network.path.mobilenode.library.utils.prefs
import network.path.mobilenode.library.utils.prefsOptional

class PathStorageImpl(context: Context) : PathStorage {
    companion object {
        private const val PATH_ADDRESS_KEY = "PATH_ADDRESS_KEY"

        private const val NODE_ID_KEY = "NODE_ID_KEY"
        private const val IS_SERVICE_RUNNING_KEY = "IS_SERVICE_RUNNING_KEY"
        //Reserved keys: "COMPLETED_JOBS_KEY"

        private const val CHECKS_COUNT_KEY_SUFFIX = "_CHECKS_COUNT_KEY"
        private const val CHECKS_LATENCY_KEY_SUFFIX = "_LATENCY_MILLIS_KEY"

        private const val PROXY_DOMAIN_KEY = "PROXY_DOMAIN_KEY"

        private const val WIFI_SETTING_KEY = "WIFI_SETTING_KEY"
    }

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    override var walletAddress: String by prefs(sharedPreferences, PATH_ADDRESS_KEY, Constants.PATH_DEFAULT_WALLET_ADDRESS)

    override var nodeId: String? by prefsOptional(sharedPreferences, NODE_ID_KEY, String::class.java)

    override var isActivated: Boolean by prefs(sharedPreferences, IS_SERVICE_RUNNING_KEY, false)

    private var wifiSettingValue: Int by prefs(sharedPreferences, WIFI_SETTING_KEY, WifiSetting.WIFI_AND_CELLULAR.ordinal)

    override var wifiSetting: WifiSetting
        get() = WifiSetting.valueOf(wifiSettingValue) ?: WifiSetting.WIFI_AND_CELLULAR
        set(value) {
            wifiSettingValue = value.value
        }

    override var proxyDomain: String? by prefsOptional(sharedPreferences, PROXY_DOMAIN_KEY, String::class.java, 3_600_000L) // 1 hour

    private fun createPrefKey(type: CheckType, key: String) = "$type$key"

    override fun statisticsForType(type: CheckType): CheckTypeStatistics {
        val averageLatency = sharedPreferences.getLong(createPrefKey(type, CHECKS_LATENCY_KEY_SUFFIX), 0L)
        val count = if (averageLatency < 1) 0L else sharedPreferences.getLong(createPrefKey(type, CHECKS_COUNT_KEY_SUFFIX), 0L)
        return CheckTypeStatistics(type, count, averageLatency)
    }

    override fun recordStatistics(type: CheckType, elapsed: Long): CheckTypeStatistics {
        val stats = statisticsForType(type)
        val newStats = stats.add(elapsed)

        val edit = sharedPreferences.edit()
        edit.putLong(createPrefKey(type, CHECKS_COUNT_KEY_SUFFIX), newStats.count)
        edit.putLong(createPrefKey(type, CHECKS_LATENCY_KEY_SUFFIX), newStats.totalLatencyMillis)
        edit.apply()

        return newStats
    }
}
