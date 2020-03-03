package com.simprints.id.data.analytics.eventdata.controllers.remote

import com.simprints.id.data.analytics.eventdata.models.remote.session.ApiSessionEvents
import io.reactivex.Single
import retrofit2.adapter.rxjava2.Result
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface SessionsRemoteInterface {

    @POST("projects/{projectId}/sessions")
    fun uploadSessions(@Path("projectId") projectId: String,
                       @Body sessionsJson: HashMap<String, Array<ApiSessionEvents>>): Single<Result<Void?>>
}
