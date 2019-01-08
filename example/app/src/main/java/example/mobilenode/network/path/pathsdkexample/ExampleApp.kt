package example.mobilenode.network.path.pathsdkexample

import android.app.Application
import timber.log.Timber

class ExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
