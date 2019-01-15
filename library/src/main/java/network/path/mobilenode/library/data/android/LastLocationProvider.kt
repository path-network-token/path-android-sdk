package network.path.mobilenode.library.data.android

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import timber.log.Timber

internal class LastLocationProvider(context: Context) : LocationCallback() {
    private val fusedLocationProvider = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null

    fun start() {
        val request = LocationRequest.create()
        request.interval = 60000L
        request.fastestInterval = 5000L
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        try {
            fusedLocationProvider.requestLocationUpdates(request, this, null)
        } catch (e: SecurityException) {
            Timber.w("LOCATION: cannot request updates: $e")
        }
    }

    fun stop() {
        fusedLocationProvider.removeLocationUpdates(this)
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
        Timber.w("LOCATION: security exception: $e")
        null
    }

    override fun onLocationResult(result: LocationResult?) {
        lastLocation = result?.lastLocation
        Timber.d("LOCATION: updated location: ${result?.lastLocation}")
    }
}
