package com.simprints.id.commontesttools.sessionEvents

import com.simprints.id.data.db.event.domain.events.session.DatabaseInfo
import com.simprints.id.data.db.event.domain.events.session.Device
import com.simprints.id.data.db.event.domain.events.session.SessionEvent
import com.simprints.id.tools.TimeHelper
import java.util.*

fun createFakeSession(timeHelper: TimeHelper? = null,
                      projectId: String = "some_project",
                      id: String = UUID.randomUUID().toString(),
                      startTime: Long = timeHelper?.now() ?: 0,
                      databaseInfo: DatabaseInfo = DatabaseInfo(0, 0),
                      fakeRelativeEndTime: Long = 0): SessionEvent =
    SessionEvent(
        id = id,
        projectId = projectId,
        appVersionName = "some_version",
        libVersionName = "some_version",
        language = "en",
        device = Device(deviceId = "device_id"),
        startTime = startTime,
        databaseInfo = databaseInfo).apply {
        relativeEndTime = fakeRelativeEndTime
    }

fun createFakeOpenSession(timeHelper: TimeHelper,
                          projectId: String = "some_project",
                          id: String = UUID.randomUUID().toString()) =
    createFakeSession(timeHelper, projectId, id, timeHelper.nowMinus(1000))

fun createFakeOpenSessionButExpired(timeHelper: TimeHelper,
                                    projectId: String = "some_project",
                                    id: String = UUID.randomUUID().toString()) =
    createFakeSession(timeHelper, projectId, id, timeHelper.nowMinus(SessionEvent.GRACE_PERIOD + 1000))

fun createFakeClosedSession(timeHelper: TimeHelper,
                            projectId: String = "some_project",
                            id: String = UUID.randomUUID().toString()) =
    createFakeSession(timeHelper, projectId, id, timeHelper.nowMinus(1000)).apply {
        relativeEndTime = timeRelativeToStartTime(timeHelper.now())
    }
