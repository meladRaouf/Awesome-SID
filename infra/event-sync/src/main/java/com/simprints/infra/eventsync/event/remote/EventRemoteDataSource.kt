package com.simprints.infra.eventsync.event.remote

import androidx.annotation.VisibleForTesting
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken.START_ARRAY
import com.fasterxml.jackson.core.JsonToken.START_OBJECT
import com.simprints.core.tools.json.JsonHelper
import com.simprints.infra.authstore.AuthStore
import com.simprints.infra.events.event.domain.EventCount
import com.simprints.infra.events.event.domain.models.subject.EnrolmentRecordEvent
import com.simprints.infra.eventsync.event.remote.exceptions.TooManyRequestsException
import com.simprints.infra.eventsync.event.remote.models.fromApiToDomain
import com.simprints.infra.eventsync.event.remote.models.subject.ApiEnrolmentRecordEvent
import com.simprints.infra.eventsync.event.remote.models.subject.fromApiToDomain
import com.simprints.infra.eventsync.status.down.domain.EventDownSyncResult
import com.simprints.infra.eventsync.status.up.domain.EventUpSyncResult
import com.simprints.infra.logging.Simber
import com.simprints.infra.network.SimNetwork.SimApiClient
import com.simprints.infra.network.exceptions.SyncCloudIntegrationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.produce
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.inject.Inject

internal class EventRemoteDataSource @Inject constructor(
    private val authStore: AuthStore,
    private val jsonHelper: JsonHelper,
) {

    suspend fun count(query: ApiRemoteEventQuery): List<EventCount> =
        executeCall { eventsRemoteInterface ->
            eventsRemoteInterface.countEvents(
                projectId = query.projectId,
                moduleId = query.moduleId,
                attendantId = query.userId,
                subjectId = query.subjectId,
                modes = query.modes,
                lastEventId = query.lastEventId
            ).map { it.fromApiToDomain() }
        }


    suspend fun dumpInvalidEvents(projectId: String, events: List<String>) {
        executeCall { remoteInterface ->
            remoteInterface.dumpInvalidEvents(projectId = projectId, events = events)
        }
    }

    suspend fun getEvents(
        query: ApiRemoteEventQuery,
        scope: CoroutineScope,
    ): EventDownSyncResult {
        return try {
            val response = takeStreaming(query)

            val totalCount = response.headers()[COUNT_HEADER]?.toIntOrNull() ?: 0
            val isTotalLowerBound = response
                .headers()[IS_COUNT_HEADER_LOWER_BOUND]
                ?.toBoolean() ?: false // If not present, assume it's not lower bound

            val streaming = response.body()?.byteStream() ?: ByteArrayInputStream(byteArrayOf())
            Simber.tag("SYNC").d("[EVENT_REMOTE_SOURCE] Stream taken")

            EventDownSyncResult(
                totalCount = totalCount.takeUnless { isTotalLowerBound },
                requestId = getRequestId(response),
                status = response.code(),
                eventStream = scope.produce(capacity = CHANNEL_CAPACITY_FOR_PROPAGATION) {
                    parseStreamAndEmitEvents(streaming, this)
                }
            )
        } catch (t: Throwable) {
            if (t is SyncCloudIntegrationException && t.httpStatusCode() == TOO_MANY_REQUEST_STATUS)
                throw TooManyRequestsException()
            else
                throw t

        }
    }

    @VisibleForTesting
    suspend fun parseStreamAndEmitEvents(
        streaming: InputStream,
        channel: ProducerScope<EnrolmentRecordEvent>,
    ) {
        val parser: JsonParser = JsonFactory().createParser(streaming)
        check(parser.nextToken() == START_ARRAY) { "Expected an array" }

        Simber.tag("SYNC").d("[EVENT_REMOTE_SOURCE] Start parsing stream")

        try {
            while (parser.nextToken() == START_OBJECT) {
                val event =
                    jsonHelper.jackson.readValue(parser, ApiEnrolmentRecordEvent::class.java)
                channel.send(event.fromApiToDomain())
            }

            parser.close()
            channel.close()
        } catch (t: Throwable) {
            Simber.d(t)
            parser.close()
            channel.close(t)
        }
    }

    private suspend fun takeStreaming(query: ApiRemoteEventQuery) =
        executeCall { eventsRemoteInterface ->
            eventsRemoteInterface.downloadEvents(
                projectId = query.projectId,
                moduleId = query.moduleId,
                attendantId = query.userId,
                subjectId = query.subjectId,
                modes = query.modes,
                lastEventId = query.lastEventId
            )
        }

    suspend fun post(
        projectId: String,
        body: ApiUploadEventsBody,
        acceptInvalidEvents: Boolean = true,
    ): EventUpSyncResult {
        val response = executeCall { remoteInterface ->
            remoteInterface.uploadEvents(projectId, acceptInvalidEvents, body)
        }

        return EventUpSyncResult(
            requestId = getRequestId(response),
            status = response.code(),
        )
    }

    fun getRequestId(response: Response<*>) = response.headers()[REQUEST_ID_HEADER].orEmpty()

    private suspend fun <T> executeCall(block: suspend (EventRemoteInterface) -> T): T =
        getEventsApiClient().executeCall { block(it) }

    private suspend fun getEventsApiClient(): SimApiClient<EventRemoteInterface> =
        authStore.buildClient(EventRemoteInterface::class)

    companion object {

        private const val CHANNEL_CAPACITY_FOR_PROPAGATION = 2000
        private const val TOO_MANY_REQUEST_STATUS = 429

        private const val COUNT_HEADER = "x-event-count"
        internal const val REQUEST_ID_HEADER = "x-request-id"
        private const val IS_COUNT_HEADER_LOWER_BOUND = "x-event-count-is-lower-bound"
    }
}
