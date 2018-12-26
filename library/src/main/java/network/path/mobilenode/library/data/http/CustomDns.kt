package network.path.mobilenode.library.data.http

import network.path.mobilenode.library.BuildConfig
import okhttp3.Dns
import org.xbill.DNS.Address
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

class CustomDns : Dns {
    companion object {
        private const val API_HOST = BuildConfig.HTTP_SERVER_URL
        private val API_IP = if (BuildConfig.DEBUG) "52.13.210.106" else "34.210.251.154"
    }

    private var isInitialized = false
    private var staticIpAddress: InetAddress? = null

    override fun lookup(hostname: String): MutableList<InetAddress> {
        // I'm initializing the DNS resolvers here to take advantage of this method being called in a background-thread managed by OkHttp
        init()
        return try {
            Collections.singletonList(Address.getByName(hostname))
        } catch (e: UnknownHostException) {
            // fallback to the API's static IP
            if (API_HOST == hostname && staticIpAddress != null) {
                Collections.singletonList(staticIpAddress)
            } else {
                throw e
            }
        }
    }

    private fun init() {
        if (isInitialized) return

        isInitialized = true

        try {
            staticIpAddress = InetAddress.getByName(API_IP)
            Timber.d("DNS: initialised static IP address")
        } catch (e: UnknownHostException) {
            Timber.w("DNS: couldn't initialize static IP address")
        }

        try {
            // configure the resolvers, starting with the default ones (based on the current network connection)
            val defaultResolver = Lookup.getDefaultResolver()
            // use Google's public DNS services
            val googleFirstResolver = SimpleResolver("8.8.8.8")
            val googleSecondResolver = SimpleResolver("8.8.4.4")
            // also try using Amazon
            val amazonResolver = SimpleResolver("205.251.198.30")
            Lookup.setDefaultResolver(ExtendedResolver(arrayOf(defaultResolver, googleFirstResolver, googleSecondResolver, amazonResolver)))
            Timber.d("DNS: initialised custom resolvers")
        } catch (e: UnknownHostException) {
            Timber.w("DNS: couldn't initialize custom resolvers")
        }
    }
}
