package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInEntryBindingTest {
    private val time = 1_800_000_000_000L


    private val first = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", time + 600_000, time + 7_800_000)
    private val second = first.copy(id = "414f1652-7e75-4d90-9cd7-c42f3665623a")
    private fun raw(session: PrototypeSession) = PrototypeCodeCodec.encode(PrototypeCode(session, time, time + 30_000))
    private fun service(store: MemoryPrototypeStore) = LocalPrototypeBackend(store, { time })
    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun wrongActivityIsRejectedBeforeLocationOrAnyRecordAndCorrectRetryWorks() = scenario {
        val store = MemoryPrototypeStore()
        var locations = 0
        val model = PrototypeViewModel("bob", service(store), now = { time },
            entry = CheckInEntry.Activity(first))
        model.receiveScan(raw(second), model.beginScan()); runCurrent()
        assertTrue(model.state.value.message.contains("another event"))
        assertEquals(first, model.state.value.participantSession)
        assertTrue(store.sessions("bob").isEmpty())
        assertTrue(store.receipts.isEmpty())
        assertEquals(0, locations)
        model.receiveScan(raw(first), model.beginScan()); runCurrent()
        assertEquals(first.id, model.state.value.receipt!!.sessionId)
        assertEquals(1, store.receipts.size)
    }

    @Test fun sameIdWithDifferentMeetingCannotReplaceTheBoundSnapshot() = scenario {
        val store = MemoryPrototypeStore()
        val model = PrototypeViewModel("bob", service(store), now = { time },
            entry = CheckInEntry.Activity(first))
        model.receiveScan(raw(first.copy(startAt = first.startAt + 1))); runCurrent()
        assertTrue(model.state.value.message.contains("does not match this event"))
        assertTrue(store.sessions("bob").isEmpty())
        assertEquals(first, model.state.value.participantSession)
    }

    @Test fun hostSelectingAnOldActivityDoesNotKeepShowingTheCurrentActivityQr() = scenario {
        val store = MemoryPrototypeStore().apply { hostedSession = second; rememberSession("alice", first) }
        val model = PrototypeViewModel("alice", service(store), now = { time })
        model.enterActivity(first)
        assertNull(model.state.value.session)
        assertNull(model.state.value.code)
        assertTrue(model.state.value.attendees.isEmpty())
        model.prepareMeeting(); runCurrent()
        assertTrue(model.state.value.message.contains("Open this event"))
        assertEquals(second, store.hostedSession)
    }

    @Test fun generalScanAcceptsAnotherActivityOnlyAfterExplicitChoice() = scenario {
        val store = MemoryPrototypeStore()
        val model = PrototypeViewModel("bob", service(store), now = { time },
            entry = CheckInEntry.Activity(first))
        model.receiveScan(raw(first), model.beginScan()); runCurrent()
        val ticket = model.beginScan(general = true)!!
        assertNull(model.state.value.receipt)
        assertNull(model.state.value.participantSession)
        assertNull(model.state.value.notice)
        model.receiveScan(raw(second), ticket); runCurrent()
        assertEquals(second.id, model.state.value.receipt!!.sessionId)
        assertEquals(2, store.receipts.size)
    }

    @Test fun changingActivityInvalidatesAlreadyLaunchedCameraResult() = scenario {
        val store = MemoryPrototypeStore()
        val model = PrototypeViewModel("bob", service(store), now = { time })
        model.enterActivity(first)
        val oldTicket = model.beginScan()!!
        model.enterActivity(second)
        model.receiveScan(raw(first), oldTicket); runCurrent()
        assertEquals(second, model.state.value.participantSession)
        assertTrue(store.receipts.isEmpty())
        assertTrue(store.sessions("bob").isEmpty())
        val newTicket = model.beginScan()!!
        model.leaveForeground() // Opening the camera must not discard the captured scope.
        model.receiveScan(raw(second), newTicket); runCurrent()
        assertEquals(second.id, model.state.value.receipt!!.sessionId)
        val notice = model.state.value.notice
        model.receiveScan(raw(second), newTicket); runCurrent()
        assertEquals(notice, model.state.value.notice)
        assertEquals(1, store.receipts.size)
    }

    @Test fun accountChangeBeforeCameraReturnDropsOldResultEvenAfterLoggingBackIn() = scenario {
        val store = MemoryPrototypeStore()
        var user: String? = "bob"
        val model = PrototypeViewModel("bob", service(store), now = { time },
            identity = CheckInIdentitySource { user })
        val ticket = model.beginScan()!!
        user = "carol"
        model.receiveScan(raw(first), ticket); runCurrent()
        assertTrue(model.state.value.identityExpired)
        assertTrue(model.state.value.history.isEmpty())
        assertNull(model.state.value.participantSession)
        user = "bob"
        model.receiveScan(raw(first)); runCurrent()
        assertTrue(store.receipts.isEmpty())
        assertTrue(store.sessions("bob").isEmpty())
    }

    @Test fun logoutBeforeQueuedScanPreventsCommitAndClearsPrivateState() = scenario {
        val store = MemoryPrototypeStore()
        var user: String? = "bob"
        val model = PrototypeViewModel("bob", service(store), now = { time }, identity = CheckInIdentitySource { user })
        model.receiveScan(raw(first))
        user = null; runCurrent()
        assertTrue(store.receipts.isEmpty())
        assertTrue(model.state.value.identityExpired)
        assertNull(model.state.value.receipt)
        assertTrue(model.state.value.history.isEmpty())
    }

    @Test fun accountChangeBeforeQueuedHostIssuanceNeverConfirmsArrival() = scenario {
        val store = MemoryPrototypeStore().apply { hostedSession = first }
        var user: String? = "alice"
        val model = PrototypeViewModel("alice", service(store), now = { time }, identity = CheckInIdentitySource { user })
        model.showCode()
        user = "bob"; runCurrent()
        assertTrue(model.state.value.identityExpired)
        assertNull(model.state.value.code)
        assertTrue(store.receipts.isEmpty())
    }

    @Test fun restorationUsesBoundEventAndUserInsteadOfMostRecentUnrelatedReceipt() = scenario {
        val store = MemoryPrototypeStore()
        val backend = service(store)
        val older = backend.submit("bob", raw(first))
        val newer = backend.submit("bob", raw(second))
        val bob = PrototypeViewModel("bob", backend, newer, { time }, CheckInEntry.Activity(first))
        assertEquals(older, bob.state.value.receipt)
        assertNull(bob.state.value.notice)
        assertEquals(ParticipantScanStage.READY, bob.state.value.scanStage)
        val carol = PrototypeViewModel("carol", backend, newer, { time }, CheckInEntry.Activity(first))
        assertNull(carol.state.value.receipt)
        assertTrue(carol.state.value.history.isEmpty())
        val fresh = PrototypeViewModel("david", backend, newer)
        assertNull(fresh.state.value.participantSession)
        assertNull(fresh.state.value.receipt)
    }

    @Test fun anotherUserNeverInheritsTheDevicesHostedActivity() = scenario {
        val store = MemoryPrototypeStore().apply { hostedSession = first }
        val model = PrototypeViewModel("carol", service(store))
        assertNull(model.state.value.session)
        assertNull(model.state.value.participantSession)
        assertTrue(model.state.value.history.isEmpty())
    }

    @Test fun conflictingBoundSnapshotCannotDisplayTheOldReceiptBeforeScanning() = scenario {
        val store = MemoryPrototypeStore()
        val backend = service(store)
        val receipt = backend.submit("bob", raw(first))
        val model = PrototypeViewModel("bob", backend, receipt,
            entry = CheckInEntry.Activity(first.copy(endAt = first.endAt + 1)))
        assertNull(model.state.value.receipt)
        assertEquals(receipt, store.receipt(first.id, "bob"))
    }

    @Test fun mockedRegistrationIsActivitySpecificAndRecheckedAfterLocation() = scenario {
        val store = MemoryPrototypeStore()
        var active = true
        val registrations = object : CheckInRegistrationSource {
            override fun status(activityId: String?, userId: String) =
                if (active && activityId == first.id && userId == "bob") CheckInRegistration.CONFIRMED else CheckInRegistration.NONE
            override fun participants(activityId: String?) = if (activityId == first.id) listOf(PrototypeAttendee("bob", "Bob")) else emptyList()
        }
        val backend = LocalPrototypeBackend(store, { time }, registrations)
        val model = PrototypeViewModel("bob", backend, now = { time },
            entry = CheckInEntry.Activity(first), registrations = registrations)
        assertEquals(CheckInRegistration.CONFIRMED, model.state.value.registration)
        assertEquals(1, model.state.value.participants!!.size)
        val ticket = model.beginScan()
        active = false
        model.receiveScan(raw(first), ticket); runCurrent()
        assertTrue(store.receipts.isEmpty())
        assertTrue(model.state.value.message.contains("do not have a confirmed place"))
        assertThrows(IllegalArgumentException::class.java) { backend.submit("bob", raw(second)) }
    }

    @Test fun queuedFeedbackCannotBeSavedAfterSwitchingAccount() = scenario {
        val store = MemoryPrototypeStore()
        store.rememberSession("bob", first)
        val backend = LocalPrototypeBackend(store, { first.endAt })
        var user: String? = "bob"
        val model = PrototypeViewModel("bob", backend, identity = CheckInIdentitySource { user })
        model.submitFeedback(first.id, 5, "A friendly group and a well-organized event.")
        user = "carol"
        runCurrent()
        assertNull(store.feedback(first.id, "bob"))
        assertNull(store.feedback(first.id, "carol"))
        assertTrue(model.state.value.identityExpired)
    }
}
