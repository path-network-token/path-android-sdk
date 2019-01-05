
# About Path Network

Path is revolutionizing the uptime and performance **monitoring industry** by using **distributed nodes** powered by everyday users. With a monitoring network offering unprecedented global coverage, Path's powerful analytics give invaluable insight into website, application, and network uptime and performance.

More info on [official site](https://path.net).

# How does it work?

Any Android application using **path-android-sdk** can connect to Path API and perform monitoring jobs to earn Path tokens.

## Including in your project

Path Android SDK is available in JCenter, so simply add it as a dependency

```
implementation 'network.path.mobilenode:library:${version}'
```

where `${version}` corresponds to published version in [Download]

## Simple example

* Get reference to singleton instance of `PathSystem` in your `onCreate()` method of activity/service:
```kotlin
val pathSystem = PathSystem.create(context)
```

* Add listener to it:
```kotlin
val listener = object : PathSystem.Listener {
    override fun onStatusChanged(status: ConnectionStatus) {
        // Process connection status change 
    }
    override fun onNodeId(nodeId: String?) {
        // New node ID acquired
    }
    override fun onNodeInfoReceived(nodeInfo: NodeInfo?) {
        // Process new node information (ASN, location, etc.)
    }
    override fun onRunningChanged(isRunning: Boolean) {
        // Job processing status changed from paused to running or vice versa
    }
    override fun onStatisticsChanged(statistics: List<JobTypeStatistics>) {
        // Statistics was updated after another job was completed
    }
}
pathSystem.addListener(listener)
```
**Please note:** callbacks are not guaranteed to be called on UI thread.

* Initialise connection to the API and start executing jobs by calling `start` method on `PathSystem` object:
```kotlin
pathSystem.start()
``` 

* When you want to disconnect from the backend and stop any interaction with API call `stop` method on `PathSystem` object:
```kotlin
pathSystem.stop()
```

* To pause/resume execution of jobs call `toggle` method on `PathSystem` object (connection to the API will be kept alive):
```kotlin
pathSystem.toggle()
```

* You can always get current values which you receive through listener callbacks:
```kotlin
// Current status of connection to the backend
val status = pathSystem.status
// Node ID: null if system has never connected before, non-null string value otherwise
val nodeId = pathSystem.nodeId 
// Latest node information. Can be null if system has not yet connected
val nodeInfo = pathSystem.nodeInfo 
// true if job execution is enabled, false if job execution is paused
val isRunning = pathSystem.isRunning
// Latest statistics about executed jobs
val stats = pathSystem.statistics 
```

* You can also restrict job execution to Wi-Fi only networks by changing `wifiSetting` property of `PathSystem` object:
```kotlin
// Jobs are executed on both mobile and Wi-Fi networks
pathSystem.wifiSetting = WifiSetting.WIFI_AND_CELLULAR 
// Jobs are executed only on Wi-Fi networks
pathSystem.wifiSetting = WifiSetting.WIFI_ONLY 
```

# Support

Contact us on contact@path.net if you have any questions/issues/suggestions.
