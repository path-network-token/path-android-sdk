package network.path.mobilenode.library.domain

import network.path.mobilenode.library.domain.entity.CheckType
import network.path.mobilenode.library.domain.entity.CheckTypeStatistics

enum class WifiSetting(val value: Int) {
    WIFI_AND_CELLULAR(0),
    WIFI_ONLY(1);

    companion object {
        fun valueOf(value: Int): WifiSetting? = WifiSetting.values().find { it.ordinal == value }
    }
}

interface PathStorage {
    var walletAddress: String
    var nodeId: String?
    var isActivated: Boolean
    var wifiSetting: WifiSetting

    var proxyDomain: String?

    fun statisticsForType(type: CheckType): CheckTypeStatistics
    fun recordStatistics(type: CheckType, elapsed: Long): CheckTypeStatistics
}
