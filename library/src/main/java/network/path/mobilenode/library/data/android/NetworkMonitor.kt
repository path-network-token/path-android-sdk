package network.path.mobilenode.library.data.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi

internal class NetworkMonitor(private val context: Context) {
    interface Listener {
        fun onStatusChanged(connected: Boolean)
    }

    var isConnected = false

    private val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkStatus()
        }
    }

    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Listeners
    private val listeners = mutableListOf<Listener>()

    fun addListener(l: Listener) {
        listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    // Lifecycle
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkCallback = NetworkCallback()
        }
    }

    fun start() {
        checkStatus()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> connectivityManager.registerDefaultNetworkCallback(networkCallback)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val builder = NetworkRequest.Builder()
                connectivityManager.registerNetworkCallback(builder.build(), networkCallback)
            }
            else -> context.registerReceiver(networkReceiver, intentFilter)
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } else {
            context.unregisterReceiver(networkReceiver)
        }
    }

    private fun checkStatus() {
        val networkInfo = connectivityManager.activeNetworkInfo ?: return
        updateStatus(networkInfo.isConnected)
    }

    private fun updateStatus(isConnected: Boolean) {
        if (isConnected != this.isConnected) {
            this.isConnected = isConnected
            listeners.forEach { it.onStatusChanged(isConnected) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            updateStatus(true)
        }

        override fun onLost(network: Network?) {
            updateStatus(false)
        }
    }
}
