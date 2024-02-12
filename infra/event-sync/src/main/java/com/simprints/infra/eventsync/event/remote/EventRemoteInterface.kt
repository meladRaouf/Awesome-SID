package com.simprints.infra.eventsync.event.remote

import com.simprints.infra.eventsync.event.remote.models.ApiEventCount
import com.simprints.infra.network.SimRemoteInterface
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*


@JvmSuppressWildcards
internal interface EventRemoteInterface : SimRemoteInterface {

    @GET("projects/{projectId}/events/count")
    suspend fun countEvents(
        @Path("projectId") projectId: String,
        @Query("l_moduleId") moduleId: String?,
        @Query("l_attendantId") attendantId: String?,
        @Query("l_subjectId") subjectId: String?,
        @Query("l_mode") modes: List<ApiModes>,
        @Query("lastEventId") lastEventId: String?
    ): List<ApiEventCount>

    @Headers(
        "Content-Encoding: gzip",
        "X-Force-Version: 2024.1.1"
    )
    @POST("projects/{projectId}/events")
    suspend fun uploadEvents(
        @Path("projectId") projectId: String,
        @Query("acceptInvalidEvents") acceptInvalidEvents: Boolean = true,
        @Body body: ApiUploadEventsBody
    )

    @Streaming
    @Headers("X-Force-Version: 2024.1.1")
    @GET("projects/{projectId}/events")
    suspend fun downloadEvents(
        @Path("projectId") projectId: String,
        @Query("l_moduleId") moduleId: String?,
        @Query("l_attendantId") attendantId: String?,
        @Query("l_subjectId") subjectId: String?,
        @Query("l_mode") modes: List<ApiModes>,
        @Query("lastEventId") lastEventId: String?
    ): Response<ResponseBody>

    @Headers("Content-Encoding: gzip")
    @POST("projects/{projectId}/dump")
    suspend fun dumpInvalidEvents(
        @Path("projectId") projectId: String,
        @Query("type") type: String = "CORRUPTED_EVENTS",
        @Body events: List<String>,
    )

}
