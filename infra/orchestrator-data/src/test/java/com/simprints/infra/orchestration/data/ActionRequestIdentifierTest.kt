package com.simprints.infra.orchestration.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActionRequestIdentifierTest {

    @Test
    fun `Correctly parses intent action`() {
        mapOf(
            "com.simprints.action.ACTION_NAME" to ActionRequestIdentifier("ACTION_NAME", "com.simprints.action", "", 1),
            "ACTION_NAME" to ActionRequestIdentifier("ACTION_NAME", "ACTION_NAME", "", 1),
            "" to ActionRequestIdentifier("", "", "", 1),
        ).forEach { (action, expected) -> assertThat(ActionRequestIdentifier.fromIntentAction(action)).isEqualTo(expected) }
    }
}
