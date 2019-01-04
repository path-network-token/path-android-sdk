package network.path.mobilenode.library.domain

import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.JobTypeStatistics
import network.path.mobilenode.library.domain.entity.WifiSetting

internal interface PathStorage {
    var walletAddress: String
    var nodeId: String?
    var isActivated: Boolean
    var wifiSetting: WifiSetting

    var proxyDomain: String?

    fun statisticsForType(type: JobType): JobTypeStatistics
    fun recordStatistics(type: JobType, elapsed: Long): JobTypeStatistics
}
