package network.path.mobilenode.library.domain.entity

enum class WifiSetting(val value: Int) {
    /**
     * Allow job execution on both Wi-Fi and Cellular networks
     */
    WIFI_AND_CELLULAR(0),
    /**
     * Allow job execution on Wi-Fi only
     */
    WIFI_ONLY(1);

    companion object {
        fun valueOf(value: Int): WifiSetting? = WifiSetting.values().find { it.ordinal == value }
    }
}
