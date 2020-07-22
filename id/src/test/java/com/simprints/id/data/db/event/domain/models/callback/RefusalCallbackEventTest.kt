package com.simprints.id.data.db.event.domain.models.callback
import com.google.common.truth.Truth.assertThat
import com.simprints.id.data.db.event.domain.models.CREATED_AT
import com.simprints.id.data.db.event.domain.models.DEFAULT_ENDED_AT
import com.simprints.id.data.db.event.domain.models.EventLabel.SessionIdLabel
import com.simprints.id.data.db.event.domain.models.EventType.CALLBACK_REFUSAL
import com.simprints.id.data.db.event.domain.models.callback.RefusalCallbackEvent.Companion.EVENT_VERSION
import com.simprints.id.data.db.event.domain.models.callback.RefusalCallbackEvent.RefusalCallbackPayload
import com.simprints.id.orchestrator.SOME_GUID1
import org.junit.Test

class RefusalCallbackEventTest {

    @Test
    fun create_RefusalCallbackEvent() {
        val event = RefusalCallbackEvent(CREATED_AT, "some_reason", "some_extra", SOME_GUID1)
        assertThat(event.id).isNotNull()
        assertThat(event.labels).containsExactly(SessionIdLabel(SOME_GUID1))
        assertThat(event.type).isEqualTo(CALLBACK_REFUSAL)
        with(event.payload as RefusalCallbackPayload) {
            assertThat(createdAt).isEqualTo(CREATED_AT)
            assertThat(endedAt).isEqualTo(DEFAULT_ENDED_AT)
            assertThat(eventVersion).isEqualTo(EVENT_VERSION)
            assertThat(type).isEqualTo(CALLBACK_REFUSAL)
            assertThat(reason).isEqualTo("some_reason")
            assertThat(extra).isEqualTo("some_extra")
        }
    }
}
