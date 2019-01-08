package network.path.mobilenode.library.data.http

import com.google.gson.Gson
import network.path.mobilenode.library.Constants
import network.path.mobilenode.library.domain.entity.CheckIn
import network.path.mobilenode.library.domain.entity.JobList
import network.path.mobilenode.library.domain.entity.JobRequest
import network.path.mobilenode.library.domain.entity.JobResult
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface PathService {
    @POST("/checkin/{nodeId}")
    fun checkIn(@Path("nodeId") nodeId: String?, @Body checkIn: CheckIn): Call<JobList>

    @GET("/job_request/{executionId}")
    fun requestDetails(@Path("executionId") executionId: String): Call<JobRequest>

    @POST("/job_result/{nodeId}/{executionId}")
    fun postResult(@Path("nodeId") nodeId: String, @Path("executionId") executionId: String, @Body result: JobResult): Call<Unit>
}

internal class PathServiceImpl(
    okHttpClient: OkHttpClient,
    gson: Gson,
    isTest: Boolean
) : PathService by Retrofit.Builder()
    .baseUrl(if (isTest) Constants.HTTP_TEST_URL else Constants.HTTP_PROD_URL)
    .addConverterFactory(GsonConverterFactory.create(gson))
    .client(okHttpClient)
    .build()
    .create(PathService::class.java)
