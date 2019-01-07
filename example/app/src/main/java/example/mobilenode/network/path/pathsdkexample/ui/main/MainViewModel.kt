package example.mobilenode.network.path.pathsdkexample.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import network.path.mobilenode.library.domain.PathSystem
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobTypeStatistics
import network.path.mobilenode.library.domain.entity.NodeInfo

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val pathSystem = PathSystem.create(application.applicationContext)
    var isStarted = false
        private set

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

    private val listener = object : PathSystem.Listener {
        override fun onNodeId(nodeId: String?) = updateNodeId(nodeId)

        override fun onStatusChanged(status: ConnectionStatus) = updateStatus(status)

        override fun onNodeInfoReceived(nodeInfo: NodeInfo?) = updateNodeInfo(nodeInfo)

        override fun onRunningChanged(isRunning: Boolean) = updateRunning(isRunning)

        override fun onStatisticsChanged(statistics: List<JobTypeStatistics>) = updateStats(statistics)
    }

    fun onViewCreated() {
        pathSystem.addListener(listener)
        updateStatus(pathSystem.status)
        updateNodeId(pathSystem.nodeId)
        updateNodeInfo(pathSystem.nodeInfo)
        updateRunning(pathSystem.isRunning)
        updateStats(pathSystem.statistics)
    }

    fun start() {
        if (!isStarted) {
            pathSystem.start()
            isStarted = true
        }
    }

    fun toggle() {
        pathSystem.toggle()
    }

    fun stop() {
        if (isStarted) {
            pathSystem.stop()
            isStarted = false
        }
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
