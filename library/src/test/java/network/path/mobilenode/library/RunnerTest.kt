package network.path.mobilenode.library

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import network.path.mobilenode.library.data.runner.*
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class RunnerTest {
    companion object {
        const val DUMMY_UUID = "DUMMY_UUID"
    }

    @Test
    fun testFallbackRunner() {
        val request = JobRequest(executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID)
        val runner = FallbackRunner
        val result = runner.runJob(request, MockTimeSource)
        Assertions.assertEquals(result.checkType, JobType.UNKNOWN)
        Assertions.assertEquals(result.executionUuid, DUMMY_UUID)
        Assertions.assertEquals(result.responseTime, 0L)
        Assertions.assertEquals(result.status, Status.UNKNOWN)
    }

    @Test
    fun test() {
        val s = "{\n" +
                "   \"target\":\"path.net\",\n" +
                "   \"target_ip\":\"13.35.146.35\",\n" +
                "   \"max_hops\":30,\n" +
                "   \"packet_size\":60,\n" +
                "   \"probes_per_hop\":10,\n" +
                "   \"hops\":[\n" +
                "      {\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"203.134.4.185\",\n" +
                "         \"rtts\":[\n" +
                "            10.095,\n" +
                "            10.383,\n" +
                "            10.337,\n" +
                "            9.597,\n" +
                "            10.541,\n" +
                "            10.552,\n" +
                "            10.497,\n" +
                "            10.132,\n" +
                "            10.139,\n" +
                "            10.076\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"203.134.7.129\",\n" +
                "         \"rtts\":[\n" +
                "            9.973,\n" +
                "            10.257,\n" +
                "            11.480,\n" +
                "            11.550,\n" +
                "            12.607,\n" +
                "            12.557,\n" +
                "            10.980,\n" +
                "            9.751,\n" +
                "            11.138,\n" +
                "            11.102\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"203.134.72.226\",\n" +
                "         \"rtts\":[\n" +
                "            10.185,\n" +
                "            10.199,\n" +
                "            9.997,\n" +
                "            9.885,\n" +
                "            10.062,\n" +
                "            9.867,\n" +
                "            10.858,\n" +
                "            10.815,\n" +
                "            10.256,\n" +
                "            9.578\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"203.134.8.14\",\n" +
                "         \"rtts\":[\n" +
                "            10.612,\n" +
                "            11.441,\n" +
                "            9.884,\n" +
                "            10.230,\n" +
                "            10.124,\n" +
                "            10.145,\n" +
                "            10.027,\n" +
                "            10.036,\n" +
                "            10.170,\n" +
                "            10.128\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"52.95.36.18\",\n" +
                "         \"rtts\":[\n" +
                "            10.988,\n" +
                "            11.407,\n" +
                "            11.273,\n" +
                "            10.969,\n" +
                "            11.332,\n" +
                "            10.891,\n" +
                "            10.761,\n" +
                "            10.919,\n" +
                "            11.757,\n" +
                "            11.001\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"52.95.36.9\",\n" +
                "         \"rtts\":[\n" +
                "            10.222,\n" +
                "            10.727,\n" +
                "            10.453,\n" +
                "            10.502,\n" +
                "            10.513,\n" +
                "            10.424,\n" +
                "            10.458,\n" +
                "            10.978,\n" +
                "            10.163,\n" +
                "            10.577\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "      },\n" +
                "      {\n" +
                "      },\n" +
                "      {\n" +
                "      },\n" +
                "      {\n" +
                "      },\n" +
                "      {\n" +
                "      },\n" +
                "      {\n" +
                "         \"ip\":\"13.35.146.35\",\n" +
                "         \"rtts\":[\n" +
                "            13.876,\n" +
                "            13.890,\n" +
                "            13.911,\n" +
                "            15.008,\n" +
                "            15.018,\n" +
                "            15.035,\n" +
                "            16.157,\n" +
                "            16.170,\n" +
                "            17.248,\n" +
                "            15.207\n" +
                "         ],\n" +
                "         \"lost\":0\n" +
                "      },\n" +
                "      {\n" +
                "      }\n" +
                "   ]\n" +
                "}"

        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val result = gson.fromJson(s, TraceResult::class.java)
        Assertions.assertNull(result.hops.last().rtts)
    }

    @Test
    fun testJobRequestFindRunner() {
        val executor = PathJobExecutorImpl(
            Mockito.mock(OkHttpClient::class.java),
            Mockito.mock(PathStorage::class.java),
            Mockito.mock(Context::class.java),
            Mockito.mock(Gson::class.java),
            MockTimeSource
        )

        // Fallback runner
        JobRequest(executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is FallbackRunner)
        }

        // HTTP runner
        JobRequest(protocol = "http", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is HttpRunner)
        }
        JobRequest(protocol = "HTTPS", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is HttpRunner)
        }

        // TCP runner
        JobRequest(protocol = "TcP", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is TcpRunner)
        }

        // UDP runner
        JobRequest(protocol = "uDp", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            val runner = executor.findRunner(it)
            Assertions.assertTrue(runner is UdpRunner)
        }

        // Tracepath runner
        // Very fragile test as I'm relying on loading a library when TraceRunner is created
        JobRequest(protocol = "", method = "traceroute", executionUuid = DUMMY_UUID, jobUuid = DUMMY_UUID).let {
            Assertions.assertThrows(UnsatisfiedLinkError::class.java) {
                executor.findRunner(it)
            }
        }
    }
}
