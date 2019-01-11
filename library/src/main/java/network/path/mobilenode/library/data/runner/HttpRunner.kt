package network.path.mobilenode.library.data.runner

import network.path.mobilenode.library.BuildConfig
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.PathStorage
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobType
import network.path.mobilenode.library.utils.getBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

internal class HttpRunner(private val okHttpClient: OkHttpClient, private val storage: PathStorage) : Runner {
    companion object {
        private val HTTP_PROTOCOL_REGEX = "^https?://.*".toRegex(RegexOption.IGNORE_CASE)
    }

    override val jobType = JobType.HTTP

    override fun runJob(jobRequest: JobRequest, timeSource: TimeSource) =
        computeJobResult(jobType, jobRequest, timeSource) { runHttpJob(it) }

    private fun runHttpJob(jobRequest: JobRequest): String {
        val request = buildRequest(jobRequest)

        return okHttpClient.newCall(request).execute().use {
            it.getBody().string()
        }
    }

    private fun buildRequest(jobRequest: JobRequest): Request {
        val completeUrl = with(jobRequest) {
            val prependedProtocol = when {
                endpointAddress == null -> throw IOException("Missing endpoint address in $jobRequest")
                endpointAddress.matches(HTTP_PROTOCOL_REGEX) -> ""
                else -> "http://"
            }

            val urlPrefix = HttpUrl.parse("$prependedProtocol$endpointAddress")
                ?: throw IOException("Unparsable url: $endpointAddress")
            val urlPrefixWithPortBuilder = urlPrefix.newBuilder()
            if (endpointPort != null && Constants.TCP_UDP_PORT_RANGE.contains(endpointPort)) {
                urlPrefixWithPortBuilder.port(endpointPort)
            }

            "${urlPrefixWithPortBuilder.build()}${endpointAdditionalParams.orEmpty()}"
        }

        val method = jobRequest.method ?: "GET"
        val requestBuilder = Request.Builder()
            .method(method, null)
            .url(completeUrl)

        var hasUserAgent = false
        jobRequest.headers?.flatMap { it.entries }?.forEach { entry ->
            requestBuilder.addHeader(entry.key, entry.value)
            if (entry.key == "User-Agent") {
                hasUserAgent = true
            }
        }

        if (!hasUserAgent) {
            val nodeId = storage.nodeId
            val ua =
                "Mozilla/5.0 (Path Network ${Constants.PATH_API_VERSION}; Android; ${System.getProperty("os.arch")}) ${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE} (KHTML, like Gecko) Node/$nodeId"
            requestBuilder.addHeader("User-Agent", ua)
        }

        return requestBuilder.build()
    }
}