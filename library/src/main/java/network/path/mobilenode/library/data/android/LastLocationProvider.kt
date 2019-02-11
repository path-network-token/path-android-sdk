package network.path.mobilenode.library.data.android

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import timber.log.Timber

internal class LastLocationProvider(context: Context) :
    LocationCallback(),
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    companion object {
        private val UPDATE_DELAY = 15000L
    }

    private val apiClient = GoogleApiClient.Builder(context)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(LocationServices.API)
        .build()

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    private val fusedLocationProvider = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null
    private var lastProvider: String? = null

    private val oldListener = object : android.location.LocationListener {
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
            Timber.d("LOCATION: status changed [$provider, $status, $extras]")
        }

        override fun onProviderEnabled(provider: String) {
            Timber.d("LOCATION: provider enabled [$provider]")
            updateListener()
        }

        override fun onProviderDisabled(provider: String) {
            Timber.d("LOCATION: provider disabled [$provider]")
            updateListener()
        }

        override fun onLocationChanged(location: Location?) {
            lastLocation = location
            Timber.d("LOCATION: location changed (old): $location")
        }
    }

    fun start() {
        apiClient.connect()

        val request = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(60 * 1000)
            .setFastestInterval(5 * 1000)
        try {
            fusedLocationProvider.requestLocationUpdates(request, this, null)
        } catch (e: SecurityException) {
            Timber.w(e, "LOCATION: cannot request fused provider updates: $e")
        }

        updateListener()
    }

    fun stop() {
        fusedLocationProvider.removeLocationUpdates(this)
        if (apiClient.isConnected) {
            apiClient.disconnect()
        }
    }

    fun location(): Location? = try {
        fusedLocationProvider.flushLocations()

        val task = fusedLocationProvider.lastLocation
        val foundLocation = if (!task.isComplete) lastLocation else {
            val location = task.result
            Timber.d("LOCATION: from provider [$location], mocked: ${location?.isFromMockProvider}")

            if (location?.isFromMockProvider == true) null else location
        }
        Timber.d("LOCATION: found location [$foundLocation]")
        foundLocation
    } catch (e: SecurityException) {
        Timber.w(e, "LOCATION: security exception")
        null
    }

    @SuppressLint("MissingPermission")
    private fun updateListener() {
        val criteria = android.location.Criteria()
        criteria.accuracy = android.location.Criteria.ACCURACY_COARSE
        criteria.isAltitudeRequired = false
        criteria.isBearingRequired = false
        criteria.powerRequirement = Criteria.POWER_LOW
        val provider = locationManager.getBestProvider(criteria, true) ?: return
        if (lastProvider != provider) {
            try {
                locationManager.requestLocationUpdates(provider, UPDATE_DELAY, 0f, oldListener)
                lastProvider = provider
                Timber.d("LOCATION: requested updates from [$provider]")
            } catch (e: Exception) {
                Timber.w(e, "LOCATION: cannot request location manager updates: $e")
            }
        }
    }

    override fun onLocationResult(result: LocationResult?) {
        lastLocation = result?.lastLocation
        Timber.d("LOCATION: location result: ${result?.lastLocation}")
    }

    // GoogleApiClient.ConnectionCallbacks
    override fun onConnected(bundle: Bundle?) {
        Timber.d("LOCATION: connected [$bundle]")
    }

    override fun onConnectionSuspended(i: Int) {
        Timber.d("LOCATION: connection suspended [$i]")
    }

    // GoogleApiClient.OnConnectionFailedListener
    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.d("LOCATION: connection failed [$result]")
        if (result.hasResolution()) {
            Timber.d("LOCATION: problem has resolution")
        }
    }
}
