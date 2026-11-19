package com.gogether.app

import com.gogether.app.feature.checkin.prototype.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInRecoveryTest {
    private val time = 1_800_000_000_000L
    private val point = MeetingPoint(31.27, 120.74)
    private val session = PrototypeSession("f23e4af8-b3ed-49ef-a7ee-d7c1cf2bf317", point,
        time + 600_000, time + 7_800_000)
    private val raw = PrototypeCodeCodec.encode(PrototypeCode(session, time, time + 30_000))
    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }
    private inner class Fixture {
        var clock = time
        var identity = "bob"
        var fail = true
        var calls = 0
        val store = MemoryPrototypeStore()
        val backend = LocalPrototypeBackend(store, { clock }, { 0 })
        val model = PrototypeViewModel("bob", backend, PositionSource {
            calls++
            if (fail) error("Could not get your location. Move to an open area and try again.")
            PositionFix(point, 10.0, 0)
        }, now = { clock }, entry = CheckInEntry.Activity(session),
            identity = CheckInIdentitySource { identity })
    }

    @Test fun freshCodeRetriesLocationOnceEvenWithRapidRepeatedTaps() = scenario {
        val f = Fixture()
        f.model.receiveScan(raw); runCurrent()
        assertTrue(f.model.state.value.locationRetryAvailable)
        assertNull(f.store.receipt(session.id, "bob"))
        f.fail = false
        f.model.retryLocation(); f.model.retryLocation(); runCurrent()
        assertEquals(2, f.calls)
        assertEquals(ParticipantScanStage.SUCCESS, f.model.state.value.scanStage)
        assertNotNull(f.store.receipt(session.id, "bob"))
        assertFalse(f.model.state.value.locationRetryAvailable)
        f.model.retryLocation(); runCurrent()
        assertEquals(2, f.calls)
    }

    @Test fun expiredCodeIsRejectedBeforeRequestingLocationAgain() = scenario {
        val f = Fixture()
        f.model.receiveScan(raw); runCurrent()
        f.clock += 30_000
        f.fail = false
        f.model.retryLocation(); runCurrent()
        assertEquals(1, f.calls)
        assertTrue(f.model.state.value.message.contains("expired"))
        assertFalse(f.model.state.value.locationRetryAvailable)
        assertNull(f.store.receipt(session.id, "bob"))
    }

    @Test fun cancellationBetweenAttemptsIsRecheckedBeforeLocation() = scenario {
        val f = Fixture()
        f.model.receiveScan(raw); runCurrent()
        f.store.cancel(session.id)
        f.fail = false
        f.model.retryLocation(); runCurrent()
        assertEquals(1, f.calls)
        assertTrue(f.model.state.value.message.contains("cancelled"))
        assertNull(f.store.receipt(session.id, "bob"))
    }

    @Test fun switchingAccountOrActivityAndLeavingPageDiscardRetry() = scenario {
        for (change in 0..2) {
            val f = Fixture()
            f.model.receiveScan(raw); runCurrent()
            when (change) {
                0 -> f.identity = "carol"
                1 -> f.model.enterActivity(session.copy(id = "414f1652-7e75-4d90-9cd7-c42f3665623a"))
                else -> f.model.leaveForeground()
            }
            f.fail = false
            f.model.retryLocation(); runCurrent()
            assertEquals(1, f.calls)
            assertNull(f.store.receipt(session.id, "bob"))
            assertFalse(f.model.state.value.locationRetryAvailable)
        }
    }

    @Test fun closingCameraKeepsSavedReceiptAndDoesNotShowFailureOrNewSuccessNotice() = scenario {
        val f = Fixture()
        f.fail = false
        f.model.receiveScan(raw); runCurrent()
        val receipt = f.model.state.value.receipt
        f.model.receiveScan(null, f.model.beginScan()); runCurrent()
        assertEquals(receipt, f.model.state.value.receipt)
        assertEquals(session, f.model.state.value.participantSession)
        assertEquals(ParticipantScanStage.READY, f.model.state.value.scanStage)
        assertEquals("", f.model.state.value.message)
        assertNull(f.model.state.value.notice)
        assertEquals(1, f.calls)
    }

    @Test fun invalidCodeAndFailedSubmissionCannotBeRetriedAsLocationFailures() = scenario {
        val f = Fixture()
        f.model.receiveScan(raw); runCurrent()
        f.model.receiveScan("invalid"); runCurrent()
        f.model.retryLocation(); runCurrent()
        assertEquals(1, f.calls)
        assertFalse(f.model.state.value.locationRetryAvailable)
        f.fail = false
        f.model.receiveScan(raw); runCurrent()
        f.model.receiveScan(raw); runCurrent()
        assertEquals(ParticipantScanStage.ATTENTION, f.model.state.value.scanStage)
        assertFalse(f.model.state.value.locationRetryAvailable)
        f.model.retryLocation(); runCurrent()
        assertEquals(3, f.calls)
    }

    @Test fun registrationLostAfterLocationFailureBlocksRetry() = scenario {
        var registered = true
        val registrations = object : CheckInRegistrationSource by MockCheckInRegistrations {
            override fun status(activityId: String?, userId: String) =
                if (registered) CheckInRegistration.CONFIRMED else CheckInRegistration.NONE
        }
        val store = MemoryPrototypeStore()
        var calls = 0
        val model = PrototypeViewModel("bob", LocalPrototypeBackend(store, { time }, { 0 }, registrations),
            PositionSource { calls++; error("Could not get your location") }, now = { time }, registrations = registrations)
        model.receiveScan(raw); runCurrent()
        registered = false
        model.retryLocation(); runCurrent()
        assertEquals(1, calls)
        assertTrue(model.state.value.message.contains("do not have a confirmed place"))
        assertNull(store.receipt(session.id, "bob"))
    }

    @Test fun lateLocationFailureCannotRestoreRetryForAnotherActivity() = scenario {
        val delayed = CompletableDeferred<Unit>()
        val model = PrototypeViewModel("bob", LocalPrototypeBackend(MemoryPrototypeStore(), { time }, { 0 }),
            PositionSource { withContext(NonCancellable) { delayed.await(); error("The earlier location request failed") } }, now = { time })
        model.receiveScan(raw); runCurrent()
        val next = session.copy(id = "414f1652-7e75-4d90-9cd7-c42f3665623a")
        model.enterActivity(next)
        delayed.complete(Unit); runCurrent()
        assertEquals(next, model.state.value.participantSession)
        assertFalse(model.state.value.locationRetryAvailable)
        assertFalse(model.state.value.busy)
        assertEquals("", model.state.value.message)
    }
}
