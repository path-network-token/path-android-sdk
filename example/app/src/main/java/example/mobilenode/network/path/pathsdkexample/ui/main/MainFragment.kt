package example.mobilenode.network.path.pathsdkexample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import example.mobilenode.network.path.pathsdkexample.R
import kotlinx.android.synthetic.main.main_fragment.*
import network.path.mobilenode.library.domain.entity.ConnectionStatus
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.domain.entity.JobTypeStatistics
import network.path.mobilenode.library.domain.entity.NodeInfo

class MainFragment : Fragment() {
    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        viewModel.let {
            it.onViewCreated()
            it.nodeId.observe(this, ::setNodeId)
            it.status.observe(this, ::setStatus)
            it.nodeInfo.observe(this, ::setNodeInfo)
            it.isRunning.observe(this, ::setRunning)
            it.statistics.observe(this, ::setStatistics)
            updateState()
        }

        buttonStatus.setOnClickListener {
            if (viewModel.isStarted) {
                viewModel.stop()
            } else {
                viewModel.start()
            }
            updateState()
        }

        buttonToggle.setOnClickListener {
            viewModel.toggle()
        }
    }

    private fun updateState() {
        buttonStatus.text = if (viewModel.isStarted) "STOP" else "START"
    }

    private fun setNodeId(nodeId: String?) {
        value0.text = nodeId?.orNoData()
    }

    private fun setStatus(status: ConnectionStatus) {
        valueStatus.text = status.label
    }

    private fun setNodeInfo(nodeInfo: NodeInfo?) {
        value1.text = nodeInfo?.networkPrefix?.orNoData()
        value2.text = nodeInfo?.asn?.toString().orNoData()
        value3.text = nodeInfo?.asOrganization.orNoData()
        value4.text = nodeInfo?.location.orNoData()
    }

    private fun setRunning(isRunning: Boolean) {
        valueRunning.text = if (isRunning) "Running" else "Paused"
        buttonToggle.text = if (isRunning) "PAUSE" else "RESUME"
    }

    private fun setStatistics(stats: List<JobTypeStatistics>) {
        val totalCount = stats.map { it.count }.sum()
        labelStats1.text = "Total jobs"
        valueStats1.text = totalCount.toString()

        if (stats.isNotEmpty()) {
            applyStats(stats[0], labelStats2, valueStats2)
            applyStats(stats[1], labelStats3, valueStats3)
            applyStats(stats[2], labelStats4, valueStats4)
        }
    }

    private fun applyStats(stat: JobTypeStatistics, label: TextView, value: TextView) {
        label.text = stat.type.title
        value.text = "${stat.count} (${stat.averageLatency} ms)"
    }

    private fun String?.orNoData() = this ?: getString(R.string.no_data)

    private val ConnectionStatus.label: String
        get() = getString(
            when (this) {
                ConnectionStatus.CONNECTED -> R.string.status_connected
                ConnectionStatus.PROXY -> R.string.status_proxy
                ConnectionStatus.LOOKING,
                ConnectionStatus.DISCONNECTED -> R.string.status_disconnected
            }
        )

    private val JobType?.title
        get() = getString(
            when (this) {
                null -> R.string.other_checks
                JobType.HTTP -> R.string.http_checks
                JobType.TCP -> R.string.tcp_checks
                JobType.UDP -> R.string.udp_checks
                JobType.DNS -> R.string.dns_checks
                JobType.TRACEROUTE -> R.string.traceroute_checks
                JobType.UNKNOWN -> R.string.unknown_checks
            }
        )

    private fun <T> LiveData<T>.observe(lifecycleOwner: LifecycleOwner, block: (T) -> Unit) {
        observe(lifecycleOwner, Observer { block(it) })
    }
}
