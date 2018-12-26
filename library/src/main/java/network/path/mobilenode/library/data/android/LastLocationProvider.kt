package network.path.mobilenode.library.data.android

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import timber.log.Timber

class LastLocationProvider(context: Context) {
    private val fusedLocationProvider = LocationServices.getFusedLocationProviderClient(context)

    fun location(): Location? = try {
        val location = fusedLocationProvider.lastLocation.result
        Timber.v("$location, mocked: ${location?.isFromMockProvider}")

        if (location?.isFromMockProvider == true) null else location
    } catch (e: SecurityException) {
        Timber.v(e)
        null
    }
}
