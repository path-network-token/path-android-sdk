package example.mobilenode.network.path.pathsdkexample.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import network.path.mobilenode.library.domain.PathSystem
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobTypeStatistics
import network.path.mobilenode.library.domain.entity.NodeInfo
import network.path.mobilenode.library.domain.entity.WifiSetting

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val pathSystem = PathSystem.create(application.applicationContext, false)
    val isStarted get() = pathSystem.isStarted

    private val _nodeId = MutableLiveData<String?>()
    val nodeId: LiveData<String?> = _nodeId

    private val _status = MutableLiveData<ConnectionStatus>()
    val status: LiveData<ConnectionStatus> = _status

    private val _nodeInfo = MutableLiveData<NodeInfo>()
    val nodeInfo: LiveData<NodeInfo> = _nodeInfo

    private val _isRunning = MutableLiveData<Boolean>()
    val isRunning: LiveData<Boolean> = _isRunning

    private val _statistics = MutableLiveData<List<JobTypeStatistics>>()
    val statistics: LiveData<List<JobTypeStatistics>> = _statistics

    val isTest get() = pathSystem.isTest

    var autoStart: Boolean
        get() = pathSystem.autoStart
        set(value) {
            pathSystem.autoStart = value
        }

    var isWifiOnly: Boolean
        get() = pathSystem.wifiSetting == WifiSetting.WIFI_ONLY
        set(value) {
            pathSystem.wifiSetting = if (value) WifiSetting.WIFI_ONLY else WifiSetting.WIFI_AND_CELLULAR
        }

    private val listener = object : PathSystem.Listener {
        override fun onNodeId(nodeId: String?) = updateNodeId(nodeId)

        override fun onConnectionStatusChanged(status: ConnectionStatus) = updateStatus(status)

        override fun onNodeInfoReceived(nodeInfo: NodeInfo?) = updateNodeInfo(nodeInfo)

        override fun onJobExecutionStatusChanged(isRunning: Boolean) = updateRunning(isRunning)

        override fun onStatisticsChanged(statistics: List<JobTypeStatistics>) = updateStats(statistics)
    }

    fun onViewCreated() {
        pathSystem.addListener(listener)
        updateStatus(pathSystem.status)
        updateNodeId(pathSystem.nodeId)
        updateNodeInfo(pathSystem.nodeInfo)
        updateRunning(pathSystem.isJobExecutionRunning)
        updateStats(pathSystem.statistics)

        if (pathSystem.autoStart && !pathSystem.isStarted) {
            pathSystem.start()
        }
        if (!pathSystem.hasAddress) {
            pathSystem.setWalletAddress("0xe9A3245A1368a5b006A6b5C7b35Ab016A085B065")
        }
    }

    fun toggleConnection(): Boolean {
        if (pathSystem.isStarted) {
            pathSystem.stop()
        } else {
            pathSystem.start()
        }
        return pathSystem.isStarted
    }

    fun toggle() {
        pathSystem.toggleJobExecution()
    }

    override fun onCleared() {
        pathSystem.removeListener(listener)
        super.onCleared()
    }

    // Private methods
    private fun updateNodeId(nodeId: String?) = _nodeId.postValue(nodeId)

    private fun updateStatus(status: ConnectionStatus) = _status.postValue(status)

    private fun updateNodeInfo(nodeInfo: NodeInfo?) = _nodeInfo.postValue(nodeInfo)

    private fun updateRunning(isRunning: Boolean) = _isRunning.postValue(isRunning)

    private fun updateStats(stats: List<JobTypeStatistics>) = _statistics.postValue(stats)
}
