package com.simprints.clientapi.clientrequests.extractors

import android.content.Intent


class IdentifyExtractor(intent: Intent) : ClientRequestExtractor(intent) {

    override val expectedKeys: List<String> = super.keys

}
