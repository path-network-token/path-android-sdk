package network.path.mobilenode.library.domain.entity

import network.path.mobilenode.library.BuildConfig
import network.path.mobilenode.library.Constants

internal data class CheckIn(
        val type: String = "checkin",
        val nodeId: String?,
        val lat: String? = null,
        val lon: String? = null,
        val wallet: String,
        val deviceType: String? = "android",
        val capabilities: Map<String, Int> = mapOf("traceroute" to 2),
        val pathApiVersion: String = Constants.PATH_API_VERSION,
        val nodeBuildVersion: String = BuildConfig.VERSION_NAME,
        val returnJobsMax: Int = 10
)
